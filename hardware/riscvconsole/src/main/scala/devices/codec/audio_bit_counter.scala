package riscvconsole.devices.codec

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util.{HasBlackBoxResource, RegEnable}
import codec_param._

class audio_bit_counter extends Module {
  val io = IO(new Bundle {
    val bit_clk_rising_edge = Input(Bool())
    val bit_clk_falling_edge = Input(Bool())
    val left_right_clk_rising_edge = Input(Bool())
    val left_right_clk_falling_edge = Input(Bool())

    val counting = Output(Bool())
  })

  val reset_bit_counter = io.left_right_clk_rising_edge | io.left_right_clk_falling_edge
  val bit_counter = RegInit(0.U(5.W))
  val counting = RegInit(false.B)

  when(reset_bit_counter) {
    bit_counter := BIT_COUNTER_INIT.U
  }.elsewhen(io.bit_clk_falling_edge && bit_counter =/= 0.U) {
    bit_counter := bit_counter - 1.U
  }

  when(reset_bit_counter) {
    counting := true.B
  }.elsewhen(io.bit_clk_falling_edge && bit_counter === 0.U) {
    counting := false.B
  }
  io.counting := counting
}