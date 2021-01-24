package riscvconsole.shell.ulx3s

import chisel3._
import chisel3.experimental.Analog

class ulx3sShell extends RawModule {
  val clk_25mhz = IO(Input(Clock()))

  val ftdi_rxd = IO(Input(Bool()))
  val ftdi_txd = IO(Output(Bool()))
  // TODO: Maybe is going to be problems if we do not connect all the FTDI SERIAL?

  val led = IO(Vec(8, Output(Bool())))
  val btn = IO(Vec(7, Input(Bool())))
  val sw = IO(Vec(4, Input(Bool())))

  val gp = IO(Vec(28, Analog(1.W)))
  val gn = IO(Vec(28, Analog(1.W)))

  // TODO: Put the PLL
}