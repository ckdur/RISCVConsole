package riscvconsole.fpga.ulx3s

import chisel3._
import chipyard.harness._
import chipyard.iobinders._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.diplomacy.lazymodule.LazyRawModuleImp
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag
import sifive.fpgashells.shell.IOPin
import sifive.fpgashells.shell.altera._
import testchipip.serdes._

class WithULX3SUARTTSI(uartBaudRate: BigInt = 115200) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    ath.io_uart_bb.bundle <> port.io.uart
    ath.other_leds(1) := port.io.dropped
    ath.other_leds(2) := port.io.tsi2tl_state(0)
    // TODO: Do not exist
    //ath.other_leds(3) := port.io.tsi2tl_state(1)
    //ath.other_leds(4) := port.io.tsi2tl_state(2)
    //ath.other_leds(5) := port.io.tsi2tl_state(3)
  }
})

object WithSPISDCardCounter {
  var cnt = 0
}

class WithULX3SSPIBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SPIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    WithSPISDCardCounter.cnt match {
      case 0 =>
        ath.io_sdspi_bb.foreach(_.bundle <> port.io)
    }
    WithSPISDCardCounter.cnt = WithSPISDCardCounter.cnt + 1
  }
})

class WithULX3SUARTBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    ath.io_uart_bb.bundle <> port.io
  }
})

class WithULX3SGPIOBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPinsPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    ath.io_gpio_bb(port.gpioId).bundle <> port.io
  }
})

class WithULX3SJTAGBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    // port.io.reset.foreach(_ := false.B) // NOTE: Configs with ULX3S should not have external reset for jtag
    port.io.TCK := ath.jtagOverlay.getWrappedValue.TCK
    port.io.TMS := ath.jtagOverlay.getWrappedValue.TMS
    port.io.TDI := ath.jtagOverlay.getWrappedValue.TDI
    ath.jtagOverlay.getWrappedValue.TDO.data := port.io.TDO
    ath.jtagOverlay.getWrappedValue.TDO.driven := true.B
    ath.ndreset.getWrappedValue := /*port.io.ndreset || */!ath.jtagOverlay.getWrappedValue.srst_n
    // ath.jtagOverlay.getWrappedValue.srst_n is ignored
  }
})

class WithULX3SDDRMemBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    val bundles = ath.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

