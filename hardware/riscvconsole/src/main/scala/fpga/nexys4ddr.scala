package riscvconsole.fpga

import chisel3._
import riscvconsole.system._
import org.chipsalliance.cde.config._
import chisel3.experimental.attach
import freechips.rocketchip.devices.debug.{Debug, JtagDTMKey}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.subsystem.PeripheryBusKey
import freechips.rocketchip.util.ResetCatchAndSync
import riscvconsole.devices.codec.{CodecIO, CodecSignals}
import riscvconsole.shell.nexys4ddr._
import sifive.fpgashells.ip.xilinx._
import sifive.blocks.devices.pinctrl._
import riscvconsole.util._
import sifive.blocks.devices.gpio.GPIOPortIO
import sifive.blocks.devices.i2c.{I2CPins, I2CPinsFromPort, I2CPort}
import sifive.blocks.devices.spi.{SPIPins, SPIPinsFromPort, SPIPortIO}
import sifive.blocks.devices.uart.UARTPortIO
import sifive.fpgashells.clocks._

class Nexys4DDRTop(implicit p: Parameters) extends Nexys4DDRShell
{
  // PLL instance
  val freq = p(PeripheryBusKey).dtsFrequency.getOrElse(BigInt(50000000)).toDouble / 1000000.0 // NOTE: Getting this from PeripheryBusKey
  val c = new PLLParameters(
    name = "pll",
    input = PLLInClockParameters(freqMHz = 100.0, feedback = true),
    req = Seq(
      PLLOutClockParameters(freqMHz = freq),
      PLLOutClockParameters(freqMHz = 166.666), // For sys_clk_i
      PLLOutClockParameters(freqMHz = 200.0) // For ref_clk
    )
  )
  val pll = Module(new Series7MMCM(c))
  pll.io.clk_in1 := CLK100MHZ
  pll.io.reset := !ck_rst

  val clock = pll.io.clk_out1.get
  val reset = WireInit(!pll.io.locked)

  withClockAndReset(clock, reset)
  {
    val greset = WireInit(false.B)
    val platform = withClockAndReset(clock, greset){ Module(LazyModule(new RVCSystem).module) }
    greset := reset.asBool || platform.outer.debug.map(_.ndreset).getOrElse(false.B) // Put the ndreset from debug here
    platform.clock := clock
    platform.reset := greset

    // default all gpio
    platform.gpio.foreach{ case gpio: GPIOPortIO =>
      gpio.pins.foreach(_.i.po.foreach(_ := false.B))

      //connect the led and sw
      ((led ++ sw) zip gpio.pins).foreach { case (l, pin) =>
        IOBUF(l, pin.toBasePin())
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
      jtag.jtag.TDI := IOBUF(jd(4))
      jtag.jtag.TMS := IOBUF(jd(5))
      jtag.jtag.TCK := IOBUF(jd(2)).asClock
      val TDO_as_base = Wire(new BasePin)
      TDO_as_base.o.oe := jtag.jtag.TDO.driven
      TDO_as_base.o.oval := jtag.jtag.TDO.data
      TDO_as_base.o.ie := false.B
      TDO_as_base.i.po.foreach(_ := false.B)
      IOBUF(jd(0), TDO_as_base)
      jtag.jtag.TRSTn.foreach(_ := IOBUF(jd(6)))

      PULLUP(jd(4))
      PULLUP(jd(5))
      PULLUP(jd(6))
    }

    platform.uart.foreach{ case uart: UARTPortIO =>
      uart.rxd := IOBUF(uart_txd_in)
      IOBUF(uart_rxd_out, uart.txd)
    }

    // SPI (for SD)
    platform.spi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      IOBUF(ja(0), spi.cs(0))
      IOBUF(ja(1), spi.dq(0))
      IOBUF(ja(2), spi.dq(1))
      IOBUF(ja(3), spi.sck)
      IOBUF(ja(4), spi.dq(2))
      IOBUF(ja(5), spi.dq(3))
    }

    // SPI flash
    platform.qspi.foreach{ case spic: SPIPortIO =>
      val spi = Wire(new SPIPins(() => new BasePin(), spic.c))
      spi.sck.i.po.foreach(_ := false.B)
      spi.cs.foreach(_.i.po.foreach(_ := false.B))
      spi.dq.foreach(_.i.po.foreach(_ := false.B))
      SPIPinsFromPort(spi, spic, clock, reset.asBool, 3)

      IOBUF(jc(3), spi.sck)
      IOBUF(jc(0), spi.cs(0))
      IOBUF(jc(1), spi.dq(0))
      IOBUF(jc(2), spi.dq(1))
      IOBUF(jc(6), spi.dq(2))
      IOBUF(jc(7), spi.dq(3))
    }

    // I2C
    platform.i2c.foreach{ case i2cc: I2CPort =>
      val i2c = Wire(new I2CPins(() => new BasePin()))
      i2c.scl.i.po.foreach(_ := false.B)
      i2c.sda.i.po.foreach(_ := false.B)
      I2CPinsFromPort(i2c, i2cc, clock, reset.asBool, 3)

      IOBUF(jc(4), i2c.scl)
      IOBUF(jc(5), i2c.sda)
    }

    platform.codec.foreach { case codec: CodecIO =>
      codec.AUD_ADCDAT := false.B
      codec.AUD_ADCLRCK.in := false.B
      codec.AUD_DACLRCK.in := false.B
      codec.AUD_BCLK.in := false.B
    }

    platform.nexys4DDRMIGPorts.foreach{ case mig =>
      ddr <> mig
      // MIG connections, like resets and stuff
      mig.sys_clk_i := pll.io.clk_out2.get.asBool
      mig.clk_ref_i := pll.io.clk_out3.get.asBool
      mig.aresetn := pll.io.locked
      mig.sys_rst := ResetCatchAndSync(pll.io.clk_out2.get, !pll.io.locked)
      reset := ResetCatchAndSync(pll.io.clk_out1.get, mig.ui_clk_sync_rst)
    }

    // Other clock not connected
    platform.otherclock := false.B.asClock
  }
}
