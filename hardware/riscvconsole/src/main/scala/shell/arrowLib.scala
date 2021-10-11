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

class pll extends BlackBox {
  val io = IO(new Bundle {
    val refclk = Input(Clock())
    val rst = Input(Bool())
    val outclk_0 = Output(Clock())
    val outclk_1 = Output(Clock())
    val locked = Output(Bool())
  })
}

class DDR3_PORT extends Bundle {
  val A = Output(Bits(14.W))
  val BA = Output(Bits(3.W))
  val CK_p = Output(Bits(1.W))
  val CK_n = Output(Bits(1.W))
  val CKE = Output(Bits(1.W))
  val CS_n = Output(Bits(1.W))
  val DM = Output(Bits(8.W))
  val RAS_n = Output(Bool())
  val CAS_n = Output(Bool())
  val WE_n = Output(Bool())
  val RESET_n = Output(Bool())
  val DQ = Analog(32.W)
  val DQS_p = Analog(4.W)
  val DQS_n = Analog(4.W)
  val ODT = Output(Bits(1.W))
}