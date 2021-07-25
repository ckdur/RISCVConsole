package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.debug._
import riscvconsole.devices.sdram._
import sifive.blocks.devices.gpio.{GPIOPortIO, IOFPortIO}
import sifive.blocks.devices.uart._
import testchipip._

class RVCHarness()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })

  val ldut = LazyModule(new RVCSystem)
  val dut = Module(ldut.module)

  // Simulated memory.
  // Step 1: Our conversion
  p(ExtMem).foreach{ extmem =>
    val memSize = extmem.master.size
    val lineSize = p(CacheBlockBytes)
    val mem = Module(new SimDRAM(memSize, lineSize, p(SystemBusKey).dtsFrequency.getOrElse(100000000L), ldut.mem_axi4.head.params))
    mem.io.axi <> ldut.mem_axi4.head
    mem.io.clock := clock
    mem.io.reset := reset
  }

  // Debug tie off (This also handles the reset system)
  val debug_success = WireInit(false.B)
  Debug.connectDebug(dut.debug, dut.resetctrl, dut.psd, clock, reset.asBool(), debug_success)
  when (debug_success) { io.success := true.B }

  // Serial interface (if existent) will be connected here
  io.success := false.B

  (ldut.serial_tl zip ldut.serdesser).foreach { case (port, serdesser) =>
    val bits = SerialAdapter.asyncQueue(port, clock, reset)
    val ram = SerialAdapter.connectHarnessRAM(serdesser, bits, reset)
    val ser_success = SerialAdapter.connectSimSerial(ram.module.io.tsi_ser, clock, reset)
    when (ser_success) { io.success := true.B }
  }

  // UART
  UARTAdapter.connect(uart = dut.uart.map(_.asInstanceOf[UARTPortIO]), baudrate = BigInt(115200))

  // GPIO tie down
  dut.gpio.foreach{case gpio:GPIOPortIO =>
    gpio.pins.foreach{ case pin =>
      pin.i.ival := false.B
      pin.i.po.foreach(_ := false.B)
    }
  }
  dut.iof.foreach { case iof: IOFPortIO =>
    iof.iof_0.foreach(_.default())
    iof.iof_1.foreach(_.default())
  }

  // SPI
  dut.spi.foreach(_.dq.foreach(_.i := false.B)) // Tie down for now

  // SDRAM
  dut.sdramio.foreach(sdramsim(_, reset.asBool()))
  dut.otherclock := clock
}
