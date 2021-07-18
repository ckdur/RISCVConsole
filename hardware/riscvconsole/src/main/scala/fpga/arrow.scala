package riscvconsole.fpga

import chisel3._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import riscvconsole.shell.ArrowLib._
import riscvconsole.shell.arrow._
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._

class ArrowTop(implicit p: Parameters) extends ArrowShell
{
  val clock = clk_OSC_50_B5B
  val reset = (!btn(0))

  withClockAndReset(clock, reset)
  {
    val platform = Module(new RVCPlatform) //RVC:RISCV console(Full View)

    //connect the led
    (led zip platform.io.gpio.slice(0, 4)).foreach
    {
      case (l, pin) =>
        l := pin.o.oval & pin.o.oe // ":="  <- connect
        pin.i.ival := false.B//B(AKA Bool): boolean in hardware , Boolean:boolean in Java
    }

    //button into the gpio
    (btn.slice(1, 4) zip platform.io.gpio.slice(4, 7)).foreach
    {
      case (b, pin) =>
        pin.i.ival := b
    }
    //switch into the gpio
    (sw.slice(1, 4) zip platform.io.gpio.slice(7, 10)).foreach
    {
      case (s, pin) =>
        pin.i.ival := s
    }

    platform.io.jtag.TDI := ALT_IOBUF(HSMC_D(0))
    platform.io.jtag.TMS := ALT_IOBUF(HSMC_D(1))
    platform.io.jtag.TCK := ALT_IOBUF(HSMC_D(2)).asClock()
    val TDO_as_base = Wire(new BasePin)
    TDO_as_base.o.oe := platform.io.jtag.TDO.driven
    TDO_as_base.o.oval := platform.io.jtag.TDO.data
    TDO_as_base.o.ie := false.B
    ALT_IOBUF(HSMC_D(3), TDO_as_base)

    platform.io.uart_rxd := ALT_IOBUF(HSMC_RX_p(0))  //input
    ALT_IOBUF(HSMC_TX_p(0), platform.io.uart_txd)

    platform.io.jtag_RSTn := sw(0)       //reset for the jtag
  }
}