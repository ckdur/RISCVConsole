package riscvconsole.shell.ulx3s

import chisel3._
import chisel3.experimental.Analog
import riscvconsole.devices.sdram.SDRAMIf
import riscvconsole.shell.latticeLib._

class ulx3sShell extends RawModule {
  val clk_25mhz = IO(Input(Clock()))

  val ftdi_rxd = IO(Output(Bool()))
  val ftdi_txd = IO(Input(Bool()))
  // TODO: Maybe is going to be problems if we do not connect all the FTDI SERIAL?

  val led = IO(Vec(8, Output(Bool())))
  val btn = IO(Vec(7, Input(Bool())))
  val sw = IO(Vec(4, Input(Bool())))

  val gp = IO(Vec(28, Analog(1.W)))
  val gn = IO(Vec(28, Analog(1.W)))

  val sdram = IO(new ULX3SSDRAM)

  val sd = IO(new Bundle{
    val clk = Analog(1.W)
    val cmd = Analog(1.W)
    val d = Vec(4, Analog(1.W))
    val wp = Analog(1.W)
    val cdn = Analog(1.W)
  })

  // Put the PLL
  val pll = withClockAndReset(clk_25mhz, false.B) {
    ecp5pll(ecp5pllConfig(
      in_hz = 25000000,
      out0_hz = 50000000,
      out1_hz = 20000000
    ))
  }
  pll.io.standby := false.B
  pll.io.phasesel := 0.U
  pll.io.phasedir := false.B
  pll.io.phasestep := false.B
  pll.io.phaseloadreg := false.B
  val clk_50mhz = pll.io.clk_o(0)
  val clk_20mhz = pll.io.clk_o(1)
  val locked = pll.io.locked
}