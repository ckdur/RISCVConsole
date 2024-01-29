package riscvconsole.fpga

import chisel3._
import chisel3.experimental.attach
import freechips.rocketchip.devices.debug._
import riscvconsole.shell.ulx3s._
import sifive.fpgashells.ip.lattice._
import riscvconsole.system._
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.util.ResetCatchAndSync
import riscvconsole.devices.codec.CodecIO
import sifive.fpgashells.shell.SDRAMIf
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._
import sifive.blocks.devices.gpio.GPIOPortIO
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart.UARTPortIO

object ulx2sUtil {
  def from_SDRAMIf(port: ULX3SSDRAM, io: SDRAMIf) = {
    port.sdram_clk_o := io.sdram_clk_o
    port.sdram_cke_o := io.sdram_cke_o
    port.sdram_cs_o := io.sdram_cs_o
    port.sdram_ras_o := io.sdram_ras_o
    port.sdram_cas_o := io.sdram_cas_o
    port.sdram_we_o := io.sdram_we_o
    port.sdram_dqm_o := io.sdram_dqm_o
    port.sdram_addr_o := io.sdram_addr_o
    port.sdram_ba_o := io.sdram_ba_o
    io.sdram_data_i := VecInit((io.sdram_data_o.asBools zip port.sdram_data_io).map{
      case (o, an) =>
        val b = Module(new BB)
        b.io.T := !io.sdram_drive_o
        b.io.I := o
        attach(b.io.B, an)
        b.io.O
    }).asUInt
  }
}

class ulx3sTop(implicit p: Parameters) extends ulx3sShell {
  val clock = clk_20mhz
  val reset = (!btn(0)) // Inverted logic

  withClockAndReset(clock, reset) {
    val greset = WireInit(false.B)
    val platform = withClockAndReset(clock, greset){ Module(LazyModule(new RVCSystem).module) }
    greset := reset.asBool || platform.outer.debug.map(_.ndreset).getOrElse(false.B) // Put the ndreset from debug here
    platform.clock := clock
    platform.reset := greset

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

    // Debug additional connections
    platform.outer.debug.foreach{ d =>
      platform.outer.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := reset } }
      d.systemjtag.map { j =>
        j.reset := ResetCatchAndSync(j.jtag.TCK, reset)
        j.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
        j.part_number := p(JtagDTMKey).idcodePartNum.U(16.W)
        j.version := p(JtagDTMKey).idcodeVersion.U(4.W)
      }
      Debug.connectDebugClockAndReset(Some(d), clock)
    }


    // JTAG
    platform.outer.debug.map(_.systemjtag).foreach{ case Some(jtag) =>
      jtag.jtag.TDI := BB(gp(0))
      jtag.jtag.TMS := BB(gp(1))
      jtag.jtag.TCK := BB(gp(2)).asClock
      val TDO_as_base = Wire(new BasePin)
      TDO_as_base.o.oe := jtag.jtag.TDO.driven
      TDO_as_base.o.oval := jtag.jtag.TDO.data
      TDO_as_base.o.ie := false.B
      TDO_as_base.i.po.foreach(_ := false.B)
      BB(gp(3), TDO_as_base)
      jtag.jtag.TRSTn.foreach(_ := sw(0))
    }

    platform.uart.foreach{ case uart: UARTPortIO =>
      ftdi_rxd := uart.txd // ftdi_received -> fpga_transmitted
      uart.rxd := ftdi_txd // ftdi_transmitted -> fpga_received
    }

    platform.sdramio.foreach(io => ulx2sUtil.from_SDRAMIf(sdram, io))
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

    platform.i2c.foreach{ case i2cc: I2CPort =>
      val i2c = Wire(new I2CPins(() => new BasePin()))
      i2c.scl.i.po.foreach(_ := false.B)
      i2c.sda.i.po.foreach(_ := false.B)
      I2CPinsFromPort(i2c, i2cc, clock, reset.asBool, 3)
      BB(gp(10), i2c.scl)
      BB(gp(11), i2c.sda)
    }

    platform.codec.foreach { case codec: CodecIO =>
      codec.AUD_ADCDAT := false.B
      codec.AUD_ADCLRCK.in := false.B
      codec.AUD_DACLRCK.in := false.B
      codec.AUD_BCLK.in := false.B
    }
  }
}
