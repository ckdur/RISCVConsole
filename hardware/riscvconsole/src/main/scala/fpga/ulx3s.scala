package riscvconsole.fpga

import chisel3._
import riscvconsole.shell.ulx3s._
import riscvconsole.shell.latticeLib._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._

class ulx3sTop(implicit p: Parameters) extends ulx3sShell {
  val clock = clk_50mhz
  val reset = (!btn(0)) // Inverted logic

  withClockAndReset(clock, reset) {
    val platform = Module(new RVCPlatform)
    platform.suggestName("platform")

    (led zip platform.io.gpio.slice(0, 8)).foreach{
      case (l,pin) =>
        l := pin.o.oval & pin.o.oe
        pin.i.ival := false.B
    }

    (btn.slice(1,7) zip platform.io.gpio.slice(8, 14)).foreach {
      case (b, pin) =>
        pin.i.ival := b
    }

    platform.io.jtag.TDI := BB(gp(0))
    platform.io.jtag.TMS := BB(gp(1))
    platform.io.jtag.TCK := BB(gp(2)).asClock()
    val TDO_as_base = Wire(new BasePin)
    TDO_as_base.o.oe := platform.io.jtag.TDO.driven
    TDO_as_base.o.oval := platform.io.jtag.TDO.data
    TDO_as_base.o.ie := false.B
    BB(gp(3), TDO_as_base)

    ftdi_rxd := platform.io.uart_txd // ftdi_received -> fpga_transmitted
    platform.io.uart_rxd := ftdi_txd // ftdi_transmitted -> fpga_received

    platform.io.jtag_RSTn := sw(0)

    sdram.from_SDRAMIf( platform.io.sdram.head )
  }
}
