package riscvconsole.fpga.ulx3s

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.prci._
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
import sifive.fpgashells.devices.common._

// It is an SDRAM, but we use the DDR placer just to not repeat code
class SDRAMULX3SPlacedOverlay(val shell: ULX3SHarness, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[ULX3SSDRAM](name, designInput, shellInput)
{
  val sdramParams = sdram_bb_cfg (
    SDRAM_HZ = 50000000,
    SDRAM_ADDR_W = 24,
    SDRAM_COL_W = 9,
    SDRAM_BANK_W = 2,
    SDRAM_DQM_W = 2,
    SDRAM_DQ_W = 16,
    SDRAM_READ_LATENCY = 3
  )
  val memifParams             = SDRAMConfig(address = di.baseAddress, sdcfg = sdramParams)
  val memifClockDomainWrapper = LazyModule(new ClockSinkDomain(take = Some(ClockParameters(sdramParams.SDRAM_HZ.toDouble / 1000000))))
  val memif                   = memifClockDomainWrapper { LazyModule(new TLSDRAM(memifParams, 4, 4)) }
  memifClockDomainWrapper.clockNode := ClockGroup() := shell.harnessSysPLLNode

  val ioNode = memif.ioNode.makeSink()

  def overlayOutput = DDROverlayOutput(ddr = memif.node)
  def ioFactory = new ULX3SSDRAM

  // val getStatus = shell { InModuleBody { Wire(new SDRAMIf) } }

  shell { InModuleBody {
    io.from_SDRAMIf(ioNode.bundle.asInstanceOf[SDRAMIf])

    ULX3SSDRAMLocs.addr.zipWithIndex.foreach { case (pin, i) =>
      shell.lpf.addPackagePin(IOPin(io.sdram_addr_o, i), pin)
      shell.lpf.addIOStandard(IOPin(io.sdram_addr_o, i), "LVCMOS33", drive = Some(4))
    }
    ULX3SSDRAMLocs.data.zipWithIndex.foreach { case (pin, i) =>
      shell.lpf.addPackagePin(IOPin(io.sdram_data_io(i)), pin)
      shell.lpf.addIOStandard(IOPin(io.sdram_data_io(i)), "LVCMOS33", drive = Some(4))
    }
    ULX3SSDRAMLocs.ba.zipWithIndex.foreach { case (pin, i) =>
      shell.lpf.addPackagePin(IOPin(io.sdram_ba_o, i), pin)
      shell.lpf.addIOStandard(IOPin(io.sdram_ba_o, i), "LVCMOS33", drive = Some(4))
    }
    ULX3SSDRAMLocs.dqm.zipWithIndex.foreach { case (pin, i) =>
      shell.lpf.addPackagePin(IOPin(io.sdram_dqm_o, i), pin)
      shell.lpf.addIOStandard(IOPin(io.sdram_dqm_o, i), "LVCMOS33", drive = Some(4))
    }
    shell.lpf.addPackagePin(IOPin(io.sdram_clk_o), ULX3SSDRAMLocs.clk)
    shell.lpf.addIOStandard(IOPin(io.sdram_clk_o), "LVCMOS33", drive = Some(4))
    shell.lpf.addPackagePin(IOPin(io.sdram_cke_o), ULX3SSDRAMLocs.cke)
    shell.lpf.addIOStandard(IOPin(io.sdram_cke_o), "LVCMOS33", drive = Some(4))
    shell.lpf.addPackagePin(IOPin(io.sdram_cs_o), ULX3SSDRAMLocs.cs)
    shell.lpf.addIOStandard(IOPin(io.sdram_cs_o), "LVCMOS33", drive = Some(4))
    shell.lpf.addPackagePin(IOPin(io.sdram_we_o), ULX3SSDRAMLocs.we)
    shell.lpf.addIOStandard(IOPin(io.sdram_we_o), "LVCMOS33", drive = Some(4))
    shell.lpf.addPackagePin(IOPin(io.sdram_ras_o), ULX3SSDRAMLocs.ras)
    shell.lpf.addIOStandard(IOPin(io.sdram_ras_o), "LVCMOS33", drive = Some(4))
    shell.lpf.addPackagePin(IOPin(io.sdram_cas_o), ULX3SSDRAMLocs.cas)
    shell.lpf.addIOStandard(IOPin(io.sdram_cas_o), "LVCMOS33", drive = Some(4))
  } }
}

class SDRAMULX3SShellPlacer(val shell: ULX3SHarness, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[LatticeShell] {
  def place(designInput: DDRDesignInput) = new SDRAMULX3SPlacedOverlay(shell, valName.value, designInput, shellInput)
}

class ULX3SHarness(override implicit val p: Parameters) extends ULX3SShell {
  import GPIOULX3SPinConstraints._
  val sdram     = Overlay(DDROverlayKey, new SDRAMULX3SShellPlacer(this, DDRShellInput()))
  val uart      = Overlay(UARTOverlayKey, new UARTULX3SShellPlacer(this, UARTShellInput()))
  val jtagseq   = Seq(gp ->  0, gp ->  3, gp ->  2, gp ->  1, gp ->  4)
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugULX3SShellPlacer(this, ULX3SGPIOGroup(jtagseq), JTAGDebugShellInput()))
  val gpioseq   = Seq(gp ->  5, gp ->  6, gp ->  7, gp ->  8, gp ->  9)
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

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = _outer.dutClock.in.head._1.reset
  def success = { require(false, "Unused"); false.B}

  // Necessary to connect any child clock
  childClock := referenceClock
  childReset := referenceReset
  instantiateChipTops()
}
