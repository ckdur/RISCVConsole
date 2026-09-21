package riscvconsole.fpga.vcu108

import chisel3._
import chipyard.harness._
import sys.process._
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.resources._
import freechips.rocketchip.util._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx.{VCU108DDRSize, VCU108ShellPMOD}
import testchipip.serdes.SerialTLKey
import sifive.blocks.devices.gpio._

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(
    SPIParams(rAddress = BigInt(0x64001000L)),
    SPIParams(rAddress = BigInt(0x64004000L))
  )
  case PeripheryGPIOKey => List(GPIOParams(address = 0x64002000L, width = 7))
  case VCU108ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VCU108DDRSize)))) // set extmem to DDR size
  case SerialTLKey => Nil // remove serialized tl port
  case testchipip.serdes.SerialTLKey => up(testchipip.serdes.SerialTLKey).slice(0, 1) // Only take the first SerialTL if available
})

class AllClocksAreIgnored extends HarnessClockInstantiator {
  def instantiateHarnessClocks(refClock: Clock, refClockFreqMHz: Double): Unit = {
    // Ignored
  }
}

class WithAllClocksAreIgnored extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new AllClocksAreIgnored
})

// DOC include start: AbstractVCU108 and Rocket
class WithVCU108Tweaks extends Config(
  // clocking
  new WithAllClocksAreIgnored ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithUniformBusFrequencies(100) ++
  new WithFPGAFrequency(100) ++ // default 100MHz freq
  // harness binders
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  new WithJTAG ++
  // other configuration
  new riscvconsole.config.WithSDBootBootROM ++
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1)
)

class RocketVCU108Config extends Config(
  new WithVCU108Tweaks ++
  new chipyard.RocketConfig
)
// DOC include end: AbstractVCU108 and Rocket

class BoomVCU108Config extends Config(
  new WithFPGAFrequency(50) ++
  new WithVCU108Tweaks ++
  new chipyard.MegaBoomV3Config
)

class WithFPGAFrequency(fMHz: Double) extends Config(
  new chipyard.harness.WithHarnessBinderClockFreqMHz(fMHz) ++
  new chipyard.config.WithSystemBusFrequency(fMHz) ++
  new chipyard.config.WithPeripheryBusFrequency(fMHz) ++
  new chipyard.config.WithControlBusFrequency(fMHz) ++
  new chipyard.config.WithFrontBusFrequency(fMHz) ++
  new chipyard.config.WithMemoryBusFrequency(fMHz)
)

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)
