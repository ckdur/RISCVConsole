package riscvconsole.devices.codec

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util._
import codec_param._

class audio_out_serializer extends Module {
  val io = IO(new Bundle {
    // Inputs
    val bit_clk_rising_edge = Input(Bool())
    val bit_clk_falling_edge = Input(Bool())
    val left_right_clk_rising_edge = Input(Bool())
    val left_right_clk_falling_edge = Input(Bool())

    val left_channel_data = Input(UInt(AUDIO_DATA_WIDTH.W))
    val left_channel_data_en = Input(Bool())
    val right_channel_data = Input(UInt(AUDIO_DATA_WIDTH.W))
    val right_channel_data_en = Input(Bool())
    // Bidirectionals
    // Outputs
    val left_channel_fifo_is_full = Output(Bool())
    val left_channel_fifo_is_empty = Output(Bool())
    val right_channel_fifo_is_full = Output(Bool())
    val right_channel_fifo_is_empty = Output(Bool())
    val serial_audio_out_data = Output(Bool())
  })
  val read_left_channel = Wire(Bool())
  val read_right_channel = Wire(Bool())

  val left_channel_fifo_is_empty = Wire(Bool())
  val right_channel_fifo_is_empty = Wire(Bool())

  val left_channel_fifo_is_full = Wire(Bool())
  val right_channel_fifo_is_full = Wire(Bool())

  val left_channel_from_fifo = Wire(UInt(AUDIO_DATA_WIDTH.W))
  val right_channel_from_fifo = Wire(UInt(AUDIO_DATA_WIDTH.W))

  val left_channel_was_read = RegInit(false.B)
  val data_out_shift_reg = RegInit(0.U(AUDIO_DATA_WIDTH.W))

  io.left_channel_fifo_is_full := left_channel_fifo_is_full
  io.left_channel_fifo_is_empty := left_channel_fifo_is_empty

  io.right_channel_fifo_is_full := right_channel_fifo_is_full
  io.right_channel_fifo_is_empty := right_channel_fifo_is_empty

  val serial_audio_out_data = RegInit(false.B)
  serial_audio_out_data := data_out_shift_reg(AUDIO_DATA_WIDTH-1)
  io.serial_audio_out_data := serial_audio_out_data

  when(read_left_channel) {left_channel_was_read := true.B}
    .elsewhen(read_right_channel) {left_channel_was_read := false.B}

  when(read_left_channel) {
    data_out_shift_reg := left_channel_from_fifo
  } .elsewhen(read_right_channel) {
    data_out_shift_reg := right_channel_from_fifo
  } .elsewhen(io.left_right_clk_rising_edge | io.left_right_clk_falling_edge) {
    data_out_shift_reg := 0.U
  } .elsewhen(io.bit_clk_falling_edge) {
    data_out_shift_reg := data_out_shift_reg << 1
  }

  read_left_channel	:= io.left_right_clk_rising_edge &
    !left_channel_fifo_is_empty &
    !right_channel_fifo_is_empty

  read_right_channel := io.left_right_clk_falling_edge &
    left_channel_was_read

  val Audio_Out_Left_Channel_FIFO = Module(new fifo_core(fifo_core_generic(32, 128)))
  Audio_Out_Left_Channel_FIFO.io.wrreq := io.left_channel_data_en// & !left_channel_fifo_is_full
  Audio_Out_Left_Channel_FIFO.io.wrdata := io.left_channel_data
  Audio_Out_Left_Channel_FIFO.io.rdreq := read_left_channel
  left_channel_fifo_is_empty := Audio_Out_Left_Channel_FIFO.io.empty
  left_channel_fifo_is_full := Audio_Out_Left_Channel_FIFO.io.full
  left_channel_from_fifo := Audio_Out_Left_Channel_FIFO.io.rddata

  val Audio_Out_Right_Channel_FIFO = Module(new fifo_core(fifo_core_generic(32, 128)))
  Audio_Out_Right_Channel_FIFO.io.wrreq := io.right_channel_data_en// & !right_channel_fifo_is_full
  Audio_Out_Right_Channel_FIFO.io.wrdata := io.right_channel_data
  Audio_Out_Right_Channel_FIFO.io.rdreq := read_left_channel
  right_channel_fifo_is_empty := Audio_Out_Right_Channel_FIFO.io.empty
  right_channel_fifo_is_full := Audio_Out_Right_Channel_FIFO.io.full
  right_channel_from_fifo := Audio_Out_Right_Channel_FIFO.io.rddata
}