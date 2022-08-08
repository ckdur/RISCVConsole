package riscvconsole.fpga

import chisel3._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import chisel3.experimental.attach
import riscvconsole.shell.de2._
import riscvconsole.shell.ArrowLib.{ALT_IOBUF}
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._

class DE2Top(implicit p: Parameters) extends DE2Shell
{
  val pll = Module(new pll)
  pll.io.inclk0 := sysclk
  pll.io.areset := !KEY(0)
  val clock = pll.io.c0
  val reset = !pll.io.locked

  withClockAndReset(clock, reset)
  {
    val platform = Module(new RVCPlatform) //RVC:RISCV console(Full View)

    // default all gpio
    platform.io.gpio.foreach(_.i.po.foreach(_ := false.B))

    //connect the led
    LEDR.foreach(_ := false.B)
    LEDG.foreach(_ := false.B)
    (LEDR.slice(0, 4) zip platform.io.gpio.slice(0, 4)).foreach
    {
      case (l, pin) =>
        l := pin.o.oval & pin.o.oe // ":="  <- connect
        pin.i.ival := false.B
    }

    //button into the gpio
    (KEY.slice(1, 4) zip platform.io.gpio.slice(4, 7)).foreach
    {
      case (b, pin) =>
        pin.i.ival := b
    }
    //switch into the gpio
    (SW.slice(1, 4) zip platform.io.gpio.slice(7, 10)).foreach
    {
      case (s, pin) =>
        pin.i.ival := s
    }

    // JTAG
    platform.io.jtag.TDI := ALT_IOBUF(GPIO(0))
    platform.io.jtag.TMS := ALT_IOBUF(GPIO(2))
    platform.io.jtag.TCK := ALT_IOBUF(GPIO(4)).asClock()
    val TDO_as_base = Wire(new BasePin)
    TDO_as_base.o.oe := platform.io.jtag.TDO.driven
    TDO_as_base.o.oval := platform.io.jtag.TDO.data
    TDO_as_base.o.ie := false.B
    TDO_as_base.i.po.foreach(_ := false.B)
    ALT_IOBUF(GPIO(6), TDO_as_base)

    //platform.io.uart_rxd := ALT_IOBUF(GPIO(8))
    //ALT_IOBUF(GPIO(10), platform.io.uart_txd)
    platform.io.uart_rxd := ALT_IOBUF(UART_RXD)
    ALT_IOBUF(UART_TXD, platform.io.uart_txd)

    platform.io.jtag_RSTn := SW(0)       //reset for the jtag

    // SPI (for SD)
    platform.io.spi.foreach(_.sck.i.po.foreach(_ := false.B))
    platform.io.spi.foreach(_.cs.foreach(_.i.po.foreach(_ := false.B)))
    platform.io.spi.foreach(_.dq.foreach(_.i.po.foreach(_ := false.B)))

    platform.io.spi.foreach { spi =>
      ALT_IOBUF(SD_CLK, spi.sck)
      ALT_IOBUF(SD_DAT(3), spi.cs(0))
      ALT_IOBUF(SD_CMD, spi.dq(0))
      ALT_IOBUF(SD_DAT(0), spi.dq(1))
      spi.dq(2).i.ival := false.B
      spi.dq(3).i.ival := false.B
    }

    // SPI flash
    platform.io.spiflash.foreach(_.sck.i.po.foreach(_ := false.B))
    platform.io.spiflash.foreach(_.cs.foreach(_.i.po.foreach(_ := false.B)))
    platform.io.spiflash.foreach(_.dq.foreach(_.i.po.foreach(_ := false.B)))

    platform.io.spiflash.foreach { spi =>
      ALT_IOBUF(GPIO(1), spi.sck)
      ALT_IOBUF(GPIO(3), spi.cs(0))
      ALT_IOBUF(GPIO(5), spi.dq(0))
      ALT_IOBUF(GPIO(7), spi.dq(1))
      ALT_IOBUF(GPIO(9), spi.dq(2))
      ALT_IOBUF(GPIO(11), spi.dq(3))
    }

    // I2C in AudioCodec
    platform.io.i2c.foreach(_.scl.i.po.foreach(_ := false.B))
    platform.io.i2c.foreach(_.sda.i.po.foreach(_ := false.B))

    platform.io.i2c.foreach{ i2c =>
      ALT_IOBUF(AUD.I2C_SCLK, i2c.scl)
      ALT_IOBUF(AUD.I2C_SDAT, i2c.sda)
    }

    // Codec in AudioCodec
    AUD.XCK := pll.io.c1.asBool() // 5MHz
    platform.io.codec.foreach { codec =>
      codec.AUD_ADCLRCK.i.po.foreach(_ := false.B)
      codec.AUD_DACLRCK.i.po.foreach(_ := false.B)
      codec.AUD_BCLK.i.po.foreach(_ := false.B)

      AUD.DACDAT := codec.AUD_DACDAT
      codec.AUD_ADCDAT := AUD.ADCDAT
      ALT_IOBUF(AUD.ADCLRCK, codec.AUD_ADCLRCK)
      ALT_IOBUF(AUD.DACLRCK, codec.AUD_DACLRCK)
      ALT_IOBUF(AUD.BCLK, codec.AUD_BCLK)
    }

    // The DRAM
    platform.io.sdram.foreach(DRAM.from_SDRAMIf)

    // Other clock not connected
    platform.io.otherclock := pll.io.c0
  }
}