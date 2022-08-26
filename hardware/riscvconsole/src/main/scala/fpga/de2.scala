package riscvconsole.fpga

import chisel3._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import chisel3.experimental.attach
import freechips.rocketchip.diplomacy.LazyModule
import riscvconsole.devices.codec.{CodecIO, CodecPinsFromPort, CodecSignals}
import riscvconsole.shell.de2._
import riscvconsole.shell.alteraLib.ALT_IOBUF
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._
import sifive.blocks.devices.gpio.GPIOPortIO
import sifive.blocks.devices.i2c.{I2CPins, I2CPinsFromPort, I2CPort}
import sifive.blocks.devices.spi.{SPIPins, SPIPinsFromPort, SPIPortIO}
import sifive.blocks.devices.uart.UARTPortIO

class DE2Top(implicit p: Parameters) extends DE2Shell
{
  val pll = Module(new pll)
  pll.io.inclk0 := sysclk
  pll.io.areset := !KEY(0)
  val clock = pll.io.c0
  val reset = !pll.io.locked

  withClockAndReset(clock, reset)
  {
    val greset = WireInit(false.B)
    val platform = withReset(greset){ Module(LazyModule(new RVCSystem).module) }
    greset := reset.asBool || platform.ndreset.getOrElse(false.B) // Put the ndreset from debug here

    // default all gpio
    platform.gpio.foreach { case gpio: GPIOPortIO =>
      gpio.pins.foreach(_.i.po.foreach(_ := false.B))

      //connect the led
      LEDR.foreach(_ := false.B)
      LEDG.foreach(_ := false.B)
      (LEDR.slice(0, 4) zip gpio.pins.slice(0, 4)).foreach
      {
        case (l, pin) =>
          l := pin.o.oval & pin.o.oe // ":="  <- connect
          pin.i.ival := false.B
      }

      //button into the gpio
      (KEY.slice(1, 4) zip gpio.pins.slice(4, 7)).foreach
      {
        case (b, pin) =>
          pin.i.ival := b
      }
      //switch into the gpio
      (SW.slice(1, 4) zip gpio.pins.slice(7, 10)).foreach
      {
        case (s, pin) =>
          pin.i.ival := s
      }
    }

    // JTAG
    platform.jtag.foreach { case jtag =>
      jtag.TDI := ALT_IOBUF(GPIO(0))
      jtag.TMS := ALT_IOBUF(GPIO(2))
      jtag.TCK := ALT_IOBUF(GPIO(4)).asClock()
      val TDO_as_base = Wire(new BasePin)
      TDO_as_base.o.oe := jtag.TDO.driven
      TDO_as_base.o.oval := jtag.TDO.data
      TDO_as_base.o.ie := false.B
      TDO_as_base.i.po.foreach(_ := false.B)
      ALT_IOBUF(GPIO(6), TDO_as_base)
      jtag.TRSTn.foreach(_ := SW(0))
    }

    platform.uart.foreach { case uart: UARTPortIO =>
      uart.rxd := ALT_IOBUF(UART_RXD)
      ALT_IOBUF(UART_TXD, uart.txd)
    }

    // SPI (for SD)
    // SPI (for SD)
    platform.spi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      ALT_IOBUF(SD_CLK, spi.sck)
      ALT_IOBUF(SD_DAT(3), spi.cs(0))
      ALT_IOBUF(SD_CMD, spi.dq(0))
      ALT_IOBUF(SD_DAT(0), spi.dq(1))
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

      ALT_IOBUF(GPIO(1), spi.sck)
      ALT_IOBUF(GPIO(3), spi.cs(0))
      ALT_IOBUF(GPIO(5), spi.dq(0))
      ALT_IOBUF(GPIO(7), spi.dq(1))
      ALT_IOBUF(GPIO(9), spi.dq(2))
      ALT_IOBUF(GPIO(11), spi.dq(3))
    }

    // I2C in AudioCodec
    platform.i2c.foreach{ case i2cc: I2CPort =>
      val i2c = Wire(new I2CPins(() => new BasePin()))
      i2c.scl.i.po.foreach(_ := false.B)
      i2c.sda.i.po.foreach(_ := false.B)
      I2CPinsFromPort(i2c, i2cc, clock, reset.asBool, 3)

      ALT_IOBUF(AUD.I2C_SCLK, i2c.scl)
      ALT_IOBUF(AUD.I2C_SDAT, i2c.sda)
    }

    // Codec in AudioCodec
    AUD.XCK := pll.io.c1.asBool // 5MHz
    AUD.DACDAT := 0.U
    platform.codec.foreach { case codecc: CodecIO =>
      val codec = Wire(new CodecSignals(() => new BasePin()))
      codec.AUD_ADCLRCK.i.po.foreach(_ := false.B)
      codec.AUD_DACLRCK.i.po.foreach(_ := false.B)
      codec.AUD_BCLK.i.po.foreach(_ := false.B)
      CodecPinsFromPort(codec, codecc, clock, reset.asBool, 3)

      AUD.DACDAT := codec.AUD_DACDAT
      codec.AUD_ADCDAT := AUD.ADCDAT
      ALT_IOBUF(AUD.ADCLRCK, codec.AUD_ADCLRCK)
      ALT_IOBUF(AUD.DACLRCK, codec.AUD_DACLRCK)
      ALT_IOBUF(AUD.BCLK, codec.AUD_BCLK)
    }

    // The DRAM
    DRAM.default
    platform.sdramio.foreach(DRAM.from_SDRAMIf)

    // Other clock not connected
    platform.otherclock := pll.io.c0
  }
}