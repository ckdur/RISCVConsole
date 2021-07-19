package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.debug._
import sifive.blocks.devices.gpio.GPIOPortIO
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
    val mem = Module(new SimDRAM(memSize, lineSize, ldut.mem_axi4.head.params))
    mem.io.axi <> ldut.mem_axi4.head
    mem.io.clock := clock
    mem.io.reset := reset
  }

  // Debug tie off (This also handles the reset system)
  Debug.tieoffDebug(dut.debug, dut.resetctrl, Some(dut.psd))
  dut.debug.foreach { d =>
    d.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare; cdmi.dmiClock := clock })
    d.dmactiveAck := DontCare
    d.clock := clock
  }

  // Serial interface (if existent) will be connected here
  io.success := false.B
  val ser_success = SerialAdapter.connectSimSerial(dut.serial, clock, reset)
  when (ser_success) { io.success := true.B }

  // UART
  UARTAdapter.connect(uart = dut.uart.map(_.asInstanceOf[UARTPortIO]), baudrate = BigInt(115200))

  // GPIO tie down
  dut.gpio.foreach{case gpio:GPIOPortIO =>
    gpio.pins.foreach{ case pin =>
      pin.i.ival := false.B
    }
    gpio.iof_0.foreach{ case iof =>
      iof.foreach{ case u =>
        u.default()
      }
    }
    gpio.iof_1.foreach{ case iof =>
      iof.foreach{ case u =>
        u.default()
      }
    }
  }

}
