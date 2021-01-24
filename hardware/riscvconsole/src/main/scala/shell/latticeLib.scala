package riscvconsole.shell.latticeLib

import chisel3._
import chisel3.experimental._
import sifive.blocks.devices.pinctrl._

class BB extends BlackBox{
  val io = IO(new Bundle{
    val B = Analog(1.W)
    val T = Input(Bool())
    val I = Input(Bool())
    val O = Output(Bool())
  })

  def asInput() : Bool = {
    io.T := false.B
    io.I := false.B
    io.O
  }

  def asOutput(o: Bool) : Unit = {
    io.T := true.B
    io.I := o
  }

  def fromBase(e: BasePin) : Unit = {
    io.T := e.o.oe
    io.I := e.o.oval
    e.i.ival := io.O
  }

  def attachTo(analog: Analog) : Unit = {
    attach(analog, io.B)
  }
}

object BB {
  def apply : BB = {
    Module(new BB)
  }

  def apply(analog: Analog) : Bool = {
    val m = Module(new BB)
    m.attachTo(analog)
    m.asInput()
  }

  def apply(analog: Analog, i: Bool) : Unit = {
    val m = Module(new BB)
    m.attachTo(analog)
    m.asOutput(i)
  }

  def apply(analog: Analog, e: BasePin) : Unit = {
    val m = Module(new BB)
    m.attachTo(analog)
    m.fromBase(e)
  }
}