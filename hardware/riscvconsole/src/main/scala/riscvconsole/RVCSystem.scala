package riscvconsole.system

import chipyard._
import org.chipsalliance.cde.config.Parameters

class RVCDigitalTop(implicit p: Parameters) extends DigitalTop
{
  override lazy val module = new RVCDigitalTopModule(this)
}

class RVCDigitalTopModule[+L <: RVCDigitalTop](l: L) extends DigitalTopModule(l)
