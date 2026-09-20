package riscvconsole.config

import chipyard.{BuildSystem, ChipTop}
import chipyard.harness._
import chipyard.iobinders._
import freechips.rocketchip.util.ClockGate
import org.chipsalliance.cde.config._
import testchipip.serdes._

class DefaultTopAndSystem extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new riscvconsole.ChipTop()(p)
  case BuildSystem => (p: Parameters) => new riscvconsole.DigitalTop()(p)
})

class RocketConfig extends Config(
  new DefaultTopAndSystem ++
  new chipyard.RocketConfig)

