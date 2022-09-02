package riscvconsole.devices.gcd

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._
import chisel3.util.{DeqIO, Valid}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.logicaltree._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._

// Problem:
// Implement a GCD circuit (the greatest common divisor of two numbers).
// Input numbers are bundled as 'RealGCDInput' and communicated using the Decoupled handshake protocol
//
class RealGCDInput extends Bundle {
  val a = UInt(16.W)
  val b = UInt(16.W)
}

class RealGCD extends Module {
  val io  = IO(new Bundle {
    val in  = DeqIO(new RealGCDInput())
    val out = Output(Valid(UInt(16.W)))
  })

  val x = Reg(UInt())
  val y = Reg(UInt())
  val p = RegInit(false.B)

  io.in.ready := !p

  when (io.in.valid && !p) {
    x := io.in.bits.a
    y := io.in.bits.b
    p := true.B
  }

  when (p) {
    when (x > y)  { x := y; y := x }
      .otherwise    { y := y - x }
  }

  io.out.bits  := x
  io.out.valid := y === 0.U && p
  when (io.out.valid) {
    p := false.B
  }
}

case class GCDParams(address: BigInt)

case class OMGCD
(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMGCD", "OMDevice", "OMComponent"),
) extends OMDevice

object GCDCtrlRegs {
  val trigger     = 0x00
  val data_a      = 0x04
  val data_b      = 0x08
  val data_c      = 0x0C
  val status      = 0x10
}

class GCDIO extends Bundle {
  val ready = Output(Bool())
}

abstract class GCD(busWidthBytes: Int, c: GCDParams)(implicit p: Parameters)
  extends IORegisterRouter(
    RegisterRouterParams(
      name = "gcd",
      compat = Seq("console,gcd0"),
      base = c.address,
      beatBytes = busWidthBytes),
    new GCDIO) with HasInterruptSources
{
  def nInterrupts: Int = 1
  lazy val module = new LazyModuleImp(this) {
    // Instantiation of the RealGCD
    val mod = Module(new RealGCD)

    // Inputs of the RealGCD
    val data_a = Reg(UInt(16.W))
    val data_b = Reg(UInt(16.W))
    val rst = RegInit(false.B)
    val trig = WireInit(false.B)

    mod.io.in.valid := trig
    mod.io.in.bits.a := data_a
    mod.io.in.bits.b := data_b
    mod.reset := reset.asBool || rst

    // Outputs of the RealGCD
    val in_ready = Wire(Bool())
    val out_valid = Wire(Bool())
    val data_c = Wire(UInt(16.W))

    in_ready := mod.io.in.ready
    out_valid := mod.io.out.valid
    data_c := mod.io.out.bits

    // External ports
    port.ready := mod.io.out.valid

    // Interrupts
    val int_trig = RegInit(false.B)
    when(!int_trig && out_valid) {
      int_trig := true.B
    }.elsewhen(int_trig && trig) {
      int_trig := false.B
    }
    interrupts(0) := int_trig

    // Mapping
    val mapping = Seq(
      GCDCtrlRegs.trigger -> Seq(
        RegField(1, trig, RegFieldDesc("trigger", "GCD trigger/start")),
        RegField(7),
        RegField(1, rst, RegFieldDesc("rst", "GCD Reset", reset = Some(0))),
        RegField(7),
        RegField(1, int_trig, RegFieldDesc("int_trig", "Interrupt Status", reset = Some(0)))
      ),
      GCDCtrlRegs.data_a -> Seq(RegField(16, data_a, RegFieldDesc("data_a", "A data for GCD"))),
      GCDCtrlRegs.data_b -> Seq(RegField(16, data_b, RegFieldDesc("data_b", "B data for GCD"))),
      GCDCtrlRegs.data_c -> Seq(RegField.r(16, data_c, RegFieldDesc("data_c", "C output for GCD", volatile = true))),
      GCDCtrlRegs.status -> Seq(
        RegField.r(1, out_valid, RegFieldDesc("out_valid", "GCD process ready", volatile = true)),
        RegField(7),
        RegField.r(1, in_ready, RegFieldDesc("in_ready", "GCD data ready (captured)", volatile = true))
      ),
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }

  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      Seq(
        OMGCD(
          memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("GCD", resourceBindings, Some(module.omRegMap)),
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings),
        )
      )
    }
  }
}

class TLGCD(busWidthBytes: Int, params: GCDParams)(implicit p: Parameters)
  extends GCD(busWidthBytes, params) with HasTLControlRegMap

object GCDID {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

case class GCDAttachParams
(
  device: GCDParams,
  controlWhere: TLBusWrapperLocation = PBUS)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLGCD = where {
    val name = s"gcd_${GCDID.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val gcd = LazyModule(new TLGCD(cbus.beatBytes, device))
    gcd.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>
      (gcd.controlXing(NoCrossing)
        := TLFragmenter(cbus)
        := bus )
    }

    where.ibus.fromSync := gcd.intXing(NoCrossing)

    LogicalModuleTree.add(where.logicalTreeNode, gcd.logicalTreeNode)
    gcd
  }
}

case object PeripheryGCDKey extends Field[Seq[GCDParams]](Nil)

trait HasPeripheryGCD { this: BaseSubsystem =>
  val gcdNodes = p(PeripheryGCDKey).map { ps =>
    GCDAttachParams(ps).attachTo(this)
  }
  val gcdPortNodes = gcdNodes.map(_.ioNode.makeSink())
}

trait HasPeripheryGCDModuleImp extends LazyModuleImp {
  val outer: HasPeripheryGCD
  val gcd: Seq[GCDIO] = outer.gcdPortNodes.zipWithIndex.map { case(n,i) =>
    n.makeIO()(ValName(s"codec_$i")).asInstanceOf[GCDIO]
  }
}