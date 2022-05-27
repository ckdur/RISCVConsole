package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.jtag.JTAGIO
import freechips.rocketchip.subsystem.ExtMem
import riscvconsole.devices.altera.ddr3._
import riscvconsole.devices.codec._
import riscvconsole.devices.sdram.{SDRAMIf, SDRAMKey}
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import freechips.rocketchip.util._

class RVCPlatform(implicit p: Parameters) extends Module
{
  val ngpio = p(PeripheryGPIOKey)(0).width
  val io = IO(new Bundle {
    val uart_txd = Output(Bool())
    val uart_rxd = Input(Bool())

    val gpio = Vec(ngpio, new EnhancedPin)

    // Jtag port
    val jtag = Flipped(new JTAGIO(false))
    val jtag_RSTn = Input(Bool())

    // SDRAM port
    val sdram = MixedVec(p(SDRAMKey).map{ A => new SDRAMIf(A.sdcfg)})

    // Altera DDR3 port
    val ddr3 = p(QsysDDR3Mem).map{A => new QsysIO}
    val ddr3refclk = p(QsysDDR3Mem).map{A => Input(Clock())}
    val ddr3refrstn = p(QsysDDR3Mem).map{A => Input(Bool())}

    // Codec port
    val codec = Vec(p(PeripheryCodecKey).size, new CodecSignals(() => new BasePin()))

    // SPI for SD
    val spi = MixedVec( p(PeripherySPIKey).map{A => new SPIPins(() => new BasePin(), A)} )

    // SPIFlash for SD
    val spiflash = MixedVec( p(PeripherySPIFlashKey).map{A => new SPIPins(() => new BasePin(), A)} )

    // I2C
    val i2c = MixedVec(p(PeripheryI2CKey).map{ A => new I2CPins(() => new BasePin() )})

    val otherclock = Input(Clock())
  })

  val greset = WireInit(false.B)
  val sys = withReset(greset){ Module(LazyModule(new RVCSystem).module) }
  greset := reset.asBool || sys.debug.get.ndreset // Put the ndreset from debug here

  sys.resetctrl.foreach { rstctrl => rstctrl.hartIsInReset.foreach(_ := greset)}

  Debug.connectDebugClockHelper(sys.debug.get, reset, clock)
  io.jtag <> sys.debug.get.systemjtag.get.jtag
  sys.debug.get.systemjtag.get.mfr_id := 0.U
  sys.debug.get.systemjtag.get.part_number := 0.U
  sys.debug.get.systemjtag.get.version := 0.U
  sys.debug.get.systemjtag.get.reset := (!io.jtag_RSTn).asAsyncReset
  //Debug.tieoffDebug(sys.debug)

  val gpio: GPIOPortIO = sys.gpio(0)
  (gpio.pins zip io.gpio).foreach{ case (a, b) => b <> a}

  val uart: UARTPortIO = sys.uart(0)
  io.uart_txd := uart.txd
  uart.rxd := io.uart_rxd

  val spi = sys.spi
  (spi zip io.spi).foreach{ case (a, b) =>
    b.sck.default()
    b.cs.foreach(_.default())
    b.dq.foreach(_.default())
    SPIPinsFromPort(b, a, sys.clock, sys.reset.asBool, 3)
  }

  val spiflash = sys.qspi
  (spiflash zip io.spiflash).foreach{ case (a, b) =>
    b.sck.default()
    b.cs.foreach(_.default())
    b.dq.foreach(_.default())
    SPIPinsFromPort(b, a, sys.clock, sys.reset.asBool, 3)
  }

  val i2c = sys.i2c
  (i2c zip io.i2c).foreach { case (a, b) =>
    b.scl.default()
    b.sda.default()
    I2CPinsFromPort(b, a, sys.clock, sys.reset.asBool, 3)
  }

  val codec = sys.codec
  (codec zip io.codec).foreach { case (a, b) =>
    b.AUD_BCLK.default()
    b.AUD_ADCLRCK.default()
    b.AUD_DACLRCK.default()
    CodecPinsFromPort(b, a, sys.clock, sys.reset.asBool, 3)
  }

  (io.sdram zip sys.sdramio).map{case (port, sys) => port <> sys}
  (io.ddr3 zip sys.ddr3Ports).map{case (port, sys) => port <> sys}
  (io.ddr3refclk zip sys.ddr3refclk).map{case (port, sys) => sys := port}
  (io.ddr3refrstn zip sys.ddr3refrstn).map{case (port, sys) => sys := port}
  sys.otherclock := io.otherclock
}