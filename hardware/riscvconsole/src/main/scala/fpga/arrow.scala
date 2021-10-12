package riscvconsole.fpga

import chisel3._
import riscvconsole.system._
import chipsalliance.rocketchip.config._
import chisel3.experimental.attach
import riscvconsole.shell.ArrowLib._
import riscvconsole.shell.arrow._
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._

class ArrowTop(implicit p: Parameters) extends ArrowShell
{
  val pll = Module(new pll)
  pll.io.refclk := sysclk
  pll.io.rst := !btn(0)
  val clock = pll.io.outclk_0
  val reset = !pll.io.locked

  withClockAndReset(clock, reset)
  {
    val platform = Module(new RVCPlatform) //RVC:RISCV console(Full View)

    // default all gpio
    platform.io.gpio.foreach(_.i.po.foreach(_ := false.B))

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

    // JTAG
    platform.io.jtag.TDI := ALT_IOBUF(HSMC_TX_n(11))
    platform.io.jtag.TMS := ALT_IOBUF(HSMC_TX_p(11))
    platform.io.jtag.TCK := ALT_IOBUF(HSMC_TX_n(10)).asClock()
    val TDO_as_base = Wire(new BasePin)
    TDO_as_base.o.oe := platform.io.jtag.TDO.driven
    TDO_as_base.o.oval := platform.io.jtag.TDO.data
    TDO_as_base.o.ie := false.B
    TDO_as_base.i.po.foreach(_ := false.B)
    ALT_IOBUF(HSMC_TX_p(10), TDO_as_base)

    platform.io.uart_rxd := ALT_IOBUF(HSMC_RX_p(0))  // HSMC_RX_p_0 / G12 / J3 - 36
    ALT_IOBUF(HSMC_TX_p(0), platform.io.uart_txd)    // HSMC_TX_p_0 / A9 / J3 - 40

    platform.io.jtag_RSTn := sw(0)       //reset for the jtag

    // SPI (for SD)
    platform.io.spi.foreach(_.sck.i.po.foreach(_ := false.B))
    platform.io.spi.foreach(_.cs.foreach(_.i.po.foreach(_ := false.B)))
    platform.io.spi.foreach(_.dq.foreach(_.i.po.foreach(_ := false.B)))

    platform.io.spi.foreach { spi =>
      ALT_IOBUF(HSMC_TX_p(1), spi.sck)
      ALT_IOBUF(HSMC_TX_p(2), spi.cs(0))
      ALT_IOBUF(HSMC_TX_p(3), spi.dq(0))
      ALT_IOBUF(HSMC_TX_p(4), spi.dq(1))
      ALT_IOBUF(HSMC_TX_p(5), spi.dq(2))
      ALT_IOBUF(HSMC_TX_p(6), spi.dq(3))
    }

    // SPI flash
    platform.io.spiflash.foreach(_.sck.i.po.foreach(_ := false.B))
    platform.io.spiflash.foreach(_.cs.foreach(_.i.po.foreach(_ := false.B)))
    platform.io.spiflash.foreach(_.dq.foreach(_.i.po.foreach(_ := false.B)))

    platform.io.spiflash.foreach { spi =>
      ALT_IOBUF(HSMC_TX_p(7), spi.sck)
      ALT_IOBUF(HSMC_TX_p(8), spi.cs(0))
      ALT_IOBUF(HSMC_TX_p(9), spi.dq(0))
      ALT_IOBUF(HSMC_TX_p(10), spi.dq(1))
      ALT_IOBUF(HSMC_TX_p(11), spi.dq(2))
      ALT_IOBUF(HSMC_TX_p(12), spi.dq(3))
    }

    // I2C in AudioCodec
    platform.io.i2c.foreach(_.scl.i.po.foreach(_ := false.B))
    platform.io.i2c.foreach(_.sda.i.po.foreach(_ := false.B))

    platform.io.i2c.foreach{ i2c =>
      ALT_IOBUF(AUD.I2C_SCLK, i2c.scl)
      ALT_IOBUF(AUD.I2C_SDAT, i2c.sda)
    }

    // Codec in AudioCodec
    AUD.XCK := pll.io.outclk_1.asBool() // 5MHz
    platform.io.gpio(10).i.ival := false.B
    AUD.MUTE := platform.io.gpio(10).o.oval
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

    // The DDR3
    platform.io.ddr3refclk.foreach(_ := sysclk) // TODO: This clock is okay?
    platform.io.ddr3refrstn.foreach(_ := btn(0))
    platform.io.ddr3.foreach{ ddr3 =>
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
    platform.io.otherclock := false.B.asClock()
  }
}