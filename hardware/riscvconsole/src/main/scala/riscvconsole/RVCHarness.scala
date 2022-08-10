package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.util.PlusArg
import riscvconsole.devices.codec.CodecIO
import riscvconsole.devices.sdram._
import sifive.blocks.devices.gpio.{GPIOPortIO, IOFPortIO}
import sifive.blocks.devices.i2c.I2CPort
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
  dut.jtag.foreach { jtag =>
    val debug_success = WireInit(false.B)
    val simjtag = Module(new SimJTAG(tickDelay=3))
    jtag.TRSTn.foreach(_ := !reset.asBool) // NOTE: Normal reset
    jtag.TMS := simjtag.io.jtag.TMS
    jtag.TDI := simjtag.io.jtag.TDI
    jtag.TCK := simjtag.io.jtag.TCK
    simjtag.io.jtag.TDO.data := jtag.TDO.data
    simjtag.io.jtag.TDO.driven := jtag.TDO.driven

    simjtag.io.clock := clock
    simjtag.io.reset := reset
    simjtag.io.enable := PlusArg("jtag_rbb_enable", 0, "Enable SimJTAG for JTAG Connections. Simulation will pause until connection is made.")
    simjtag.io.init_done := !reset.asBool
    when (simjtag.io.exit === 1.U) { io.success := true.B }
    when (simjtag.io.exit >= 2.U) {
      printf("*** FAILED *** (exit code = %d)\n", simjtag.io.exit >> 1.U)
      assert(false.B)
    }
    // Equivalent of simjtag.connect
  }


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
  dut.gpio.foreach{ case gpio:GPIOPortIO =>
    gpio.pins.foreach{ case pin =>
      pin.i.ival := false.B
      pin.i.po.foreach(_ := false.B)
    }
  }
  dut.iof.foreach { case iof: Option[IOFPortIO] =>
    iof.foreach(_.iof_0.foreach(_.default()))
    iof.foreach(_.iof_1.foreach(_.default()))
  }

  // SPI
  dut.spi.foreach(_.dq.foreach(_.i := false.B)) // Tie down for now

  // SDRAM
  dut.sdramio.foreach(sdramsim(_, reset.asBool()))
  dut.otherclock := clock

  // I2C
  dut.i2c.foreach{ case i2c:I2CPort =>
    i2c.sda.in := false.B
    i2c.scl.in := false.B
  }

  // CODEC
  dut.codec.foreach{ case codec:CodecIO =>
    codec.AUD_DACLRCK.in := false.B
    codec.AUD_ADCLRCK.in := false.B
    codec.AUD_BCLK.in := false.B
    codec.AUD_ADCDAT := false.B
  }
}
