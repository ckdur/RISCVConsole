package riscvconsole.config

import chipyard.clocking._
import freechips.rocketchip.devices.debug._
import org.chipsalliance.cde.config._

class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey, site).copy(enableTileClockGating = false, enableTileResetSetting = true) // NOTE: We cannot disable the TileReset.
})
