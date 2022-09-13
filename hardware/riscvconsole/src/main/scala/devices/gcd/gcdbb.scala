package riscvconsole.devices.gcd

import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._
import chisel3.util.{DeqIO, HasBlackBoxResource, RegEnable, Valid}
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

class RealGCDBB extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle{
    val i_clk = Input(Clock())
    val i_rst = Input(Bool())
    val i_a = Input(UInt(16.W))
    val i_b = Input(UInt(16.W))
    val i_in_valid = Input(Bool())
    val o_in_ready = Output(Bool())
    val o_out_valid = Output(Bool())
    val o_c = Output(Bool())
  })
  addResource("/gcd/RealGCDBB.v")
}

abstract class GCDBB(busWidthBytes: Int, c: GCDParams)(implicit p: Parameters)
  extends RegisterRouter(
    RegisterRouterParams(
      name = "gcdbb",
      compat = Seq("console,gcd0"),
      base = c.address,
      beatBytes = busWidthBytes))
{
  lazy val module = new LazyModuleImp(this) {
    // Instantiation of the RealGCD
    val mod = Module(new RealGCDBB)

    // Inputs of the RealGCDBB
    val data_a = Reg(UInt(16.W))
    val data_b = Reg(UInt(16.W))
    val rst = RegInit(false.B)
    val trig = WireInit(false.B)

    mod.io.i_in_valid := trig
    mod.io.i_a := data_a
    mod.io.i_b := data_b
    mod.io.i_rst := reset.asBool || rst
    mod.io.i_clk := clock

    // Outputs of the RealGCDBB
    val in_ready = Wire(Bool())
    val out_valid = Wire(Bool())
    val data_c = Wire(UInt(16.W))

    in_ready := mod.io.o_in_ready
    out_valid := mod.io.o_out_valid
    data_c := RegEnable(mod.io.o_c, out_valid)

    val mapping = Seq(
      GCDCtrlRegs.trigger -> Seq(
        RegField(1, trig, RegFieldDesc("trigger", "GCD trigger/start")),
        RegField(7),
        RegField(1, rst, RegFieldDesc("rst", "GCD Reset", reset = Some(0)))
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

class TLGCDBB(busWidthBytes: Int, params: GCDParams)(implicit p: Parameters)
  extends GCDBB(busWidthBytes, params) with HasTLControlRegMap

object GCDBBID {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

case class GCDBBAttachParams
(
  device: GCDParams,
  controlWhere: TLBusWrapperLocation = PBUS)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLGCDBB = where {
    val name = s"gcdbb_${GCDBBID.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val gcdbb = LazyModule(new TLGCDBB(cbus.beatBytes, device))
    gcdbb.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>
      (gcdbb.controlXing(NoCrossing)
        := TLFragmenter(cbus)
        := bus )
    }

    LogicalModuleTree.add(where.logicalTreeNode, gcdbb.logicalTreeNode)
    gcdbb
  }
}

case object PeripheryGCDBBKey extends Field[Seq[GCDParams]](Nil)

trait HasPeripheryGCDBB { this: BaseSubsystem =>
  val gcdbbNodes = p(PeripheryGCDBBKey).map { ps =>
    GCDBBAttachParams(ps).attachTo(this)
  }
}
