package riscvconsole.fpga.tr4

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import sifive.blocks.devices.uart._
import sifive.fpgashells.shell.DesignKey
import chipyard.BuildSystem
import chipyard.fpga.arty100t.WithNoDesignKey
import sifive.blocks.devices.spi._

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyModule()(p)
})

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIFlashKey => up(PeripherySPIFlashKey) ++ Seq(
    SPIFlashParams(rAddress = 0x64001000L, fAddress = 0x40000000L, fSize = 0x10000000))
  case testchipip.tsi.UARTTSIClientKey => None  // This disables the UART TSI. We do not need this in FPGA
  case testchipip.serdes.SerialTLKey => up(testchipip.serdes.SerialTLKey).slice(0, 1) // Only take the first SerialTL if available
})

class WithSystemModifications extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(
    size = 0x40000000, beatBytes = 4
  )))
})

class WithTR4Modifiers extends Config(
  new WithNoDesignKey ++
  // Clocking
  new chipyard.harness.WithHarnessBinderClockFreqMHz(50.0) ++
  new chipyard.config.WithUniformBusFrequencies(50.0) ++
  //new chipyard.harness.WithAllClocksAreIgnored ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  // Clocking by default
  new chipyard.harness.WithClockFromHarness ++                     // all Clock I/O in ChipTop should be driven by harnessClockInstantiator
  new chipyard.harness.WithResetFromHarness ++                     // reset controlled by harness
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // generate clocks in harness with unsynthesizable ClockSourceAtFreqMHz
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore",        /** create a "uncore" clock group tieing all the bus clocks together */
    Seq("sbus", "mbus", "pbus", "fbus", "cbus", "obus", "implicit", "clock_tap"),
    Seq("tile"))) ++
  new chipyard.config.WithNoSubsystemClockIO ++                        // drive the subsystem diplomatic clocks from ChipTop instead of using implicit clocks
  // Devices
  new WithDefaultPeripherals ++
  new testchipip.serdes.WithNoSerialTL ++                                          // No serial TL
  //new chipyard.config.WithGPIO(width = 3) ++                                           // Add a regular GPIO of width 3
  //new chipyard.config.WithUART(address = 0x64000000L) ++                               // Add UART (Disabled and moved to WithDefaultPeripherals because the abstracts adds another)
  new riscvconsole.config.WithoutClockGating ++                                 // No clock gating
  new riscvconsole.config.WithJustJumpBootROM ++                                // The just jump boot ROM
  new chipyard.config.WithDebugModuleAbstractDataWords(8) ++         // increase debug module data capacity
  new freechips.rocketchip.subsystem.WithJtagDTM ++                         // set the debug module to expose a JTAG port
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++                        // TODO: necessary?
  // Harness binders (From ChipTop to Harness)
  new WithTR4SPIFlashBinder ++
  new WithTR4UARTBinder ++
  new WithTR4GPIOBinder ++
  new WithTR4JTAGBinder ++
  new WithTR4DDRMemBinder ++
  // IO Cells (From DigitalTop to ChipTop)
  new chipyard.iobinders.WithSPIIOPunchthrough ++
  new riscvconsole.iobinders.WithSPIFlashIOPunchthrough ++
  new chipyard.iobinders.WithGPIOPunchthrough ++
  new chipyard.iobinders.WithDebugIOCells(externalReset = false) ++
  new chipyard.iobinders.WithUARTIOCells ++
  new chipyard.iobinders.WithExtInterruptIOCells ++
  new chipyard.iobinders.WithTLMemPunchthrough ++
  // Bootloader
  new testchipip.boot.WithNoBootAddrReg ++
  new testchipip.boot.WithNoCustomBootPin ++
  // Buses
  new chipyard.config.WithTLBackingMemory ++
  new WithSystemModifications ++
  new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++      // leave the bus clocks undriven by sbus
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new freechips.rocketchip.subsystem.WithDTS("ckdur,tr4", Nil) // custom device name for DTS
)

class RocketTR4Config extends Config(
  new WithTR4Modifiers ++
  new chipyard.RocketConfig
)
