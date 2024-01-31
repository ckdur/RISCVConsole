package riscvconsole.fpga.ulx3s

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tilelink._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.SystemBusKey
import sifive.fpgashells.shell.lattice._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._
import sifive.blocks.devices.uart._
import chipyard._
import chipyard.harness._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.fpgashells.ip.lattice.ecp5pllCompat

class ULX3SHarness(override implicit val p: Parameters) extends ULX3SShell {
  def dp = designParameters

  val clockOverlay = dp(ClockInputOverlayKey).map(_.place(ClockInputDesignInput())).head
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"ULX3S FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler())
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLLNode

  harnessSysPLLNode := clockOverlay.overlayOutput.node

  val sdramOverlay = dp(SDRAMOverlayKey).head.place(SDRAMDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLLNode)).asInstanceOf[SDRAMULX3SPlacedOverlay]
  val sdramClient = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_sdram",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))
  val sdramBlockDuringReset = LazyModule(new TLBlockDuringReset(4))
  sdramOverlay.overlayOutput.sdram := sdramBlockDuringReset.node := sdramClient

  val ledOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val all_leds = ledOverlays.map(_.overlayOutput.led)
  val status_leds = all_leds.take(3)
  val other_leds = all_leds.drop(3)

  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).headOption.getOrElse(UARTParams(0))))
  val uartOverlay = dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  val jtagOverlay = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput()).overlayOutput.jtag

  val io_gpio_bb = dp(PeripheryGPIOKey).map { p => BundleBridgeSource(() => new GPIOPortIO(p)) }
  ((dp(GPIOOverlayKey) zip io_gpio_bb) zip dp(PeripheryGPIOKey)).map { case ((placer, gpiobb), gpiocfg) =>
    placer.place(GPIODesignInput(gpiocfg, gpiobb))
  }

  val io_sd_bb = if(dp(PeripherySPIKey).nonEmpty) {
    val spicfg = dp(PeripherySPIKey).head
    val spibb = BundleBridgeSource(() => new SPIPortIO(spicfg))
    val spi = dp(SDOverlayKey).head
    spi.place(SPIDesignInput(spicfg, spibb))
    Some(spibb)
  } else None

  override lazy val module = new HarnessLikeImpl

  class HarnessLikeImpl extends ULX3SShellImpl(this) with HasHarnessInstantiators {
    override def provideImplicitClockToLazyChildren = true

    all_leds.foreach(_ := DontCare)
    clockOverlay.overlayOutput.node.out(0)._1.reset := ~resetPin

    val clk = clockOverlay.overlayOutput.node.out.head._1.clock

    // Blink the status LEDs for sanity
    withClockAndReset(clk, dutClock.in.head._1.reset) {
      val period = (BigInt(100) << 20) / status_leds.size
      val counter = RegInit(0.U(log2Ceil(period).W))
      val on = RegInit(0.U(log2Ceil(status_leds.size).W))
      status_leds.zipWithIndex.map { case (o,s) => o := on === s.U }
      counter := Mux(counter === (period-1).U, 0.U, counter + 1.U)
      when (counter === 0.U) {
        on := Mux(on === (status_leds.size-1).U, 0.U, on + 1.U)
      }
    }

    other_leds(0) := resetPin

    harnessSysPLL.plls.foreach(_._1.getReset.get := pllReset)
    harnessSysPLL.plls.foreach(_._1.asInstanceOf[ecp5pllCompat].tieoffextra)

    def referenceClockFreqMHz = dutFreqMHz
    def referenceClock = dutClock.in.head._1.clock
    def referenceReset = dutClock.in.head._1.reset
    def success = { require(false, "Unused"); false.B }

    childClock := harnessBinderClock
    childReset := harnessBinderReset

    sdramOverlay.mig.module.clock := harnessBinderClock
    sdramOverlay.mig.module.reset := harnessBinderReset
    sdramBlockDuringReset.module.clock := harnessBinderClock
    sdramBlockDuringReset.module.reset := harnessBinderReset.asBool // || !sdramOverlay.mig.module.io.port.init_calib_complete

    // other_leds(6) := ddrOverlay.mig.module.io.port.init_calib_complete

    withClockAndReset(harnessBinderClock, harnessBinderReset) {
      instantiateChipTops()
    }
  }
}
