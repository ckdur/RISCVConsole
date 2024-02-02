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
  // Add the chosen, for the bootargs to be output in the console at boot
  val chosen = new Device {
    def describe(resources: ResourceBindings): Description = {
      Description("chosen", Map("bootargs" -> Seq(ResourceString("console=hvc0 earlycon=sbi"))))
    }
  }
  ResourceBinding {
    Resource(chosen, "bootargs").bind(ResourceString(""))
  }

  val latchedBootROM  = p(LatchedBootROMLocated(location)).map { BootROMLatched.attach(_, this, CBUS) }
  override lazy val module = new RVCDigitalTopModule(this)
}

class RVCDigitalTopModule[+L <: RVCDigitalTop](l: L) extends DigitalTopModule(l)
