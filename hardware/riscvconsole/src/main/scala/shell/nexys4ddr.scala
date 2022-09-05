package riscvconsole.shell.nexys4ddr

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, IO, attach}
import chipsalliance.rocketchip.config.Parameters
import sifive.fpgashells.ip.xilinx.nexys4ddrmig._


class Nexys4DDRShell(implicit val p :Parameters) extends RawModule {
  // Clock & Reset
  val CLK100MHZ    = IO(Input(Clock()))
  val ck_rst       = IO(Input(Bool()))

  // Green LEDs
  val led          = IO(Vec(16, Analog(1.W)))

  // RGB LEDs, 3 pins each
  val led0_r       = IO(Analog(1.W))
  val led0_g       = IO(Analog(1.W))
  val led0_b       = IO(Analog(1.W))

  val led1_r       = IO(Analog(1.W))
  val led1_g       = IO(Analog(1.W))
  val led1_b       = IO(Analog(1.W))

  // 7 segment display (Mapped to GPIO)
  val cat          = IO(Vec(8, Analog(1.W)))
  val an           = IO(Vec(8, Analog(1.W)))

  // Sliding switches
  val sw           = IO(Vec(16, Analog(1.W)))

  // Buttons. First 2 used as GPIO, 1 as fallback, the last as wakeup
  val btn          = IO(Vec(5, Analog(1.W)))

  // UART0
  val uart_rxd_out = IO(Analog(1.W))
  val uart_txd_in  = IO(Analog(1.W))

  // Jx
  val ja           = IO(Vec(8, Analog(1.W)))
  val jb           = IO(Vec(8, Analog(1.W)))
  val jc           = IO(Vec(8, Analog(1.W)))
  val jd           = IO(Vec(8, Analog(1.W)))

  // DDR
  val ddr = IO(new Nexys4DDRMIGIODDR(0x08000000L))
}