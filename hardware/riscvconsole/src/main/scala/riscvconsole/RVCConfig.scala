package riscvconsole.system

import chipyard._
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink.MaskROMLocated
import freechips.rocketchip.diplomacy._
import riscvconsole.devices.codec._
import riscvconsole.devices.fft._
import sifive.fpgashells.shell._

class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyRawModule()(p)
})

class WithRVCBuildSystem extends Config ((site, here, up) => {
  case BuildSystem => (p: Parameters) => new RVCDigitalTop()(p)
})

class RVCPeripheralsConfig(gpio: Int = 14) extends Config((site, here, up) => {
  case sifive.blocks.devices.uart.PeripheryUARTKey => Seq(
    sifive.blocks.devices.uart.UARTParams(0x10000000))
  case sifive.blocks.devices.gpio.PeripheryGPIOKey => Seq(
    sifive.blocks.devices.gpio.GPIOParams(0x10001000, gpio))
  case sifive.blocks.devices.spi.PeripherySPIKey => Seq(
    sifive.blocks.devices.spi.SPIParams(0x10002000))
  //case sifive.blocks.devices.i2c.PeripheryI2CKey => Seq(
  //  sifive.blocks.devices.i2c.I2CParams(0x10003000))
  //case sifive.blocks.devices.spi.PeripherySPIFlashKey => Seq(
  //  sifive.blocks.devices.spi.SPIFlashParams(0x10003000, 0x20000000L))
  //case freechips.rocketchip.subsystem.PeripheryMaskROMKey => Seq()
})

class SetFrequency(freq: BigInt) extends Config (
  new chipyard.harness.WithSerialTLTiedOff ++
    new chipyard.harness.WithHarnessBinderClockFreqMHz(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithPeripheryBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithControlBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithMemoryBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithControlBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithSystemBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithFrontBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.config.WithOffchipBusFrequency(freq.toDouble / 1000000.0) ++
    new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
    new chipyard.clocking.WithPassthroughClockGenerator ++
    new freechips.rocketchip.subsystem.WithoutTLMonitors
)

class WithCODEC extends Config((site, here, up) => {
  case PeripheryCodecKey => Seq(CodecParams(0x10004000))
})

class WithDefaultFFT extends Config((site, here, up) => {
  case PeripheryFFTKey => Seq(FFTParams(0x10005000, 10, Some(0x10006000)))
})

class RemoveDebugClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map{ debug =>
    debug.copy(clockGate = false)
  }
})

class ArrowConfig extends Config(
  new RVCPeripheralsConfig(11) ++
    new SetFrequency(50000000) ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithRV32 ++
    new freechips.rocketchip.subsystem.WithTimebase(1000000) ++
    new freechips.rocketchip.subsystem.WithNBreakpoints(1) ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new chipyard.config.WithTLBackingMemory ++ // FPGA-shells converts the AXI to TL for us
    new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(1024) << 20) ++ // 256mb on Nexys4DDR
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++
    //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 1, nWays = 2, capacityKB = 16) ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.WithoutFPU() ++
    new freechips.rocketchip.subsystem.WithNMedCores(1) ++            // single rocket-core with VM support and FPU
    new freechips.rocketchip.subsystem.WithCoherentBusTopology ++  // Hierarchical buses with broadcast L2
    new chipyard.config.AbstractConfig)                    // "base" rocketchip system

class Nexys4DDRConfig extends Config(
  new RVCPeripheralsConfig(8) ++
    new SetFrequency(50000000) ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithRV32 ++
    new freechips.rocketchip.subsystem.WithTimebase(1000000) ++
    new freechips.rocketchip.subsystem.WithNBreakpoints(1) ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new chipyard.config.WithTLBackingMemory ++ // FPGA-shells converts the AXI to TL for us
    new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(256) << 20) ++ // 256mb on Nexys4DDR
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++
    //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks = 1, nWays = 2, capacityKB = 16) ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.WithoutFPU() ++
    new freechips.rocketchip.subsystem.WithNMedCores(1) ++            // single rocket-core with VM support and FPU
    new freechips.rocketchip.subsystem.WithCoherentBusTopology ++  // Hierarchical buses with broadcast L2
    new chipyard.config.AbstractConfig)                    // "base" rocketchip system
