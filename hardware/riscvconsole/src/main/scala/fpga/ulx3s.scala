package riscvconsole.fpga

import chisel3._
import riscvconsole.shell.ulx3s._
import riscvconsole.shell.latticeLib._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._

class ulx3sTop(implicit p: Parameters) extends ulx3sShell {
  val clock = clk_20mhz
  val reset = (!btn(0)) // Inverted logic

  withClockAndReset(clock, reset) {
    val platform = Module(new RVCPlatform)
    platform.suggestName("platform")

    // default all gpio
    platform.io.gpio.foreach(_.i.po.foreach(_ := false.B))

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
    TDO_as_base.i.po.foreach(_ := false.B)
    BB(gp(3), TDO_as_base)

    ftdi_rxd := platform.io.uart_txd // ftdi_received -> fpga_transmitted
    platform.io.uart_rxd := ftdi_txd // ftdi_transmitted -> fpga_received

    platform.io.jtag_RSTn := sw(0)

    sdram.from_SDRAMIf( platform.io.sdram.head )
    platform.io.otherclock := clk_50mhz

    platform.io.spi.foreach(_.sck.i.po.foreach(_ := false.B))
    platform.io.spi.foreach(_.cs.foreach(_.i.po.foreach(_ := false.B)))
    platform.io.spi.foreach(_.dq.foreach(_.i.po.foreach(_ := false.B)))

    platform.io.spi.foreach{ spi =>
      BB(sd.clk, spi.sck)
      BB(sd.d(3), spi.cs(0))
      BB(sd.cmd, spi.dq(0))
      BB(sd.d(0), spi.dq(1))
      //BB(sd.wp, spi.dq(2))
      //BB(sd.cdn, spi.dq(3))
      spi.dq(2).i.ival := false.B
      spi.dq(3).i.ival := false.B
    }

    platform.io.spiflash.foreach(_.sck.i.po.foreach(_ := false.B))
    platform.io.spiflash.foreach(_.cs.foreach(_.i.po.foreach(_ := false.B)))
    platform.io.spiflash.foreach(_.dq.foreach(_.i.po.foreach(_ := false.B)))

    platform.io.spiflash.foreach { qspi =>
      BB(gp(4), qspi.sck)
      BB(gp(5), qspi.cs(0))
      BB(gp(6), qspi.dq(0))
      BB(gp(7), qspi.dq(1))
      BB(gp(8), qspi.dq(2))
      BB(gp(9), qspi.dq(3))
    }
    platform.io.codec.foreach { codec =>
      codec.AUD_ADCDAT := false.B
      codec.AUD_ADCLRCK.i.ival := false.B
      codec.AUD_DACLRCK.i.ival := false.B
      codec.AUD_BCLK.i.ival := false.B
      codec.AUD_ADCLRCK.i.po.foreach(_ := false.B)
      codec.AUD_DACLRCK.i.po.foreach(_ := false.B)
      codec.AUD_BCLK.i.po.foreach(_ := false.B)
    }
  }
}
