package riscvconsole.devices.codec

import chisel3._
import chisel3.util._

case class fifo_core_generic
(
  beatBits: Int = 8,
  Size: Int = 8
) {
  val countBits = log2Ceil(beatBits)
}

class fifo_core_port(val conf: fifo_core_generic) extends Bundle {
  val wrdata = Input(UInt(conf.beatBits.W))
  val rdreq = Input(Bool())
  val wrreq = Input(Bool())
  val full = Output(Bool())
  val empty = Output(Bool())
  //val count = Output(UInt(conf.countBits.W))
  val rddata = Output(UInt(conf.beatBits.W))
}

class fifo_core(conf: fifo_core_generic) extends Module {
  val io = IO(new fifo_core_port(conf))

  require(conf.Size > 0)

  val queue = Module(new Queue(UInt(conf.beatBits.W), conf.Size))
  // Is empty is no valids into !io.deq.valid
  io.empty := !queue.io.deq.valid

  // full - enqueues cannot be done anymore
  io.full := !queue.io.enq.ready

  // wrreq will set the valid of the enqueue (write action)
  queue.io.enq.valid := io.wrreq
  queue.io.enq.bits := io.wrdata

  // And rdreg will set the ready of the dequeue (read action)
  queue.io.deq.ready := io.rdreq
  io.rddata := RegEnable(queue.io.deq.bits, queue.io.deq.fire())

  // The count of the elements
  //io.count := queue.io.count
}
