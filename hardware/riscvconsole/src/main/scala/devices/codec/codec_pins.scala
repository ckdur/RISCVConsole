package riscvconsole.devices.codec

import chisel3._
import chisel3.util._
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.blocks.devices.pinctrl.Pin

class CodecSignals[T <: Data](private val pingen: () => T) extends Bundle {
  val AUD_BCLK: T = pingen()
  val AUD_ADCLRCK: T = pingen()
  val AUD_DACLRCK: T = pingen()
  val AUD_ADCDAT = Input(Bool())
  val AUD_DACDAT = Output(Bool())
}

class CodecPins[T <: Pin](pingen: () => T) extends CodecSignals[T](pingen)

object CodecPinsFromPort {

  def apply[T <: Pin](pins: CodecSignals[T], codec: CodecIO, clock: Clock, reset: Bool, syncStages: Int = 0) = {
    withClockAndReset(clock, reset) {
      pins.AUD_BCLK.outputPin(codec.AUD_BCLK.out, pue=false.B, ie = true.B)
      pins.AUD_BCLK.o.oe := codec.AUD_BCLK.drive
      codec.AUD_BCLK.in := SyncResetSynchronizerShiftReg(pins.AUD_BCLK.i.ival, syncStages, init = true.B,
        name = Some("codec_AUD_BCLK"))

      pins.AUD_ADCLRCK.outputPin(codec.AUD_ADCLRCK.out, pue=false.B, ie = true.B)
      pins.AUD_ADCLRCK.o.oe := codec.AUD_ADCLRCK.drive
      codec.AUD_ADCLRCK.in := SyncResetSynchronizerShiftReg(pins.AUD_ADCLRCK.i.ival, syncStages, init = true.B,
        name = Some("codec_AUD_ADCLRCK"))

      pins.AUD_DACLRCK.outputPin(codec.AUD_DACLRCK.out, pue=false.B, ie = true.B)
      pins.AUD_DACLRCK.o.oe := codec.AUD_DACLRCK.drive
      codec.AUD_DACLRCK.in := SyncResetSynchronizerShiftReg(pins.AUD_DACLRCK.i.ival, syncStages, init = true.B,
        name = Some("codec_AUD_DACLRCK"))

      pins.AUD_DACDAT := codec.AUD_DACDAT
      codec.AUD_ADCDAT := pins.AUD_ADCDAT
    }
  }
}
