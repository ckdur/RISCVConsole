package riscvconsole.fpga.ulx3s

import sys.process._
import chipyard._
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink.{BootROMLocated, BootROMParams, DevNullParams, MaskROMLocated}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile.XLen
import riscvconsole.devices.codec._
import riscvconsole.devices.fft._
import riscvconsole.system._
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.lattice._

class WithULX3SSystemModifications extends Config((site, here, up) => {
  case LatchedBootROMLocated(x) =>
    // invoke makefile for sdboot
    val freq = site(SystemBusKey).dtsFrequency.get
    val clean = s"make -C software/sdboot XLEN=${site(XLen)} TL_CLK=${freq} clean"
    require (clean.! == 0, "Failed to clean bootrom")
    val make = s"make -C software/sdboot XLEN=${site(XLen)} TL_CLK=${freq} bin"
    require (make.! == 0, "Failed to build bootrom")
    Some(BootROMParams(hang = 0x10000, contentFileName = s"./software/sdboot/sdboot.bin"))

  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(ULX3SSDRAMCfg).size))) // set extmem to SDRAM size (note the size)
})

class WithULX3STweaks extends Config (
  new chipyard.config.WithTLBackingMemory ++
    new chipyard.config.WithNoBootROM ++
    new WithULX3SSystemModifications ++
    new chipyard.harness.WithSerialTLTiedOff ++
    new testchipip.serdes.WithNoSerialTL ++
    new testchipip.soc.WithMbusScratchpad(0x82200000L, 0x4000) ++
    new testchipip.soc.WithNoScratchpads ++
    new testchipip.boot.WithNoCustomBootPin ++
    new testchipip.boot.WithNoBootAddrReg ++
    new freechips.rocketchip.subsystem.WithTimebase(1000000) ++
    new chipyard.harness.WithHarnessBinderClockFreqMHz(20.0) ++
    new chipyard.config.WithPeripheryBusFrequency(20.0) ++
    new chipyard.config.WithControlBusFrequency(20.0) ++
    new chipyard.config.WithMemoryBusFrequency(20.0) ++
    new chipyard.config.WithControlBusFrequency(20.0) ++
    new chipyard.config.WithSystemBusFrequency(20.0) ++
    new chipyard.config.WithFrontBusFrequency(20.0) ++
    new chipyard.config.WithOffchipBusFrequency(20.0) ++
    new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
    new chipyard.clocking.WithPassthroughClockGenerator ++
    new freechips.rocketchip.subsystem.WithoutTLMonitors ++
    new riscvconsole.fpga.ulx3s.WithULX3SJTAG ++
    new riscvconsole.fpga.ulx3s.WithULX3SUART ++
    new riscvconsole.fpga.ulx3s.WithULX3SSDRAMTL ++
    new riscvconsole.fpga.ulx3s.WithULX3SUARTTSI ++
    new riscvconsole.fpga.ulx3s.WithULX3SGPIOBinder ++
    new riscvconsole.fpga.ulx3s.WithULX3SSDBinder ++
    // new chipyard.config.WithUART ++
    new chipyard.config.WithSPI ++
    new chipyard.config.WithGPIO(width = 10) ++
    new riscvconsole.system.WithoutClockGating ++
    new riscvconsole.system.WithNoDesignKey ++
    new riscvconsole.system.WithRVCBuildSystem ++
    new chipyard.iobinders.WithGPIOPunchthrough ++
    // Core configs
    new freechips.rocketchip.subsystem.WithRV32 ++
    new freechips.rocketchip.subsystem.WithNBreakpoints(1) ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++
    //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 1, nWays = 2, capacityKB = 16) ++       // use Sifive L2 cache
    new chipyard.config.WithBroadcastManager ++ // no l2
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.WithoutFPU() ++
    new freechips.rocketchip.subsystem.WithNSmallCores(1) ++            // single rocket-core with VM support and FPU
    new freechips.rocketchip.subsystem.WithCoherentBusTopology  // Hierarchical buses with broadcast L2
)

class RocketULX3SConfig extends Config(
  new WithULX3STweaks ++
    new chipyard.config.AbstractConfig)
