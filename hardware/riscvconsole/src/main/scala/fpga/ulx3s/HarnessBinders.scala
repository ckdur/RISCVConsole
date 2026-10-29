package riscvconsole.fpga.ulx3s

import chisel3._
import freechips.rocketchip.util._
import sifive.blocks.devices.uart._
import sifive.fpgashells.shell._
import chipyard.harness._
import chipyard.iobinders._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.nodes._
import riscvconsole.iobinders._
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
    // The DTM and debug module only reset with the board reset, not with ndreset
    port.io.reset.foreach(_ := th.referenceReset.asBool)
    ath.ndreset.getWrappedValue := !ath.jtagOverlay.getWrappedValue.srst_n
  }
})

class WithULX3SDebugNDReset extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: DebugResetPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    ath.debugNDReset.getWrappedValue := port.io.asBool
  }
})

// Same as WithResetFromHarness, but also resets the ChipTop on debug module ndreset
class WithULX3SResetFromHarness extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ResetPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    port.io := (th.referenceReset.asBool || ath.debugNDReset.getWrappedValue).asAsyncReset
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
    if(SPITracker.i < 2) {
      ath.io_sd_bb(SPITracker.i).bundle <> port.io
    }
    SPITracker.incr
  }
})

class WithULX3SADCFIFOBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ADCFIFOPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    val extraio = ath.extraOverlay.head.getWrappedValue
    val extraio_valid = extraio(0)
    val extraio_rst_n = extraio(1)
    val extraio_clk = extraio(2)
    val extraio_io_valid = extraio(3)
    val extraio_io_bits = extraio.slice(4, 4 + port.io.io.bits.getWidth)

    UIntToAnalog(port.io.valid, extraio_valid, true.B)
    UIntToAnalog(port.io.rst_n, extraio_rst_n, true.B)
    UIntToAnalog(port.io.clk.asBool, extraio_clk, true.B)
    port.io.io.valid := AnalogToUInt(extraio_io_valid)
    port.io.io.bits := VecInit(extraio_io_bits.map { a => AnalogToUInt(a).asBool }.reverse).asUInt
  }
})

class WithULX3SROCounterBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: ROCounterPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[ULX3SHarness]
    val roio = ath.roOverlay.head.getWrappedValue
    val n = port.io.vout.getWidth
    UIntToAnalog(port.io.rstb, roio(0), true.B)
    (0 until n).foreach { i => UIntToAnalog(port.io.dut_gate(i), roio(1 + i), true.B) }
    port.io.vout := VecInit((0 until n).map { i => AnalogToUInt(roio(1 + n + i)).asBool }).asUInt
  }
})
