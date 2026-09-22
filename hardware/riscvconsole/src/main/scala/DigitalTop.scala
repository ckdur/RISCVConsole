package riscvconsole

import org.chipsalliance.cde.config.Parameters

// The current DigitalTop now supports the new devices
class DigitalTop (implicit p: Parameters) extends chipyard.DigitalTop()(p)
  with riscvconsole.devices.codec.HasPeripheryCodec
  with riscvconsole.devices.fft.HasPeripheryFFT
{
  // Add the chosen, for the bootargs to be output in the console at boot
  /*val chosen = new Device {
    def describe(resources: ResourceBindings): Description = {
      Description("chosen", Map("bootargs" -> Seq(ResourceString("console=hvc0 earlycon=sbi"))))
    }
  }
  ResourceBinding {
    Resource(chosen, "bootargs").bind(ResourceString(""))
  }

  val latchedBootROM  = p(LatchedBootROMLocated(location)).map { BootROMLatched.attach(_, this, CBUS) }
  override lazy val module = new RVCDigitalTopModule(this)
*/
}
