package riscvconsole.shell.arrow

import chisel3._
import chisel3.experimental.Analog
import riscvconsole.shell.alteraLib._

class AUD_CODEC_PORT extends Bundle {
  val XCK = Output(Bool())
  val BCLK = Analog(1.W)
  val DACDAT = Output(Bool())
  val DACLRCK = Analog(1.W)
  val ADCDAT = Input(Bool())
  val ADCLRCK = Analog(1.W)
  val MUTE = Output(Bool())
  val I2C_SCLK = Analog(1.W)
  val I2C_SDAT = Analog(1.W)
}

class pll extends BlackBox {
  val io = IO(new Bundle {
    val refclk = Input(Clock())
    val rst = Input(Bool())
    val outclk_0 = Output(Clock())
    val outclk_1 = Output(Clock())
    val locked = Output(Bool())
  })
}

class ArrowShell extends RawModule {
  val clk_OSC_50_B5B = IO(Input(Clock()))

  val led = IO(Vec(4, Output(Bool())))
  val btn = IO(Vec(4, Input(Bool())))
  val sw = IO(Vec(4, Input(Bool())))

  val HSMC_D = IO(Vec(4, Analog(1.W)))
  //val HSMC_GXB_RX_p = IO(Vec(8, Analog(1.W)))
  //val HSMC_GXB_TX_p = IO(Vec(8, Analog(1.W)))
  //val HSMC_GXB_RX_n = IO(Vec(8, Analog(1.W)))
  //val HSMC_GXB_TX_n = IO(Vec(8, Analog(1.W)))
  val HSMC_RX_p = IO(Vec(17, Analog(1.W)))
  val HSMC_TX_p = IO(Vec(17, Analog(1.W)))
  val HSMC_RX_n = IO(Vec(17, Analog(1.W)))
  val HSMC_TX_n = IO(Vec(17, Analog(1.W)))

  val AUD = IO(new AUD_CODEC_PORT)

  val DDR3 = IO(new DDR3_PORT)

  val ckctrl_0 = Module(new clkctrl)
  ckctrl_0.io.inclk := clk_OSC_50_B5B
  val sysclk = ckctrl_0.io.outclk
}