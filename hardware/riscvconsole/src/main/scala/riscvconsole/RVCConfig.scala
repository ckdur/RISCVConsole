package riscvconsole.system

import chipsalliance.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink.MaskROMLocated
import riscvconsole.devices.altera.ddr3.QsysDDR3Mem
import riscvconsole.devices.codec._
import riscvconsole.devices.sdram._
import riscvconsole.devices.fft._
import riscvconsole.devices.xilinx.artya7ddr.ArtyA7MIGMem

class RVCPeripheralsConfig(gpio: Int = 14) extends Config((site, here, up) => {
  case sifive.blocks.devices.uart.PeripheryUARTKey => Seq(
    sifive.blocks.devices.uart.UARTParams(0x10000000))
  case sifive.blocks.devices.gpio.PeripheryGPIOKey => Seq(
    sifive.blocks.devices.gpio.GPIOParams(0x10001000, gpio))
  case sifive.blocks.devices.spi.PeripherySPIKey => Seq(
    sifive.blocks.devices.spi.SPIParams(0x10002000))
  case sifive.blocks.devices.i2c.PeripheryI2CKey => Seq(
    sifive.blocks.devices.i2c.I2CParams(0x10003000))
  //case sifive.blocks.devices.spi.PeripherySPIFlashKey => Seq(
  //  sifive.blocks.devices.spi.SPIFlashParams(0x10003000, 0x20000000L))
  case MaskROMLocated(InSubsystem) => Seq(
    freechips.rocketchip.devices.tilelink.MaskROMParams(0x20000000L, "MyBootROM", 4096))
  case SDRAMKey => Seq()
  case SRAMKey => Seq()
  //case freechips.rocketchip.subsystem.PeripheryMaskROMKey => Seq()
  case SubsystemDriveAsyncClockGroupsKey => None
})

class SetFrequency(freq: BigInt) extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey).copy(dtsFrequency = Some(freq))
  case SystemBusKey => up(SystemBusKey).copy(dtsFrequency = Some(freq))
  case SDRAMKey => up(SDRAMKey).map{sd => sd.copy(sdcfg = sd.sdcfg.copy(SDRAM_HZ = freq))}
})

class WithSDRAM(cfg: SDRAMConfig) extends Config((site, here, up) => {
  case SDRAMKey => Seq(cfg)
})

class WithSRAM(cfg: SRAMConfig) extends Config((site, here, up) => {
  case SRAMKey => Seq(cfg)
})

class WithCODEC extends Config((site, here, up) => {
  case PeripheryCodecKey => Seq(CodecParams(0x10004000))
})

class WithDefaultFFT extends Config((site, here, up) => {
  case PeripheryFFTKey => Seq(FFTParams(0x10005000, 10, Some(0x10006000)))
})

class WithQsysDDR3Mem extends Config((site, here, up) => {
  case QsysDDR3Mem => Some(MemoryPortParams(MasterPortParams(0x80000000L, 0x40000000, 4, 4), 1))
  case SRAMKey => Nil
})

class WithArtyA7MIGMem extends Config((site, here, up) => {
  case ArtyA7MIGMem => Some(MemoryPortParams(MasterPortParams(0x80000000L, 0x10000000, 8, 4), 1))
  case SRAMKey => Nil
})

class WithExtMem extends Config((site, here, up) => {
  case ExtMem => Some(MemoryPortParams(MasterPortParams(0x80000000L, 0x40000000, 4, 4), 1))
  case SRAMKey => Nil
})

class RemoveDebugClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map{ debug =>
    debug.copy(clockGate = false)
  }
})

class ArrowConfig extends Config(
  new WithQsysDDR3Mem ++
    new RVCPeripheralsConfig(11) ++
    new SetFrequency(50000000) ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithRV32 ++
    new freechips.rocketchip.subsystem.WithTimebase(1000000) ++
    new freechips.rocketchip.subsystem.WithNBreakpoints(1) ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new freechips.rocketchip.subsystem.WithNoMemPort ++              // no top-level memory port at 0x80000000
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++
    //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 1, nWays = 2, capacityKB = 16) ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.WithoutFPU() ++
    new freechips.rocketchip.subsystem.WithNMedCores(1) ++            // single rocket-core with VM support and FPU
    new freechips.rocketchip.subsystem.WithCoherentBusTopology ++  // Hierarchical buses with broadcast L2
    new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system

class DE2Config extends Config(
  new WithSDRAM(SDRAMConfig(
    address = 0x80000000L,
    sdcfg = sdram_bb_cfg(
      SDRAM_HZ = 50000000L,
      SDRAM_DQM_W = 4,
      SDRAM_DQ_W = 32,
      SDRAM_READ_LATENCY = 2))
  ) ++
    new RVCPeripheralsConfig(10) ++
    new SetFrequency(50000000) ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithRV32 ++
    new freechips.rocketchip.subsystem.WithTimebase(1000000) ++
    new freechips.rocketchip.subsystem.WithNBreakpoints(1) ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new freechips.rocketchip.subsystem.WithNoMemPort ++              // no top-level memory port at 0x80000000
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++
    //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 1, nWays = 2, capacityKB = 16) ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.WithoutFPU() ++
    new freechips.rocketchip.subsystem.WithNMedCores(1) ++            // single rocket-core with VM support and FPU
    new freechips.rocketchip.subsystem.WithCoherentBusTopology ++  // Hierarchical buses with broadcast L2
    new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system

class ArtyA7Config extends Config(
  new WithArtyA7MIGMem ++
    new RVCPeripheralsConfig(8) ++
    new SetFrequency(50000000) ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithRV32 ++
    new freechips.rocketchip.subsystem.WithTimebase(1000000) ++
    new freechips.rocketchip.subsystem.WithNBreakpoints(1) ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new freechips.rocketchip.subsystem.WithNoMemPort ++              // no top-level memory port at 0x80000000
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++
    //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 1, nWays = 2, capacityKB = 16) ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.WithoutFPU() ++
    new freechips.rocketchip.subsystem.WithNMedCores(1) ++            // single rocket-core with VM support and FPU
    new freechips.rocketchip.subsystem.WithCoherentBusTopology ++  // Hierarchical buses with broadcast L2
    new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system

class RVCHarnessConfig extends Config(new SetFrequency(100000000) ++ new DE2Config)
