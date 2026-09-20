package riscvconsole.fpga.ulx3s

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockGroup, ClockSinkNode, ResetWrangler}
import freechips.rocketchip.subsystem.SystemBusKey
import sifive.fpgashells.shell.altera._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks.{PLLFactory, PLLFactoryKey}
import sifive.fpgashells.ip.lattice._
import sifive.fpgashells.shell.lattice._
import chipyard._
import chipyard.harness._
import chipyard.iobinders.HasIOBinders
import freechips.rocketchip.diplomacy.IdRange
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.bundlebridge._

class ULX3SHarness(override implicit val p: Parameters) extends ULX3SShell {
  val uart      = Overlay(UARTOverlayKey, new UARTULX3SShellPlacer(this, UARTShellInput()))
  val jtagseq   = Seq(1 ->  0, 1 ->  1, 1 ->  2, 1 ->  3, 1 -> 24)
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugULX3SShellPlacer(this, ULX3SGPIOGroup(jtagseq), JTAGDebugShellInput()))
  val gpioseq   = Seq(1 ->  0, 1 ->  1, 1 ->  2, 1 ->  3, 1 -> 24)
  val gpio      = Overlay(GPIOOverlayKey, new GPIOPeripheralULX3SShellPlacer(this, ULX3SGPIOGroup(gpioseq), GPIOShellInput()))

  def dp = designParameters

  val clockOverlay = dp(ClockInputOverlayKey).map(_.place(ClockInputDesignInput()))
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"ULX3S FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler())
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLLNode

  harnessSysPLLNode := clockOverlay.head.overlayOutput.node

  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).headOption.getOrElse(UARTParams(0))))
  val uartOverlay = dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  val ledOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val all_leds = ledOverlays.map(_.overlayOutput.led)
  println(s"All leds: ${all_leds.length}, ${all_leds}")
  val status_leds = all_leds.take(1)
  println(s"Status leds: ${status_leds.length}, ${status_leds}")
  val other_leds = all_leds.drop(1)
  println(s"Other leds: ${other_leds.length}, ${other_leds}")

  val jtagOverlay = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput()).overlayOutput.jtag

  val io_qspi_bb = dp(PeripherySPIFlashKey).map { spicfg => BundleBridgeSource(() => new SPIPortIO(spicfg)) }
  ((dp(SPIFlashOverlayKey) zip io_qspi_bb) zip dp(PeripherySPIFlashKey)).map {
    case ((qspi, qspibb), spicfg) =>
      qspi.place(SPIFlashDesignInput(qspibb))
  }

  val io_gpio_bb = dp(PeripheryGPIOKey).map { p => BundleBridgeSource(() => new GPIOPortIO(p)) }
  ((dp(GPIOOverlayKey) zip io_gpio_bb) zip dp(PeripheryGPIOKey)).map { case ((placer, gpiobb), gpiocfg) =>
    placer.place(GPIODesignInput(gpiocfg, gpiobb))
  }

  val ddrOverlays = dp(ExtTLMem).map {case mem => dp(DDROverlayKey).head.place(DDRDesignInput(mem.master.base, dutWrangler.node, harnessSysPLLNode, true))}
  val ddrNode = ddrOverlays.map(_.overlayOutput.ddr)
  val ddrClient = dp(ExtTLMem).map { case mem =>
    TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
      name = "chip_ddr",
      sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
    )))))
  }

  // Clock for a possible serial interface
  val extClocks: Seq[(Double, ClockSinkNode)] = dp(testchipip.serdes.SerialTLKey).flatMap { f =>
    val tlSerialClockFreq = f.phyParams match {
      case params: testchipip.serdes.DecoupledInternalSyncSerialPhyParams => Some(params.freqMHz.toDouble)
      case params: testchipip.serdes.DecoupledExternalSyncSerialPhyParams => None
      case params: testchipip.serdes.CreditedSourceSyncSerialPhyParams => Some(params.freqMHz.toDouble)
    }

    tlSerialClockFreq.map { freqMHz =>
      val thisClock = ClockSinkNode(freqMHz = freqMHz)
      val thisGroup = ClockGroup()
      thisClock := thisGroup := harnessSysPLLNode
      (freqMHz, thisClock)
    }
  }.toSeq

  (ddrNode zip ddrClient).map{case (a, b) => a := b}

  override lazy val module = new ULX3SHarnessImpl(this)
}

class ULX3SHarnessImpl(_outer: ULX3SHarness) extends ULX3SShellImpl(_outer) with HasHarnessInstantiators {
  val _harnessOuter = _outer
  _outer.all_leds.foreach(_ := DontCare)
  _outer.clockOverlay.head.overlayOutput.node.out(0)._1.reset := _outer.resetPin

  val sysclk = _outer.clockOverlay.head.overlayOutput.node.out.head._1.clock
  val sysrst = _outer.resetPin
  val dutclk = _outer.dutClock.in.head._1.clock
  val dutrst = _outer.dutClock.in.head._1.reset

  // Blink the status LEDs for sanity
  withClockAndReset(dutclk, dutrst) {
    val period = (BigInt(100) << 20) / _outer.status_leds.size
    val counter = RegInit(0.U(log2Ceil(period).W))
    val on = RegInit(1.U(_outer.status_leds.size.W))
    counter := Mux(counter === (period - 1).U, 0.U, counter + 1.U)
    when(counter === 0.U) {
      if(_outer.status_leds.size == 1)
        on := ~on
      else
        on := on(_outer.status_leds.size - 2, 0) ## on(_outer.status_leds.size - 1)
    }
    _outer.status_leds.zipWithIndex.foreach { case (o, s) => o := on(s) }
  }

  _outer.other_leds(0) := ~_outer.resetPin

  _outer.harnessSysPLL.plls.foreach(_._1.getReset.get := _outer.pllReset)

  // Add the clocks to the addGroupOnlyNames, which is just addGroup for Altera
  val topclocks = _outer.clockOverlay.map(_.asInstanceOf[SysClockULX3SPlacedOverlay].io)
  topclocks.foreach { topclock =>
    _outer.sdc.addGroup(Seq(IOPin(topclock).name))
  }

  // Put the generation of clocks
  // TODO: Maybe this should be in a Overlay?
  _outer.harnessSysPLL.plls.foreach{ case(pll, _) =>
    //val qsyspll = pll.asInstanceOf[QsysALTPLL]
    val sysOverlay: Clock = topclocks.head
    /*qsyspll.getClockNames.zipWithIndex.foreach{ case(path, i) =>
      _outer.sdc.addGeneratedClock(s"dut_clock_${i}", IOPin(sysOverlay), path, qsyspll.c.ratios(i))
      _outer.sdc.addGroupOnlyNames(Seq(s"dut_clock_${i}"))
    }*/
  }

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = _outer.dutClock.in.head._1.reset
  def success = { require(false, "Unused"); false.B}

  // Necessary to connect any child clock
  childClock := referenceClock
  childReset := referenceReset
  instantiateChipTops()
}
