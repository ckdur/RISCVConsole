package riscvconsole

import chipyard._

import firrtl.options.{StageMain}
import chipyard.stage.ChipyardStage

object Generator extends StageMain(new ChipyardStage)
