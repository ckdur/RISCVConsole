package riscvconsole.fpga.ulx3s

import sys.process._
import chipyard._
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink.MaskROMLocated
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.devices.tilelink.{BootROMLocated, DevNullParams}
import freechips.rocketchip.tile.XLen
import riscvconsole.devices.codec._
import riscvconsole.devices.fft._
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.lattice._

class WithULX3SSystemModifications extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val freq = site(SystemBusKey).dtsFrequency.get
    val clean = s"make -C software/sdboot XLEN=${site(XLen)} TL_CLK=${freq} clean"
    require (clean.! == 0, "Failed to clean bootrom")
    val make = s"make -C software/sdboot XLEN=${site(XLen)} TL_CLK=${freq} bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./software/sdboot/sdboot.bin")
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(ULX3SSDRAMCfg).size))) // set extmem to SDRAM size (note the size)
})

class WithULX3STweaks extends Config (
  new chipyard.config.WithTLBackingMemory ++
    new WithULX3SSystemModifications ++
    new chipyard.harness.WithSerialTLTiedOff ++
    new testchipip.serdes.WithNoSerialTL ++
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
    new riscvconsole.system.RemoveDebugClockGating ++
    new riscvconsole.system.WithNoDesignKey ++
    new riscvconsole.system.WithRVCBuildSystem ++
    new chipyard.iobinders.WithGPIOPunchthrough
)

class RocketULX3SConfig extends Config(
  new WithULX3STweaks ++
    new chipyard.RocketConfig)                    // "base" rocketchip system