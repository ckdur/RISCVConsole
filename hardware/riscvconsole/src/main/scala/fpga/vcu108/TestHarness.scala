package riscvconsole.fpga.vcu108

import chisel3._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{ControlBusKey, FrontBusKey, SystemBusKey}
import freechips.rocketchip.prci._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIPortIO}
import chipyard._
import chipyard.harness._
import chipyard.iobinders.HasChipyardPorts
import chipyard.stage.phases.TargetDirKey
import freechips.rocketchip.interrupts._
import sifive.fpgashells.ip.xilinx.xdma.XDMAParams
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.{DontTouch, ResetCatchAndSync}
import sifive.fpgashells.shell._

import scala.collection.mutable.LinkedHashMap

class VCU108FPGATestHarness(override implicit val p: Parameters) extends VCU108ShellBasicOverlays with WithVisibleDigitalTopHarness {

  def dp = designParameters

  println(s"VCU108ShellPMOD = ${dp(VCU108ShellPMOD)}")
  println(s"VCU108ShellPMOD2 = ${dp(VCU108ShellPMOD2)}")
  val pmod_is_sdio  = dp(VCU108ShellPMOD) == "SDIO"
  val jtag_location = Some(if (pmod_is_sdio) "PMOD_J53" else "PMOD_J52")

  // Order matters; ddr depends on sys_clock
  val uart      = Overlay(UARTOverlayKey, new UARTVCU108ShellPlacer(this, UARTShellInput()))
  val sdio      = if (pmod_is_sdio) Some(Overlay(SPIOverlayKey, new SDIOVCU108ShellPlacer(this, SPIShellInput()))) else None
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugVCU108ShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanVCU108ShellPlacer(this, JTAGDebugBScanShellInput()))
  val edge      = Overlay(PCIeOverlayKey, new PCIeVCU108EdgeShellPlacer(this, PCIeShellInput()))
  val sys_clock2 = Overlay(ClockInputOverlayKey, new SysClock2VCU108ShellPlacer(this, ClockInputShellInput()))
  val ddr2       = Overlay(DDROverlayKey, new DDR2VCU108ShellPlacer(this, DDRShellInput()))

  println(s"sdio = ${sdio.toString}")
// DOC include start: ClockOverlay
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/
  val ndreset = InModuleBody { WireInit(false.B) }

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"VCU108 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
// DOC include end: ClockOverlay

  // Make available another clock if the external interface exists
  val extClocks: Seq[(Double, ClockSinkNode)] = dp(testchipip.serdes.SerialTLKey).flatMap { f =>
    val tlSerialClockFreq = f.phyParams match {
      case params: testchipip.serdes.DecoupledInternalSyncSerialPhyParams => Some(params.freqMHz.toDouble)
      case params: testchipip.serdes.DecoupledExternalSyncSerialPhyParams => None
      case params: testchipip.serdes.CreditedSourceSyncSerialPhyParams => Some(params.freqMHz.toDouble)
    }
    tlSerialClockFreq.map { freqMHz =>
      val thisClock = ClockSinkNode(freqMHz = freqMHz)
      val thisGroup = ClockGroup()
      thisClock := thisGroup := harnessSysPLL
      (freqMHz, thisClock)
    }
  }.toSeq

  /*** UART ***/

// DOC include start: UartOverlay
  // 1st UART goes to the VCU108 dedicated UART

  val io_uart_bb = dp(PeripheryUARTKey).headOption.map { key =>
    val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(key)))
    dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
    io_uart_bb
  }

// DOC include end: UartOverlay

  /*** SPI ***/

  // 1st SPI goes to the VCU108 SDIO port

  val io_spi_bb = dp(PeripherySPIKey).headOption.map { key =>
    val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(key)))
    dp(SPIOverlayKey).head.place(SPIDesignInput(key, io_spi_bb))
    io_spi_bb
  }

  /*** DDR ***/

  val ddrNode = dp(ExtTLMem).map {case mem => dp(DDROverlayKey).head.place(DDRDesignInput(mem.master.base, dutWrangler.node, harnessSysPLL)).overlayOutput.ddr}

  // connect 1 mem. channel to the FPGA DDR
  val ddrClient = dp(ExtTLMem).map { case mem =>
    TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
      name = "chip_ddr",
      sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
    )))))
  }
  (ddrNode zip ddrClient).map{case (a, b) => a := TLWidthWidget(dp(ExtTLMem).get.master.beatBytes) := b}

  /*** JTAG ***/
  val jtagPlacedOverlay = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput())

  // module implementation
  override lazy val module = new VCU108FPGATestHarnessImp(this)
}

trait WithVisibleDigitalTopHarness { this: Shell =>
  implicit val p: Parameters
  // Like HasHarnessInstantiators, but with a little more visibility for DigitalTop/ChipTop
  lazy val chipParameters = p(MultiChipNChips) match {
    case Some(n) => (0 until n).map { i => p(MultiChipParameters(i)).alterPartial {
      case TargetDirKey => p(TargetDirKey) // hacky fix
      case MultiChipIdx => i
    }}
    case None => Seq(p)
  }

  lazy val lazyDuts = {
    val lazyDuts = chipParameters.zipWithIndex.map { case (q, i) =>
      LazyModule(q(BuildTop)(q)).suggestName(s"chiptop$i")
    }
    lazyDuts
  }
}

trait HasHarnessInstantiatorsEx extends HasHarnessInstantiators {
  def _harnessOuter: WithVisibleDigitalTopHarness
  private val harnessBinderClockFreq: Double = p(HarnessBinderClockFrequencyKey)
  def referenceClocks: LinkedHashMap[String, (Double, Clock)]
  override def instantiateChipTops(): Seq[LazyModule] = {
    // NOTE: We cannot use the previous function.
    // Use the previous one for instantiate the chip tops.
    // val ret = HasHarnessInstantiatorsEx.super.instantiateChipTops()

    require(p(MultiChipNChips).isEmpty || supportsMultiChip,
      s"Selected Harness does not support multi-chip")
    val duts = _harnessOuter.lazyDuts.map(l => Module(l.module))

    withClockAndReset (harnessBinderClock, harnessBinderReset) {
      _harnessOuter.lazyDuts.zipWithIndex.foreach {
        case (d: HasChipyardPorts, i: Int) => {
          ApplyHarnessBinders(this, d.ports, i)(_harnessOuter.chipParameters(i))
        }
        case _ =>
      }
      ApplyMultiHarnessBinders(this, _harnessOuter.lazyDuts)
    }

    if (p(DontTouchChipTopPorts)) {
      duts.map(_ match {
        case d: DontTouch => d.dontTouchPorts()
        case _ =>
      })
    }

    val harnessBinderClk = harnessClockInstantiator.requestClockMHz("harnessbinder_clock", getHarnessBinderClockFreqMHz)
    println(s"Harness binder clock is $harnessBinderClockFreq")
    harnessBinderClock := harnessBinderClk
    harnessBinderReset := ResetCatchAndSync(harnessBinderClk, referenceReset.asBool)

    harnessClockInstantiator.instantiateHarnessClocks(referenceClock, referenceClockFreqMHz)

    // Print the registered referenced clock first to debug
    for ((name, (freq, clock)) <- referenceClocks) {
      println(s"Register reference clock: name=$name, freq=$freq")
    }

    // Now, we handle a little different the clock instantiator
    for ((name, (freq, clock)) <- harnessClockInstantiator.clockMap) {
      // Search for it
      val filteredClocks = referenceClocks.filter(_._2._1 == freq).map(_._2._2)
      require(filteredClocks.nonEmpty, s"There are no clocks with a freq=$freq, name=$name")

      clock := filteredClocks.head // Just take the first one
    }
    _harnessOuter.lazyDuts
  }
}

class VCU108FPGATestHarnessImp(_outer: VCU108FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessInstantiatorsEx {
  override def provideImplicitClockToLazyChildren = true
  val vcu108Outer = _outer
  val _harnessOuter = _outer

  val reset = IO(Input(Bool())).suggestName("reset")
  _outer.xdc.addPackagePin(reset, "E36")  // NOTE: Not written in the guide. Is in the schematic. Bank 49
  _outer.xdc.addIOStandard(reset, "LVCMOS12")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  _outer.pllReset := (resetIBUF.io.O || powerOnReset || _outer.ndreset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = hReset
  def success = { require(false, "Unused"); false.B }
  def referenceClocks = {
    val m: LinkedHashMap[String, (Double, Clock)] = LinkedHashMap.empty
    m("dut_clock") = (_outer.dutFreqMHz * (1000 * 1000), referenceClock)
    _outer.extClocks.zipWithIndex.foreach{ case((freqMHz, cnode), i) =>
      m(s"ext_clock_$i") = (freqMHz * (1000 * 1000), cnode.in.head._1.clock)
    }
    m
  }

  childClock := referenceClock
  childReset := referenceReset

  val lazyDuts = instantiateChipTops()
}
