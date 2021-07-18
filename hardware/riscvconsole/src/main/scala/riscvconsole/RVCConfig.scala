package riscvconsole.system

import chipsalliance.rocketchip.config._
import freechips.rocketchip.subsystem.WithRV32
import freechips.rocketchip.devices.debug._

class RVCPeripheralsConfig(gpio: Int = 14) extends Config((site, here, up) => {
  case sifive.blocks.devices.uart.PeripheryUARTKey => Seq(
    new sifive.blocks.devices.uart.UARTParams(0x10000000))
  case sifive.blocks.devices.gpio.PeripheryGPIOKey => Seq(
    new sifive.blocks.devices.gpio.GPIOParams(0x10001000, gpio))
  case freechips.rocketchip.subsystem.PeripheryMaskROMKey => Seq(
    freechips.rocketchip.devices.tilelink.MaskROMParams(0x10000, "MyBootROM"))
})

class RemoveDebugClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map{ debug =>
    debug.copy(clockGate = false)
  }
})

class RVCConfig extends Config(
  new RVCPeripheralsConfig ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new freechips.rocketchip.subsystem.WithNoMemPort ++              // no top-level memory port at 0x80000000
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithInclusiveCache ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.With1TinyCore ++            // single rocket-core with scratchpad
    new WithRV32 ++
    new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++  // Hierarchical buses without L2
    new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system

class ArrowConfig extends Config(
  new RVCPeripheralsConfig(10) ++
    new RemoveDebugClockGating ++
    new freechips.rocketchip.subsystem.WithJtagDTM ++
    new freechips.rocketchip.subsystem.WithNoMemPort ++              // no top-level memory port at 0x80000000
    new freechips.rocketchip.subsystem.WithNoMMIOPort ++           // no top-level MMIO master port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithNoSlavePort ++          // no top-level MMIO slave port (overrides default set in rocketchip)
    new freechips.rocketchip.subsystem.WithInclusiveCache ++       // use Sifive L2 cache
    new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++ // no external interrupts
    new freechips.rocketchip.subsystem.With1TinyCore ++            // single rocket-core with scratchpad
    new WithRV32 ++
    new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++  // Hierarchical buses without L2
    new freechips.rocketchip.system.BaseConfig)                    // "base" rocketchip system
