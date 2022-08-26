package riscvconsole.shell.de2

import chisel3._
import chisel3.experimental.{Analog, attach}
import riscvconsole.devices.sdram.SDRAMIf
import riscvconsole.shell.alteraLib._

class DE2SDRAM extends Bundle {
  val CLK = Output(Bool())
  val CKE = Output(Bool())
  val CS_N = Output(Bool())
  val RAS_N = Output(Bool())
  val CAS_N = Output(Bool())
  val WE_N = Output(Bool())
  val DQM = Output(UInt(4.W))
  val ADDR = Output(UInt(13.W))
  val BA = Output(UInt(2.W))
  val DQ = Vec(32, Analog(1.W))
  def from_SDRAMIf(io: SDRAMIf) = {
    CLK := io.sdram_clk_o
    CKE := io.sdram_cke_o
    CS_N := io.sdram_cs_o
    RAS_N := io.sdram_ras_o
    CAS_N := io.sdram_cas_o
    WE_N := io.sdram_we_o
    DQM := io.sdram_dqm_o
    ADDR := io.sdram_addr_o
    BA := io.sdram_ba_o
    io.sdram_data_i := VecInit((io.sdram_data_o.asBools() zip DQ).map{
      case (o, an) =>
        val b = Module(new ALT_IOBUF)
        b.io.oe := io.sdram_drive_o
        b.io.i := o
        attach(b.io.io, an)
        b.io.o
    }).asUInt()
  }
  def default = {
    CLK := false.B
    CKE := false.B
    CS_N := false.B
    RAS_N := false.B
    CAS_N := false.B
    WE_N := false.B
    DQM := 0.U
    ADDR := 0.U
    BA := 0.U
  }
}

class AUD_CODEC_DE2_PORT extends Bundle {
  val XCK = Output(Bool())
  val BCLK = Analog(1.W)
  val DACDAT = Output(Bool())
  val DACLRCK = Analog(1.W)
  val ADCDAT = Input(Bool())
  val ADCLRCK = Analog(1.W)
  val I2C_SCLK = Analog(1.W)
  val I2C_SDAT = Analog(1.W)
}

class pll extends BlackBox {
  val io = IO(new Bundle {
    val inclk0 = Input(Clock())
    val areset = Input(Bool())
    val c0 = Output(Clock())
    val c1 = Output(Clock())
    val locked = Output(Bool())
  })
}

class DE2Shell extends RawModule {
  val CLOCK_50 = IO(Input(Clock()))

  val LEDR = IO(Vec(18, Output(Bool())))
  val LEDG = IO(Vec(9, Output(Bool())))
  val KEY = IO(Vec(4, Input(Bool())))
  val SW = IO(Vec(18, Input(Bool())))

  //val HSMC_D = IO(Vec(4, Analog(1.W)))
  //val HSMC_RX_D_P = IO(Vec(17, Analog(1.W)))
  //val HSMC_TX_D_P = IO(Vec(17, Analog(1.W)))
  //val HSMC_RX_D_N = IO(Vec(17, Analog(1.W)))
  //val HSMC_TX_D_N = IO(Vec(17, Analog(1.W)))

  val GPIO = IO(Vec(36, Analog(1.W)))

  val AUD = IO(new AUD_CODEC_DE2_PORT)

  val DRAM = IO(new DE2SDRAM)

  val UART_TXD = IO(Analog(1.W))
  val UART_RXD = IO(Analog(1.W))

  val SD_CMD = IO(Analog(1.W))
  val SD_CLK = IO(Analog(1.W))
  val SD_WP_N = IO(Analog(1.W))
  val SD_DAT = IO(Vec(4, Analog(1.W)))

  //val ckctrl_0 = Module(new clkctrl)
  //ckctrl_0.io.inclk := CLOCK_50
  val sysclk = CLOCK_50
}
