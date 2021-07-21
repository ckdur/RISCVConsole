package riscvconsole.devices.sdram

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util._

class sdramsim extends BlackBox with HasBlackBoxResource {
  val io = IO(Flipped(new SDRAMIf {
    val reset = Output(Bool())
  }))
  addResource("/sdram/sdramsim.v")
  addResource("/sdram/sdramsim.cc")
  addResource("/sdram/sdramsim.h")
}

object sdramsim {
  def apply(io: SDRAMIf, reset: Bool) = {
    val sdram = Module(new sdramsim)
    sdram.io.sdram_clk_o := io.sdram_clk_o
    sdram.io.sdram_cke_o := io.sdram_cke_o
    sdram.io.sdram_cs_o := io.sdram_cs_o
    sdram.io.sdram_ras_o := io.sdram_ras_o
    sdram.io.sdram_cas_o := io.sdram_cas_o
    sdram.io.sdram_we_o := io.sdram_we_o
    sdram.io.sdram_dqm_o := io.sdram_dqm_o
    sdram.io.sdram_addr_o := io.sdram_addr_o
    sdram.io.sdram_ba_o := io.sdram_ba_o
    io.sdram_data_i := sdram.io.sdram_data_i
    sdram.io.sdram_data_o := io.sdram_data_o
    sdram.io.sdram_drive_o := io.sdram_drive_o
    sdram.io.reset := reset
  }
}