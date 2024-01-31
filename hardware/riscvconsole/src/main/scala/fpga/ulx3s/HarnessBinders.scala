package riscvconsole.fpga.ulx3s

import chisel3._

import freechips.rocketchip.util._
import freechips.rocketchip.diplomacy._
import sifive.blocks.devices.uart._
import sifive.fpgashells.shell._
import chipyard.harness._
import chipyard.iobinders._
import testchipip.serdes._

class WithULX3SUARTTSI extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    val harnessIO = IO(new UARTPortIO(port.io.uartParams)).suggestName("uart_tsi")
    harnessIO <> port.io.uart
    val packagePinsWithPackageIOs = Seq(
      ("A9" , IOPin(harnessIO.rxd)),
      ("D10", IOPin(harnessIO.txd)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      ath.lpf.addPackagePin(io, pin)
      ath.lpf.addIOBUF(io)
    } }

    ath.other_leds(1) := port.io.dropped
    //ath.other_leds(9) := port.io.tsi2tl_state(0)
    //ath.other_leds(10) := port.io.tsi2tl_state(1)
    //ath.other_leds(11) := port.io.tsi2tl_state(2)
    //ath.other_leds(12) := port.io.tsi2tl_state(3)
  }
})


class WithULX3SSDRAMTL extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val artyTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    val bundles = artyTh.sdramClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

// Maps the UART device to the on-board USB-UART
class WithULX3SUART extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    ath.io_uart_bb.bundle <> port.io
  }
})

class WithULX3SJTAG extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    port.io.TCK := ath.jtagOverlay.getWrappedValue.TCK
    port.io.TMS := ath.jtagOverlay.getWrappedValue.TMS
    port.io.TDI := ath.jtagOverlay.getWrappedValue.TDI
    ath.jtagOverlay.getWrappedValue.TDO.data := port.io.TDO
    ath.jtagOverlay.getWrappedValue.TDO.driven := true.B
    ath.ndreset.getWrappedValue := /*port.io.ndreset || */!ath.jtagOverlay.getWrappedValue.srst_n
  }
})

class WithULX3SGPIOBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPinsPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    ath.io_gpio_bb(port.gpioId).bundle <> port.io
  }
})

object SPITracker {
  var i = 0
  def incr = {
    i = i + 1
  }
}

class WithULX3SSDBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SPIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    if(SPITracker.i == 0) {
      ath.io_sd_bb.foreach{ io_sd_bb =>
        io_sd_bb.bundle <> port.io
      }
      SPITracker.incr
    }
  }
})
