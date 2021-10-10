package riscvconsole.devices.codec

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util.{HasBlackBoxResource, RegEnable}

class clock_edge extends Module {
  val io = IO(new Bundle {
    val test_clk = new Bidir

    val rising_edge = Output(Bool())
    val falling_edge = Output(Bool())
  })
  io.test_clk.drive := false.B
  io.test_clk.out := false.B

  val test_clk = io.test_clk.in
  val cur_test_clk = RegNext(test_clk)
  val last_test_clk = RegNext(cur_test_clk)

  val found_edge = last_test_clk ^ cur_test_clk
  io.rising_edge := found_edge & cur_test_clk
  io.falling_edge := found_edge & last_test_clk
}