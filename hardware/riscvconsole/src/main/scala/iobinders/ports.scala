package riscvconsole.iobinders

import chipyard.iobinders._
import chisel3._
import riscvconsole.devices.adcfifo._

case class ADCFIFOPort (val getIO: () => ADCFIFOIO)
  extends Port[ADCFIFOIO]
