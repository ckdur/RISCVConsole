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
    val left_channel_fifo_is_full = Output(Bool())
    val left_channel_fifo_is_empty = Output(Bool())
    val right_channel_fifo_is_full = Output(Bool())
    val right_channel_fifo_is_empty = Output(Bool())
    val left_channel_data = Output(UInt(AUDIO_DATA_WIDTH.W))
    val right_channel_data = Output(UInt(AUDIO_DATA_WIDTH.W))
  })
  val valid_audio_input = Wire(Bool())

  val left_channel_fifo_is_empty = Wire(Bool())
  val right_channel_fifo_is_empty = Wire(Bool())

  val left_channel_fifo_is_full = Wire(Bool())
  val right_channel_fifo_is_full = Wire(Bool())

  io.left_channel_fifo_is_full := left_channel_fifo_is_full
  io.left_channel_fifo_is_empty := left_channel_fifo_is_empty

  io.right_channel_fifo_is_full := right_channel_fifo_is_full
  io.right_channel_fifo_is_empty := right_channel_fifo_is_empty

  val data_in_shift_reg = RegInit(0.U(AUDIO_DATA_WIDTH.W))
  when(io.bit_clk_rising_edge & valid_audio_input) {
    data_in_shift_reg := Cat(data_in_shift_reg(AUDIO_DATA_WIDTH-2,0), io.serial_audio_in_data)
  }

  val Audio_Out_Bit_Counter = Module(new audio_bit_counter)
  Audio_Out_Bit_Counter.io.bit_clk_rising_edge := io.bit_clk_rising_edge
  Audio_Out_Bit_Counter.io.bit_clk_falling_edge := io.bit_clk_falling_edge
  Audio_Out_Bit_Counter.io.left_right_clk_rising_edge := io.left_right_clk_rising_edge
  Audio_Out_Bit_Counter.io.left_right_clk_falling_edge := io.left_right_clk_falling_edge
  valid_audio_input := Audio_Out_Bit_Counter.io.counting

  val Audio_In_Left_Channel_FIFO = Module(new fifo_core(fifo_core_generic(32, 128)))
  Audio_In_Left_Channel_FIFO.io.wrreq := io.left_right_clk_falling_edge & io.done_channel_sync // & !left_channel_fifo_is_full
  Audio_In_Left_Channel_FIFO.io.wrdata := data_in_shift_reg
  Audio_In_Left_Channel_FIFO.io.rdreq := io.read_left_audio_data_en // & !left_channel_fifo_is_empty
  left_channel_fifo_is_empty := Audio_In_Left_Channel_FIFO.io.empty
  left_channel_fifo_is_full := Audio_In_Left_Channel_FIFO.io.full
  io.left_channel_data := Audio_In_Left_Channel_FIFO.io.rddata

  val Audio_In_Right_Channel_FIFO = Module(new fifo_core(fifo_core_generic(32, 128)))
  Audio_In_Right_Channel_FIFO.io.wrreq := io.left_right_clk_rising_edge & io.done_channel_sync // & !right_channel_fifo_is_full
  Audio_In_Right_Channel_FIFO.io.wrdata := data_in_shift_reg
  Audio_In_Right_Channel_FIFO.io.rdreq := io.read_right_audio_data_en // & !right_channel_fifo_is_empty
  right_channel_fifo_is_empty := Audio_In_Right_Channel_FIFO.io.empty
  right_channel_fifo_is_full := Audio_In_Right_Channel_FIFO.io.full
  io.right_channel_data := Audio_In_Right_Channel_FIFO.io.rddata
}
