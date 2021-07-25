package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.jtag.JTAGIO
import riscvconsole.devices.sdram.{SDRAMIf, SDRAMKey}
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._

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
    val sdram = Vec(p(SDRAMKey).size, new SDRAMIf)

    // SPI for SD
    val spi = MixedVec( p(PeripherySPIKey).map{A => new SPIPins(() => new BasePin(), A)} )

    // SPIFlash for SD
    val spiflash = MixedVec( p(PeripherySPIFlashKey).map{A => new SPIPins(() => new BasePin(), A)} )

    val otherclock = Input(Clock())
  })

  val greset = WireInit(false.B)
  val sys = withReset(greset){ Module(LazyModule(new RVCSystem).module) }
  greset := reset.toBool() || sys.debug.get.ndreset // Put the ndreset from debug here

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
    SPIPinsFromPort(b, a, sys.clock, sys.reset.toBool(), 3)
  }

  val spiflash = sys.qspi
  (spiflash zip io.spiflash).foreach{ case (a, b) =>
    b.sck.default()
    b.cs.foreach(_.default())
    b.dq.foreach(_.default())
    SPIPinsFromPort(b, a, sys.clock, sys.reset.toBool(), 3)
  }

  (io.sdram zip sys.sdramio).map{case (port, sys) => port <> sys}
  sys.otherclock := io.otherclock
}