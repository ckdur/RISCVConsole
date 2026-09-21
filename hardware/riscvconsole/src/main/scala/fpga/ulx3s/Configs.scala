package riscvconsole.fpga.ulx3s

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._
import sifive.blocks.devices.uart._
import sifive.fpgashells.shell.DesignKey
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyModule()(p)
})

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIFlashKey => Nil
  case PeripheryGPIOKey => Seq(GPIOParams(address = 0x64006000L, width = 5))
  case testchipip.tsi.UARTTSIClientKey => None
  case testchipip.serdes.SerialTLKey => Nil
})

class WithSystemModifications extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(
    size = 0x2000000, beatBytes = 4
  )))
})

class WithULX3SModifiers extends Config(
  new WithNoDesignKey ++
  // Clocking
  new chipyard.harness.WithHarnessBinderClockFreqMHz(50.0) ++
  new chipyard.config.WithUniformBusFrequencies(50.0) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
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
  new WithULX3SUARTBinder ++
  new WithULX3SGPIOBinder ++
  new WithULX3SJTAGBinder ++
  new WithULX3SDDRMemBinder ++
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
  new testchipip.soc.WithNoScratchpads ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(capacityKB = 32) ++
  new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++      // leave the bus clocks undriven by sbus
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new freechips.rocketchip.subsystem.WithDTS("ckdur,ulx3s", Nil) // custom device name for DTS
)

class RocketULX3SConfig extends Config(
  new WithULX3SModifiers ++
  new freechips.rocketchip.rocket.WithNMedCores(1) ++
  new chipyard.config.AbstractConfig
)
