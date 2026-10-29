package riscvconsole.iobinders

import chipyard.iobinders._
import chisel3._
import riscvconsole.devices.adcfifo._
import riscvconsole.devices.rocounter._

case class ADCFIFOPort (val getIO: () => ADCFIFOIO)
  extends Port[ADCFIFOIO]

case class ROCounterPort (val getIO: () => ROCounterIO)
  extends Port[ROCounterIO]
