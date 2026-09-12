package riscvconsole.fpga

import chisel3._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import chisel3.experimental.attach
import freechips.rocketchip.diplomacy.LazyModule
import riscvconsole.devices.codec.{CodecIO, CodecPinsFromPort, CodecSignals}
import riscvconsole.shell.alteraLib._
import riscvconsole.shell.arrow._
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._
import sifive.blocks.devices.gpio.GPIOPortIO
import sifive.blocks.devices.i2c.{I2CPins, I2CPinsFromPort, I2CPort, I2CSignals}
import sifive.blocks.devices.spi.{SPIPins, SPIPinsFromPort, SPIPortIO}
import sifive.blocks.devices.uart.UARTPortIO

class ArrowTop(implicit p: Parameters) extends ArrowShell
{
  val pll = Module(new pll)
  pll.io.refclk := sysclk
  pll.io.rst := !btn(0)
  val clock = pll.io.outclk_0
  val reset = !pll.io.locked

  withClockAndReset(clock, reset)
  {
    val greset = WireInit(false.B)
    val platform = withReset(greset){ Module(LazyModule(new RVCSystem).module) }
    greset := reset.asBool || platform.ndreset.getOrElse(false.B) // Put the ndreset from debug here

    // default all gpio
    platform.gpio.foreach{ case gpio: GPIOPortIO =>
      gpio.pins.foreach(_.i.po.foreach(_ := false.B))

      //connect the led
      (led zip gpio.pins.slice(0, 4)).foreach
      {
        case (l, pin) =>
          l := pin.o.oval & pin.o.oe // ":="  <- connect
          pin.i.ival := false.B//B(AKA Bool): boolean in hardware , Boolean:boolean in Java
      }

      //button into the gpio
      (btn.slice(1, 4) zip gpio.pins.slice(4, 7)).foreach
      {
        case (b, pin) =>
          pin.i.ival := b
      }
      //switch into the gpio
      (sw.slice(1, 4) zip gpio.pins.slice(7, 10)).foreach
      {
        case (s, pin) =>
          pin.i.ival := s
      }
    }

    // JTAG
    platform.jtag.foreach{ case jtag =>
      jtag.TDI := ALT_IOBUF(HSMC_TX_n(11))
      jtag.TMS := ALT_IOBUF(HSMC_TX_p(11))
      jtag.TCK := ALT_IOBUF(HSMC_TX_n(10)).asClock
      val TDO_as_base = Wire(new BasePin)
      TDO_as_base.o.oe := jtag.TDO.driven
      TDO_as_base.o.oval := jtag.TDO.data
      TDO_as_base.o.ie := false.B
      TDO_as_base.i.po.foreach(_ := false.B)
      ALT_IOBUF(HSMC_TX_p(10), TDO_as_base)
      jtag.TRSTn.foreach(_ := sw(0))
    }

    platform.uart.foreach{ case uart: UARTPortIO =>
      uart.rxd := ALT_IOBUF(HSMC_TX_n(0))  // HSMC_RX_p_0 / G12 / J3 - 36
      ALT_IOBUF(HSMC_TX_p(0), uart.txd)    // HSMC_TX_p_0 / A9 / J3 - 40
    }

    // SPI (for SD)
    platform.spi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      ALT_IOBUF(HSMC_TX_p(1), spi.sck)
      ALT_IOBUF(HSMC_TX_n(1), spi.cs(0))
      ALT_IOBUF(HSMC_TX_p(2), spi.dq(0))
      ALT_IOBUF(HSMC_TX_n(2), spi.dq(1))
      ALT_IOBUF(HSMC_TX_p(3), spi.dq(2))
      ALT_IOBUF(HSMC_TX_n(3), spi.dq(3))
    }

    // SPI flash
    platform.qspi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      ALT_IOBUF(HSMC_RX_p(0), spi.sck)
      ALT_IOBUF(HSMC_RX_n(0), spi.cs(0))
      ALT_IOBUF(HSMC_RX_p(1), spi.dq(0))
      ALT_IOBUF(HSMC_RX_n(1), spi.dq(1))
      ALT_IOBUF(HSMC_RX_p(2), spi.dq(2))
      ALT_IOBUF(HSMC_RX_n(2), spi.dq(3))
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
    AUD.XCK := pll.io.outclk_1.asBool // 5MHz
    platform.gpio(0).asInstanceOf[GPIOPortIO].pins(10).i.ival := false.B
    AUD.MUTE := platform.gpio(0).asInstanceOf[GPIOPortIO].pins(10).o.oval
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

    // The DDR3
    platform.ddr3refclk.foreach(_ := sysclk) // TODO: This clock is okay?
    platform.ddr3refrstn.foreach(_ := btn(0))
    platform.ddr3Ports.foreach{ ddr3 =>
      DDR3.A := ddr3.memory_mem_a
      DDR3.BA := ddr3.memory_mem_ba
      DDR3.CK_p := ddr3.memory_mem_ck
      DDR3.CK_n := ddr3.memory_mem_ck_n
      DDR3.CKE := ddr3.memory_mem_cke
      DDR3.CS_n := ddr3.memory_mem_cs_n
      DDR3.DM := ddr3.memory_mem_dm
      DDR3.RAS_n := ddr3.memory_mem_ras_n
      DDR3.CAS_n := ddr3.memory_mem_cas_n
      DDR3.WE_n := ddr3.memory_mem_we_n
      attach(DDR3.DQ, ddr3.memory_mem_dq)
      attach(DDR3.DQS_p, ddr3.memory_mem_dqs)
      attach(DDR3.DQS_n, ddr3.memory_mem_dqs_n)
      DDR3.ODT := ddr3.memory_mem_odt
      DDR3.RESET_n := ddr3.memory_mem_reset_n.getOrElse(true.B)
      ddr3.oct_rzqin := DDR3.RZQ

      // If there is a DDR3 implementation, we override the leds to show the DDR3 status
      // TODO: Do this in the system
      led(0) := ddr3.mem_status_local_cal_fail
      led(1) := ddr3.mem_status_local_cal_success
      led(2) := ddr3.mem_status_local_init_done
    }

    // Other clock not connected
    platform.otherclock := false.B.asClock
  }
}