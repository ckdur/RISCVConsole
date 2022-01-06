package riscvconsole.devices.fft

import chisel3._
import chisel3.experimental.IntParam
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.diplomaticobjectmodel._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._

case class FFTParams(address: BigInt, LOG2_FFT_LEN: Int = 8)

case class OMFFT
(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMFFT", "OMDevice", "OMComponent"),
) extends OMDevice

object FFTCtrlRegs {
  val data_in     = 0x00
  val data_out    = 0x04
  val addr_in     = 0x08
  val addr_out    = 0x0C
  val ctrl        = 0x10
  val status      = 0x14
}

class fft_wrapper(val c: FFTParams) extends BlackBox(
  Map("LOG2_FFT_LEN" -> IntParam(c.LOG2_FFT_LEN))
) with HasBlackBoxResource {
  val io = IO(new Bundle {
    val din = Input(UInt(32.W))
    val addr_in = Input(UInt(c.LOG2_FFT_LEN.W))
    val wr_in = Input(Bool())
    val dout = Output(UInt(32.W))
    val addr_out = Input(UInt(c.LOG2_FFT_LEN.W))
    val ready = Output(Bool())
    val busy = Output(Bool())
    val start = Input(Bool())
    val rst_n = Input(Bool())
    val syn_rst_n = Input(Bool())
    val clk = Input(Clock())
  })
  addResource("/versatile_fft/butterfly.vhd")
  addResource("/versatile_fft/dpram_inf.vhd")
  addResource("/versatile_fft/fft_engine.vhd")
  addResource("/versatile_fft/fft_len.vhd")
  addResource("/versatile_fft/icpx_pkg.vhd")
  addResource("/versatile_fft/icpxram.vhd")
  addResource("/versatile_fft/fft_wrapper.vhd")
}

abstract class FFT(busWidthBytes: Int, c: FFTParams)(implicit p: Parameters)
  extends RegisterRouter(
    RegisterRouterParams(
      name = "fft",
      compat = Seq("console,fft0"),
      base = c.address,
      beatBytes = busWidthBytes))
    with HasInterruptSources {

  def nInterrupts = 1
  lazy val module = new LazyModuleImp(this) {
    val fft = Module(new fft_wrapper(c))

    // Registers
    val din = Reg(UInt(32.W))
    val addr_in = Reg(UInt(c.LOG2_FFT_LEN.W))
    val addr_out = Reg(UInt(c.LOG2_FFT_LEN.W))
    val wr_in = WireInit(false.B)
    val start = WireInit(false.B)
    val syn_rst = WireInit(false.B)

    // Connections
    fft.io.din := din
    fft.io.addr_in := addr_in
    fft.io.addr_out := addr_out
    fft.io.wr_in := wr_in
    fft.io.start := start
    fft.io.syn_rst_n := !syn_rst
    fft.io.rst_n := !reset.asBool()
    fft.io.clk := clock

    // Interrupts
    interrupts(0) := fft.io.ready

    // Mapping
    val ctrlFields = Seq(
      RegField(1, start),
      RegField(1, wr_in),
      RegField(1, syn_rst)
    )
    val statusFields = Seq(
      RegField.r(1, fft.io.ready),
      RegField.r(1, fft.io.busy),
    )
    val mapping = Seq(
      FFTCtrlRegs.data_in -> Seq(RegField(32, din,
        RegFieldDesc("din", "Data Input"))),
      FFTCtrlRegs.data_out -> Seq(RegField.r(32, fft.io.dout,
        RegFieldDesc("dout", "Data Output"))),
      FFTCtrlRegs.addr_in -> Seq(RegField.r(32, addr_in,
        RegFieldDesc("addr_in", "Addr Input"))),
      FFTCtrlRegs.addr_out -> Seq(RegField.r(32, addr_out,
        RegFieldDesc("addr_out", "Addr Output"))),
      FFTCtrlRegs.ctrl -> ctrlFields,
      FFTCtrlRegs.status -> statusFields,
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }

  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      Seq(
        OMFFT(
          memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("FFT", resourceBindings, Some(module.omRegMap)),
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings),
        )
      )
    }
  }
}

class TLFFT(busWidthBytes: Int, params: FFTParams)(implicit p: Parameters)
  extends FFT(busWidthBytes, params) with HasTLControlRegMap

object FFT {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

case class FFTAttachParams
(
  device: FFTParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLFFT = where {
    val name = s"fft_${FFT.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val fftClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val fft = fftClockDomainWrapper { LazyModule(new TLFFT(cbus.beatBytes, device)) }
    fft.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, cbus.beatBytes, cbus.beatBytes)))
        cbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(cbus) := _ }
        blocker
      }

      fftClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          cbus.dtsClk.foreach(_.bind(fft.device))
          cbus.fixedClockNode
        case _: RationalCrossing =>
          cbus.clockNode
        case _: AsynchronousCrossing =>
          val codecClockGroup = ClockGroup()
          codecClockGroup := where.asyncClockGroupsNode
          blockerOpt.map { _.clockNode := codecClockGroup } .getOrElse { codecClockGroup }
      })

      (fft.controlXing(controlXType)
        := TLFragmenter(cbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := fft.intXing(intXType)

    LogicalModuleTree.add(where.logicalTreeNode, fft.logicalTreeNode)

    fft
  }
}