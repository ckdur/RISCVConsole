package riscvconsole.shell.ArrowLib

import chisel3._
import chisel3.experimental._
import sifive.blocks.devices.pinctrl._

class ALT_IOBUF extends BlackBox{
  val io = IO(new Bundle{
    val io = Analog(1.W)
    val oe = Input(Bool())
    val i = Input(Bool())
    val o = Output(Bool())
  })

  def asInput() : Bool = {
    io.oe := false.B
    io.i := false.B
    io.o
  }

  def asOutput(o: Bool) : Unit = {
    io.oe := true.B
    io.i := o
  }

  def fromBase(e: BasePin) : Unit = {
    io.oe := e.o.oe
    io.i := e.o.oval
    e.i.ival := io.o
  }

  def attachTo(analog: Analog) : Unit = {
    attach(analog, io.io)
  }
}

object ALT_IOBUF {
  def apply : ALT_IOBUF = {
    Module(new ALT_IOBUF)
  }

  def apply(analog: Analog) : Bool = {
    val m = Module(new ALT_IOBUF)
    m.attachTo(analog)
    m.asInput()
  }

  def apply(analog: Analog, i: Bool) : Unit = {
    val m = Module(new ALT_IOBUF)
    m.attachTo(analog)
    m.asOutput(i)
  }

  def apply(analog: Analog, e: BasePin) : Unit = {
    val m = Module(new ALT_IOBUF)
    m.attachTo(analog)
    m.fromBase(e)
  }
}

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