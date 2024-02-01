package riscvconsole.devices

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._

import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}

// A replacement of the ROM, combining the BootROM way to load the bin file with the MaskROM and vlsi_rom_fpga

class BlackBoxedROMLatched(c: ROMConfig, bigs: Seq[BigInt]) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val address = Input(UInt(log2Ceil(c.depth).W))
    val oe = Input(Bool())
    val me = Input(Bool())
    val q = Output(UInt(c.width.W))
  })
  val initial = bigs.zipWithIndex.map{ case(b, i) => s"    rom[${i}] = ${c.width}'h${b.toString(16)};" }.mkString("\n")
  setInline("BlackBoxedROMLatched.v",
    s"""module BlackBoxedROMLatched(
       |  input clock,
       |  input oe,
       |  input me,
       |  input [${log2Ceil(c.depth)-1}:0] address,
       |  output [${c.width-1}:0] q
       |);
       |  reg [${c.width-1}:0] out;
       |  reg [${c.width-1}:0] rom [0:${c.depth-1}];
       |
       |
       |  initial begin: init_and_load
       |$initial
       |  end
       |
       |
       |  always @(posedge clock) begin
       |    if (me) begin
       |      out <= rom[address];
       |    end
       |  end
       |
       |  assign q = oe ? out : ${c.width}'bZ;
       |
       |endmodule
       |
       |""".stripMargin)
}

class TLROMLatched(val base: BigInt, val size: Int, contentsDelayed: => Seq[Byte], executable: Boolean = true, beatBytes: Int = 4,
  resources: Seq[Resource] = new SimpleDevice("romlatched", Seq("rvc,romlatched0")).reg("mem"))(implicit p: Parameters) extends LazyModule
{
  val node = TLManagerNode(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address     = List(AddressSet(base, size-1)),
      resources   = resources,
      regionType  = RegionType.UNCACHED,
      executable  = executable,
      supportsGet = TransferSizes(1, beatBytes),
      fifoId      = Some(0))),
    beatBytes = beatBytes)))

  lazy val module = new Impl
  class Impl extends LazyModuleImp(this) {
    val contents = contentsDelayed
    val wrapSize = 1 << log2Ceil(contents.size)
    require (wrapSize <= size)
    val width = 8 * beatBytes
    val depth = size/beatBytes

    val words = (contents ++ Seq.fill(wrapSize-contents.size)(0.toByte)).grouped(beatBytes).toSeq
    val bigs = words.map(_.foldRight(BigInt(0)){ case (x,y) => (x.toInt & 0xff) | y << 8})
    //val romc = VecInit(bigs.map(_.U(width.W)))

    val (in, edge)= node.in(0)

    val rom = Module(new BlackBoxedROMLatched(ROMConfig("", depth, 8*beatBytes), bigs))
    rom.io.clock := clock
    rom.io.address := edge.addr_hi(in.a.bits.address - base.U)(log2Ceil(depth)-1, 0)
    rom.io.oe := true.B // active high tri state enable
    rom.io.me := in.a.fire

    val d_full = RegInit(false.B)
    val d_size = Reg(UInt())
    val d_source = Reg(UInt())
    val d_data = rom.io.q holdUnless RegNext(in.a.fire)

    // Flow control
    when (in.d.fire) { d_full := false.B }
    when (in.a.fire) { d_full := true.B  }
    in.d.valid := d_full
    in.a.ready := in.d.ready || !d_full

    when (in.a.fire) {
      d_size   := in.a.bits.size
      d_source := in.a.bits.source
    }

    in.d.bits := edge.AccessAck(d_source, d_size, d_data)

    // Tie off unused channels
    in.b.valid := false.B
    in.c.ready := true.B
    in.e.ready := true.B
  }
}

object BootROMLatched {
  /** BootROM.attach not only instantiates a TLROM and attaches it to the tilelink interconnect
    *    at a configurable location, but also drives the tiles' reset vectors to point
    *    at its 'hang' address parameter value.
    */
  def attach(params: BootROMParams, subsystem: BaseSubsystem with HasHierarchicalElements with HasTileInputConstants, where: TLBusWrapperLocation)
            (implicit p: Parameters): TLROMLatched = {
    val tlbus = subsystem.locateTLBusWrapper(where)
    val bootROMDomainWrapper = tlbus.generateSynchronousDomain.suggestName("bootromlatched_domain")

    val bootROMResetVectorSourceNode = BundleBridgeSource[UInt]()
    lazy val contents = {
      val romdata = Files.readAllBytes(Paths.get(params.contentFileName))
      val rom = ByteBuffer.wrap(romdata)
      rom.array() ++ subsystem.dtb.contents
    }

    val bootrom = bootROMDomainWrapper {
      LazyModule(new TLROMLatched(params.address, params.size, contents, true, tlbus.beatBytes))
    }

    bootrom.node := tlbus.coupleTo("bootrom"){ TLFragmenter(tlbus) := _ }
    // Drive the `subsystem` reset vector to the `hang` address of this Boot ROM.
    subsystem.tileResetVectorNexusNode := bootROMResetVectorSourceNode
    InModuleBody {
      val reset_vector_source = bootROMResetVectorSourceNode.bundle
      require(reset_vector_source.getWidth >= params.hang.bitLength,
        s"BootROMLatched defined with a reset vector (${params.hang})too large for physical address space (${reset_vector_source.getWidth})")
      bootROMResetVectorSourceNode.bundle := params.hang.U
    }
    bootrom
  }
}
