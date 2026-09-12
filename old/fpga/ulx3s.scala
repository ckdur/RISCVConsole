package riscvconsole.fpga

import chisel3._
import riscvconsole.shell.ulx3s._
import riscvconsole.shell.latticeLib._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.diplomacy.LazyModule
import riscvconsole.devices.codec.CodecIO
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._
import sifive.blocks.devices.gpio.GPIOPortIO
import sifive.blocks.devices.spi.{SPIPins, SPIPinsFromPort, SPIPortIO}
import sifive.blocks.devices.uart.UARTPortIO

class ulx3sTop(implicit p: Parameters) extends ulx3sShell {
  val clock = clk_20mhz
  val reset = (!btn(0)) // Inverted logic

  withClockAndReset(clock, reset) {
    val greset = WireInit(false.B)
    val platform = withReset(greset){ Module(LazyModule(new RVCSystem).module) }
    greset := reset.asBool || platform.ndreset.getOrElse(false.B) // Put the ndreset from debug here

    // default all gpio
    platform.gpio.foreach { case gpio: GPIOPortIO =>
      gpio.pins.foreach(_.i.po.foreach(_ := false.B))

      (led zip gpio.pins.slice(0, 8)).foreach{
        case (l,pin) =>
          l := pin.o.oval & pin.o.oe
          pin.i.ival := false.B
      }

      (btn.slice(1,7) zip gpio.pins.slice(8, 14)).foreach {
        case (b, pin) =>
          pin.i.ival := b
      }
    }


    // JTAG
    platform.jtag.foreach { case jtag =>
      jtag.TDI := BB(gp(0))
      jtag.TMS := BB(gp(1))
      jtag.TCK := BB(gp(2)).asClock
      val TDO_as_base = Wire(new BasePin)
      TDO_as_base.o.oe := jtag.TDO.driven
      TDO_as_base.o.oval := jtag.TDO.data
      TDO_as_base.o.ie := false.B
      TDO_as_base.i.po.foreach(_ := false.B)
      BB(gp(3), TDO_as_base)
      jtag.TRSTn.foreach(_ := sw(0))
    }

    platform.uart.foreach{ case uart: UARTPortIO =>
      ftdi_rxd := uart.txd // ftdi_received -> fpga_transmitted
      uart.rxd := ftdi_txd // ftdi_transmitted -> fpga_received
    }

    sdram.from_SDRAMIf( platform.sdramio.head )
    platform.otherclock := clk_50mhz

    // SPI (for SD)
    platform.spi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      BB(sd.clk, spi.sck)
      BB(sd.d(3), spi.cs(0))
      BB(sd.cmd, spi.dq(0))
      BB(sd.d(0), spi.dq(1))
      //BB(sd.wp, spi.dq(2))
      //BB(sd.cdn, spi.dq(3))
      spi.dq(2).i.ival := false.B
      spi.dq(3).i.ival := false.B
    }

    // SPI flash
    platform.qspi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      BB(gp(4), spi.sck)
      BB(gp(5), spi.cs(0))
      BB(gp(6), spi.dq(0))
      BB(gp(7), spi.dq(1))
      BB(gp(8), spi.dq(2))
      BB(gp(9), spi.dq(3))
    }

    platform.codec.foreach { case codec: CodecIO =>
      codec.AUD_ADCDAT := false.B
      codec.AUD_ADCLRCK.in := false.B
      codec.AUD_DACLRCK.in := false.B
      codec.AUD_BCLK.in := false.B
    }
  }
}
