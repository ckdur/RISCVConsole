package riscvconsole.devices.codec

import chisel3._
import chisel3.experimental.{Analog, IntParam, StringParam, attach}
import chisel3.util.{HasBlackBoxResource, RegEnable}

object codec_param {
  val AUDIO_DATA_WIDTH = 32
  val BIT_COUNTER_INIT = 31
}

class Bidir extends Bundle {
  val out = Output(Bool())
  val in = Input(Bool())
  val drive = Output(Bool())
}

import codec_param._
class codec extends Module {
  val io = IO(new Bundle {
    val clear_audio_in_memory = Input(Bool())
    val read_audio_in = Input(Bool())
    val left_channel_audio_in = Output(UInt(AUDIO_DATA_WIDTH.W))
    val right_channel_audio_in = Output(UInt(AUDIO_DATA_WIDTH.W))
    val audio_in_available = Output(Bool())

    val clear_audio_out_memory = Input(Bool())
    val write_audio_out = Input(Bool())
    val left_channel_audio_out = Input(UInt(AUDIO_DATA_WIDTH.W))
    val right_channel_audio_out = Input(UInt(AUDIO_DATA_WIDTH.W))
    val audio_out_allowed = Output(Bool())

    val AUD_BCLK = new Bidir
    val AUD_ADCLRCK = new Bidir
    val AUD_DACLRCK = new Bidir
    val AUD_ADCDAT = Input(Bool())
    val AUD_DACDAT = Output(Bool())
  })
  // Internal Wires
  val	bclk_rising_edge = Wire(Bool())
  val	bclk_falling_edge = Wire(Bool())

  val	adc_lrclk_rising_edge = Wire(Bool())
  val	adc_lrclk_falling_edge = Wire(Bool())

  val	dac_lrclk_rising_edge = Wire(Bool())
  val	dac_lrclk_falling_edge = Wire(Bool())

  // Internal Registers
  val	done_adc_channel_sync = RegInit(false.B)
  when(adc_lrclk_rising_edge) { done_adc_channel_sync := true.B }

  val	done_dac_channel_sync = RegInit(false.B)
  when(dac_lrclk_falling_edge) { done_dac_channel_sync := true.B }

  val Bit_Clock_Edges = Module(new clock_edge)
  Bit_Clock_Edges.io.test_clk <> io.AUD_BCLK
  bclk_rising_edge := Bit_Clock_Edges.io.rising_edge
  bclk_falling_edge := Bit_Clock_Edges.io.falling_edge

  val ADC_Left_Right_Clock_Edges = Module(new clock_edge)
  ADC_Left_Right_Clock_Edges.io.test_clk <> io.AUD_ADCLRCK
  adc_lrclk_rising_edge := ADC_Left_Right_Clock_Edges.io.rising_edge
  adc_lrclk_falling_edge := ADC_Left_Right_Clock_Edges.io.falling_edge

  val DAC_Left_Right_Clock_Edges = Module(new clock_edge)
  DAC_Left_Right_Clock_Edges.io.test_clk <> io.AUD_DACLRCK
  dac_lrclk_rising_edge := DAC_Left_Right_Clock_Edges.io.rising_edge
  dac_lrclk_falling_edge := DAC_Left_Right_Clock_Edges.io.falling_edge

  val Audio_In_Deserializer = withReset(reset.asBool || io.clear_audio_in_memory) { Module(new audio_in_deserializer) }
  Audio_In_Deserializer.io.bit_clk_rising_edge := bclk_rising_edge
  Audio_In_Deserializer.io.bit_clk_falling_edge := bclk_falling_edge
  Audio_In_Deserializer.io.left_right_clk_rising_edge := adc_lrclk_rising_edge
  Audio_In_Deserializer.io.left_right_clk_falling_edge := adc_lrclk_falling_edge

  Audio_In_Deserializer.io.done_channel_sync := done_adc_channel_sync

  Audio_In_Deserializer.io.serial_audio_in_data := io.AUD_ADCDAT

  Audio_In_Deserializer.io.read_left_audio_data_en := io.read_audio_in// & audio_in_available
  Audio_In_Deserializer.io.read_right_audio_data_en := io.read_audio_in// & audio_in_available

  io.audio_in_available := !Audio_In_Deserializer.io.left_channel_fifo_is_empty &&
    !Audio_In_Deserializer.io.right_channel_fifo_is_empty

  io.left_channel_audio_in := Audio_In_Deserializer.io.left_channel_data
  io.right_channel_audio_in := Audio_In_Deserializer.io.right_channel_data

  val Audio_Out_Serializer = withReset(reset.asBool || io.clear_audio_out_memory) { Module(new audio_out_serializer) }
  Audio_Out_Serializer.io.bit_clk_rising_edge := bclk_rising_edge
  Audio_Out_Serializer.io.bit_clk_falling_edge := bclk_falling_edge
  Audio_Out_Serializer.io.left_right_clk_rising_edge := done_dac_channel_sync & dac_lrclk_rising_edge
  Audio_Out_Serializer.io.left_right_clk_falling_edge := done_dac_channel_sync & dac_lrclk_falling_edge

  Audio_Out_Serializer.io.left_channel_data := io.left_channel_audio_out
  Audio_Out_Serializer.io.left_channel_data_en := io.write_audio_out// & audio_out_allowed

  Audio_Out_Serializer.io.right_channel_data := io.right_channel_audio_out
  Audio_Out_Serializer.io.right_channel_data_en := io.write_audio_out// & audio_out_allowed

  io.audio_out_allowed := !Audio_Out_Serializer.io.left_channel_fifo_is_full &&
    !Audio_Out_Serializer.io.right_channel_fifo_is_full

  io.AUD_DACDAT := Audio_Out_Serializer.io.serial_audio_out_data
}