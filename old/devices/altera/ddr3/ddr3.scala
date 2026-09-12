package riscvconsole.devices.altera.ddr3

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._

// ** For Qsys-based FPGAs

case class QsysDDR3Config 
(
  size_ck: Int = 1,
  size_cke: Int = 1,
  size_cs: Int = 1,
  size_dq: Int = 32,
  size_dm: Int = 4,
  size_dqs: Int = 4,
  size_odt: Int = 1,
  is_reset: Boolean = true)

class QsysDDR3(val c: QsysDDR3Config = QsysDDR3Config()) extends Bundle {
  val memory_mem_a       = Output(Bits(15.W))
  val memory_mem_ba      = Output(Bits(3.W))
  val memory_mem_ck      = Output(Bits(c.size_ck.W))
  val memory_mem_ck_n    = Output(Bits(c.size_ck.W))
  val memory_mem_cke     = Output(Bits(c.size_cke.W))
  val memory_mem_cs_n    = Output(Bits(c.size_cs.W))
  val memory_mem_dm      = Output(Bits(c.size_dm.W))
  val memory_mem_ras_n   = Output(Bool())
  val memory_mem_cas_n   = Output(Bool())
  val memory_mem_we_n    = Output(Bool())
  val memory_mem_reset_n = if(c.is_reset) Some(Output(Bool())) else None
  val memory_mem_dq      = Analog(c.size_dq.W)
  val memory_mem_dqs     = Analog(c.size_dqs.W)
  val memory_mem_dqs_n   = Analog(c.size_dqs.W)
  val memory_mem_odt     = Output(Bits(c.size_odt.W))
  val oct_rzqin          = Input(Bool())
}

trait QsysClocksReset extends Bundle {
  //inputs
  //"NO_BUFFER" clock source (must be connected to IBUF outside of IP)
  val ddr_clk_clk       = Input(Clock())
  val ddr_reset_reset_n = Input(Bool())
}

trait QsysUserSignals extends Bundle {
  val mem_status_local_init_done   = Output(Bool())
  val mem_status_local_cal_success = Output(Bool())
  val mem_status_local_cal_fail    = Output(Bool())
}

class QsysIO(c: QsysDDR3Config = QsysDDR3Config()) extends QsysDDR3(c) with QsysUserSignals

class QsysPlatformBlackBox(c: QsysDDR3Config = QsysDDR3Config())(implicit val p:Parameters) extends BlackBox {
  override def desiredName = "main"

  val io = IO(new QsysIO(c) with QsysClocksReset {
    val sys_clk_clk       = Input(Clock())
    val sys_reset_reset_n = Input(Bool())

    //axi_s
    //slave interface write address ports
    val axi4_awid = Input(Bits(4.W))
    val axi4_awaddr = Input(Bits(30.W))
    val axi4_awlen = Input(Bits(8.W))
    val axi4_awsize = Input(Bits(3.W))
    val axi4_awburst = Input(Bits(2.W))
    val axi4_awlock = Input(Bits(1.W))
    val axi4_awcache = Input(Bits(4.W))
    val axi4_awprot = Input(Bits(3.W))
    val axi4_awqos = Input(Bits(4.W))
    val axi4_awvalid = Input(Bool())
    val axi4_awready = Output(Bool())
    //slave interface write data ports
    val axi4_wdata = Input(Bits(32.W))
    val axi4_wstrb = Input(Bits(4.W))
    val axi4_wlast = Input(Bool())
    val axi4_wvalid = Input(Bool())
    val axi4_wready = Output(Bool())
    //slave interface write response ports
    val axi4_bready = Input(Bool())
    val axi4_bid = Output(Bits(4.W))
    val axi4_bresp = Output(Bits(2.W))
    val axi4_bvalid = Output(Bool())
    //slave interface read address ports
    val axi4_arid = Input(Bits(4.W))
    val axi4_araddr = Input(Bits(30.W))
    val axi4_arlen = Input(Bits(8.W))
    val axi4_arsize = Input(Bits(3.W))
    val axi4_arburst = Input(Bits(2.W))
    val axi4_arlock = Input(Bits(1.W))
    val axi4_arcache = Input(Bits(4.W))
    val axi4_arprot = Input(Bits(3.W))
    val axi4_arqos = Input(Bits(4.W))
    val axi4_arvalid = Input(Bool())
    val axi4_arready = Output(Bool())
    //slave interface read data ports
    val axi4_rready = Input(Bool())
    val axi4_rid = Output(Bits(4.W))
    val axi4_rdata = Output(Bits(32.W))
    val axi4_rresp = Output(Bits(2.W))
    val axi4_rlast = Output(Bool())
    val axi4_rvalid = Output(Bool())
  })
}

class QsysPlatform(c : Seq[AddressSet],
                      ddrc: QsysDDR3Config = QsysDDR3Config())(implicit p: Parameters) extends LazyModule {
  val ranges = AddressRange.fromSets(c)
  require (ranges.size == 1, "DDR3 range must be contiguous")
  val offset = ranges.head.base
  val depth = ranges.head.size
  require(depth<=0x100000000L,"QsysPlatform supports upto 4GB depth configuraton")

  val device = new MemoryDevice
  val island = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address       = c,
      resources     = device.reg,
      regionType    = RegionType.UNCACHED,
      executable    = true,
      supportsWrite = TransferSizes(1, 64),
      supportsRead  = TransferSizes(1, 64))),
    beatBytes = 4
  )))

  //val buffer  = LazyModule(new TLBuffer)
  val buffer  = LazyModule(new TLBuffer)
  val toaxi4  = LazyModule(new TLToAXI4(adapterName = Some("mem")))
  val indexer = LazyModule(new AXI4IdIndexer(idBits = 4))
  val deint   = LazyModule(new AXI4Deinterleaver(p(CacheBlockBytes)))
  val yank    = LazyModule(new AXI4UserYanker)

  val node: TLInwardNode =
    island := yank.node := deint.node := indexer.node := toaxi4.node := buffer.node

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val port = new QsysIO(ddrc)
      val ckrst = new Bundle with QsysClocksReset
    })

    //MIG black box instantiation
    val blackbox = Module(new QsysPlatformBlackBox(ddrc))
    val (axi_async, _) = island.in(0)

    //pins to top level

    //inouts
    attach(io.port.memory_mem_dq,blackbox.io.memory_mem_dq)
    attach(io.port.memory_mem_dqs_n,blackbox.io.memory_mem_dqs_n)
    attach(io.port.memory_mem_dqs,blackbox.io.memory_mem_dqs)

    //outputs
    io.port.memory_mem_a            := blackbox.io.memory_mem_a
    io.port.memory_mem_ba           := blackbox.io.memory_mem_ba
    io.port.memory_mem_ras_n        := blackbox.io.memory_mem_ras_n
    io.port.memory_mem_cas_n        := blackbox.io.memory_mem_cas_n
    io.port.memory_mem_we_n         := blackbox.io.memory_mem_we_n
    if(ddrc.is_reset) io.port.memory_mem_reset_n.get := blackbox.io.memory_mem_reset_n.get
    io.port.memory_mem_ck           := blackbox.io.memory_mem_ck
    io.port.memory_mem_ck_n         := blackbox.io.memory_mem_ck_n
    io.port.memory_mem_cke          := blackbox.io.memory_mem_cke
    io.port.memory_mem_cs_n         := blackbox.io.memory_mem_cs_n
    io.port.memory_mem_dm           := blackbox.io.memory_mem_dm
    io.port.memory_mem_odt          := blackbox.io.memory_mem_odt

    //inputs
    //NO_BUFFER clock
    blackbox.io.ddr_clk_clk       := io.ckrst.ddr_clk_clk
    blackbox.io.sys_clk_clk       := clock
    blackbox.io.ddr_reset_reset_n := io.ckrst.ddr_reset_reset_n
    blackbox.io.sys_reset_reset_n := !reset.asBool()
    blackbox.io.oct_rzqin         := io.port.oct_rzqin
    io.port.mem_status_local_init_done   := blackbox.io.mem_status_local_init_done
    io.port.mem_status_local_cal_success := blackbox.io.mem_status_local_cal_success
    io.port.mem_status_local_cal_fail    := blackbox.io.mem_status_local_cal_fail

    val awaddr = axi_async.aw.bits.addr - offset.U
    val araddr = axi_async.ar.bits.addr - offset.U

    //slave AXI interface write address ports
    blackbox.io.axi4_awid    := axi_async.aw.bits.id
    blackbox.io.axi4_awaddr  := awaddr //truncated
    blackbox.io.axi4_awlen   := axi_async.aw.bits.len
    blackbox.io.axi4_awsize  := axi_async.aw.bits.size
    blackbox.io.axi4_awburst := axi_async.aw.bits.burst
    blackbox.io.axi4_awlock  := axi_async.aw.bits.lock
    blackbox.io.axi4_awcache := "b0011".U
    blackbox.io.axi4_awprot  := axi_async.aw.bits.prot
    blackbox.io.axi4_awqos   := axi_async.aw.bits.qos
    blackbox.io.axi4_awvalid := axi_async.aw.valid
    axi_async.aw.ready        := blackbox.io.axi4_awready

    //slave interface write data ports
    blackbox.io.axi4_wdata   := axi_async.w.bits.data
    blackbox.io.axi4_wstrb   := axi_async.w.bits.strb
    blackbox.io.axi4_wlast   := axi_async.w.bits.last
    blackbox.io.axi4_wvalid  := axi_async.w.valid
    axi_async.w.ready         := blackbox.io.axi4_wready

    //slave interface write response
    blackbox.io.axi4_bready  := axi_async.b.ready
    axi_async.b.bits.id       := blackbox.io.axi4_bid
    axi_async.b.bits.resp     := blackbox.io.axi4_bresp
    axi_async.b.valid         := blackbox.io.axi4_bvalid

    //slave AXI interface read address ports
    blackbox.io.axi4_arid    := axi_async.ar.bits.id
    blackbox.io.axi4_araddr  := araddr // truncated
    blackbox.io.axi4_arlen   := axi_async.ar.bits.len
    blackbox.io.axi4_arsize  := axi_async.ar.bits.size
    blackbox.io.axi4_arburst := axi_async.ar.bits.burst
    blackbox.io.axi4_arlock  := axi_async.ar.bits.lock
    blackbox.io.axi4_arcache := "b0011".U
    blackbox.io.axi4_arprot  := axi_async.ar.bits.prot
    blackbox.io.axi4_arqos   := axi_async.ar.bits.qos
    blackbox.io.axi4_arvalid := axi_async.ar.valid
    axi_async.ar.ready        := blackbox.io.axi4_arready

    //slace AXI interface read data ports
    blackbox.io.axi4_rready  := axi_async.r.ready
    axi_async.r.bits.id       := blackbox.io.axi4_rid
    axi_async.r.bits.data     := blackbox.io.axi4_rdata
    axi_async.r.bits.resp     := blackbox.io.axi4_rresp
    axi_async.r.bits.last     := blackbox.io.axi4_rlast
    axi_async.r.valid         := blackbox.io.axi4_rvalid
  }
}

