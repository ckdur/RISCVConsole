package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.diplomacy.LazyModule
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pinctrl._
import sifive.blocks.devices.uart._

class RVCPlatform(implicit p: Parameters) extends Module
{
  val ngpio = p(PeripheryGPIOKey)(0).width
  val io = IO(new Bundle {
    val uart_txd = Output(Bool())
    val uart_rxd = Input(Bool())

    val gpio = Vec(ngpio, new EnhancedPin)
  })

  val sysl = LazyModule(new RVCSystem)
  val sys = Module(sysl.module)

  sys.resetctrl.foreach { rstctrl => rstctrl.hartIsInReset.foreach(_ := reset)}

  Debug.connectDebugClockHelper(sys.debug.get, reset, clock)
  Debug.tieoffDebug(sys.debug)

  val gpio: GPIOPortIO = sys.gpio(0)
  (gpio.pins zip io.gpio).foreach{ case (a, b) => b <> a}

  val uart: UARTPortIO = sys.uart(0)
  io.uart_txd := uart.txd
  uart.rxd := io.uart_rxd
}