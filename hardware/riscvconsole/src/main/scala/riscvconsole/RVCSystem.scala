package riscvconsole.system

import chipyard._
import chisel3._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.Parameters
import riscvconsole.devices.BootROMLatched

class RVCDigitalTop(implicit p: Parameters) extends DigitalTop
{
  //val maskROMResetVectorSourceNode = BundleBridgeSource[UInt]()
  //tileResetVectorNexusNode := maskROMResetVectorSourceNode
  val latchedBootROM  = p(LatchedBootROMLocated(location)).map { BootROMLatched.attach(_, this, CBUS) }
  override lazy val module = new RVCDigitalTopModule(this)
}

class RVCDigitalTopModule[+L <: RVCDigitalTop](l: L) extends DigitalTopModule(l) {
  //outer.maskROMResetVectorSourceNode.bundle := 0x80000000L.U
}
