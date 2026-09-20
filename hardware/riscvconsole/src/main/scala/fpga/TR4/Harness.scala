package riscvconsole.fpga.tr4

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters, ClockGroup, ClockSinkNode, ResetWrangler}
import freechips.rocketchip.subsystem.SystemBusKey
import sifive.fpgashells.shell.altera._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks.{PLLFactory, PLLFactoryKey}
import sifive.fpgashells.ip.altera._
import sifive.blocks.devices.uart._
import chipyard._
import chipyard.harness._
import chipyard.iobinders.HasIOBinders
import freechips.rocketchip.diplomacy.IdRange
import sifive.blocks.devices.gpio.{GPIOPortIO, PeripheryGPIOKey}
import sifive.blocks.devices.spi.{PeripherySPIFlashKey, PeripherySPIKey, SPIParamsBase, SPIPortIO}
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.diplomacy.bundlebridge._

import scala.collection.mutable.LinkedHashMap

case object GPIOPinSeq extends Field[Seq[(Int, Int)]](Seq(1 -> 20, 1 -> 21, 1 -> 18, 1 -> 19))

class TR4Harness(override implicit val p: Parameters) extends TR4Shell {
  val uartseq   = Seq(1 -> 22, 1 -> 23)
  val uart      = Overlay(UARTOverlayKey, new UARTTR4ShellPlacer(this, TR4GPIOGroup(uartseq), UARTShellInput()))
  val jtagseq   = Seq(1 ->  0, 1 ->  1, 1 ->  2, 1 ->  3, 1 -> 24)
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugTR4ShellPlacer(this, TR4GPIOGroup(jtagseq), JTAGDebugShellInput()))
  val qspiseq   = Seq(1 -> 20, 1 -> 21, 1 -> 18, 1 -> 19)
  val qspi      = Overlay(SPIFlashOverlayKey, new SPIFlashTR4ShellPlacer(this, TR4GPIOGroup(qspiseq), SPIFlashShellInput())(ValName(s"qspi")))
  val gpioseq   = dp(GPIOPinSeq)
  val gpio      = Overlay(GPIOOverlayKey, new GPIOPeripheralTR4ShellPlacer(this, TR4GPIOGroup(gpioseq), GPIOShellInput()))

  def dp = designParameters

  val clockOverlay = dp(ClockInputOverlayKey).map(_.place(ClockInputDesignInput()))
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"TR4 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
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

  val ddrClockNode = ClockSinkNode(freqMHz = 50.0)
  ddrClockNode := clockOverlay(1).overlayOutput.node
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

  override lazy val module = new TR4HarnessImpl(this)
}

class TR4HarnessImpl(_outer: TR4Harness) extends TR4ShellImpl(_outer) with HasHarnessInstantiators {
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
  _outer.harnessSysPLL.plls.foreach(_._1.asInstanceOf[QsysALTPLL].tieoffextra)

  // Add the clocks to the addGroupOnlyNames, which is just addGroup for Altera
  val topclocks = _outer.clockOverlay.map(_.asInstanceOf[SysClockTR4PlacedOverlay].io)
  topclocks.foreach { topclock =>
    _outer.sdc.addGroupOnlyNames(Seq(IOPin(topclock).name))
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

  // TODO: This definitelly should be inside of the DDR overlay, but no known way to get the IOPin(ddrclkOverlay)
  val ddrclkOverlay: Clock = topclocks(1)
  /*_outer.sdc.addGeneratedClock(s"memif|island|blackbox|mem|pll0|pll_afi_clk",
    IOPin(ddrclkOverlay), "memif|island|blackbox|mem|pll0|upll_memphy|auto_generated|pll1|clk[0]", Rational(3, 1))
  _outer.sdc.addGeneratedClock(s"memif|island|blackbox|mem|pll0|pll_mem_clk",
    IOPin(ddrclkOverlay), "memif|island|blackbox|mem|pll0|upll_memphy|auto_generated|pll1|clk[1]", Rational(6, 1))
  _outer.sdc.addGeneratedClock(s"memif|island|blackbox|mem|pll0|pll_write_clk",
    IOPin(ddrclkOverlay), "memif|island|blackbox|mem|pll0|upll_memphy|auto_generated|pll1|clk[3]", Rational(3, 1))
  _outer.sdc.addGeneratedClock(s"memif|island|blackbox|mem|pll0|pll_addr_cmd_clk",
    IOPin(ddrclkOverlay), "memif|island|blackbox|mem|pll0|upll_memphy|auto_generated|pll1|clk[2]", Rational(6, 1))
  _outer.sdc.addGeneratedClock(s"memif|island|blackbox|mem|pll0|pll_avl_clk",
    IOPin(ddrclkOverlay), "memif|island|blackbox|mem|pll0|upll_memphy|auto_generated|pll1|clk[5]", Rational(3, 2))
  _outer.sdc.addGeneratedClock(s"memif|island|blackbox|mem|pll0|pll_config_clk",
    IOPin(ddrclkOverlay), "memif|island|blackbox|mem|pll0|upll_memphy|auto_generated|pll1|clk[6]", Rational(1, 2))*/

  /*val powerOnReset: Bool = PowerOnResetAlteraFPGAOnly(sysclk, sysrst)
  //_outer.sdc.addAsyncPath(Seq(powerOnReset))
  _outer.pllReset := _outer.resetPin || powerOnReset*/

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = _outer.dutClock.in.head._1.reset
  def success = { require(false, "Unused"); false.B}
  def referenceClocks = {
    val m: LinkedHashMap[String, (Double, Clock)] = LinkedHashMap.empty
    m("dut_clock") = (_outer.dutFreqMHz * (1000 * 1000), referenceClock)
    _outer.extClocks.zipWithIndex.foreach{ case((freqMHz, cnode), i) =>
      m(s"ext_clock_$i") = (freqMHz * (1000 * 1000), cnode.in.head._1.clock)
    }
    m
  }

  // Necessary to connect any child clock
  childClock := referenceClock
  childReset := referenceReset
  // MAKE SURE the clock and reset of the DDROverlay is connected
  _outer.ddrOverlays.foreach(_.asInstanceOf[DDRTR4PlacedOverlay].memif.module.clock := harnessBinderClock)
  _outer.ddrOverlays.foreach(_.asInstanceOf[DDRTR4PlacedOverlay].memif.module.reset := harnessBinderReset)

  instantiateChipTops()
}
