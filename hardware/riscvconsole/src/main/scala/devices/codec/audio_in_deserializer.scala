package riscvconsole.devices.codec

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util._
import codec_param._

class audio_in_deserializer extends Module {
  val io = IO(new Bundle {
    // Inputs
    val bit_clk_rising_edge = Input(Bool())
    val bit_clk_falling_edge = Input(Bool())
    val left_right_clk_rising_edge = Input(Bool())
    val left_right_clk_falling_edge = Input(Bool())
    val done_channel_sync = Input(Bool())
    val serial_audio_in_data = Input(Bool())
    val read_left_audio_data_en = Input(Bool())
    val read_right_audio_data_en = Input(Bool())
    // Bidirectionals
    // Outputs
    val left_audio_fifo_read_space = Output(UInt(8.W))
    val right_audio_fifo_read_space = Output(UInt(8.W))
    val left_channel_data = Output(UInt(AUDIO_DATA_WIDTH.W))
    val right_channel_data = Output(UInt(AUDIO_DATA_WIDTH.W))
  })
  val valid_audio_input = Wire(Bool())

  val left_channel_fifo_is_empty = Wire(Bool())
  val right_channel_fifo_is_empty = Wire(Bool())

  val left_channel_fifo_is_full = Wire(Bool())
  val right_channel_fifo_is_full = Wire(Bool())

  val left_channel_fifo_used = Wire(UInt(7.W))
  val right_channel_fifo_used = Wire(UInt(7.W))

  val left_audio_fifo_read_space = RegInit(0.U(8.W))
  left_audio_fifo_read_space := Cat(left_channel_fifo_is_full, left_channel_fifo_used)
  io.left_audio_fifo_read_space := left_audio_fifo_read_space

  val right_audio_fifo_read_space = RegInit(0.U(8.W))
  right_audio_fifo_read_space := Cat(right_channel_fifo_is_full, right_channel_fifo_used)
  io.right_audio_fifo_read_space := right_audio_fifo_read_space

  val data_in_shift_reg = RegInit(0.U(AUDIO_DATA_WIDTH.W))
  when(io.bit_clk_rising_edge & valid_audio_input) {
    data_in_shift_reg := Cat(data_in_shift_reg(AUDIO_DATA_WIDTH-2,1), io.serial_audio_in_data)
  }

  val Audio_Out_Bit_Counter = Module(new audio_bit_counter)
  Audio_Out_Bit_Counter.io.bit_clk_rising_edge := io.bit_clk_rising_edge
  Audio_Out_Bit_Counter.io.bit_clk_falling_edge := io.bit_clk_falling_edge
  Audio_Out_Bit_Counter.io.left_right_clk_rising_edge := io.left_right_clk_rising_edge
  Audio_Out_Bit_Counter.io.left_right_clk_falling_edge := io.left_right_clk_falling_edge
  valid_audio_input := Audio_Out_Bit_Counter.io.counting

  val Audio_In_Left_Channel_FIFO = Module(new fifo_core(fifo_core_generic(32, 128)))
  Audio_In_Left_Channel_FIFO.io.wrreq := io.left_right_clk_falling_edge & !left_channel_fifo_is_full & io.done_channel_sync
  Audio_In_Left_Channel_FIFO.io.wrdata := data_in_shift_reg
  Audio_In_Left_Channel_FIFO.io.rdreq := io.read_left_audio_data_en & !left_channel_fifo_is_empty
  left_channel_fifo_is_empty := Audio_In_Left_Channel_FIFO.io.empty
  left_channel_fifo_is_full := Audio_In_Left_Channel_FIFO.io.full
  left_channel_fifo_used := Audio_In_Left_Channel_FIFO.io.count
  io.left_channel_data := Audio_In_Left_Channel_FIFO.io.rddata

  val Audio_In_Right_Channel_FIFO = Module(new fifo_core(fifo_core_generic(32, 128)))
  Audio_In_Right_Channel_FIFO.io.wrreq := io.left_right_clk_rising_edge & !right_channel_fifo_is_full & io.done_channel_sync
  Audio_In_Right_Channel_FIFO.io.wrdata := data_in_shift_reg
  Audio_In_Right_Channel_FIFO.io.rdreq := io.read_right_audio_data_en & !right_channel_fifo_is_empty
  right_channel_fifo_is_empty := Audio_In_Right_Channel_FIFO.io.empty
  right_channel_fifo_is_full := Audio_In_Right_Channel_FIFO.io.full
  right_channel_fifo_used := Audio_In_Right_Channel_FIFO.io.count
  io.right_channel_data := Audio_In_Right_Channel_FIFO.io.rddata
}
