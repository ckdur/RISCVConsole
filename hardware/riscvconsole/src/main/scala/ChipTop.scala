package riscvconsole

import chisel3._
import chisel3.experimental.{Analog, attach}
import chipyard.BuildSystem
import chisel3.BlackBox
import freechips.rocketchip.resources.BindingScope
import freechips.rocketchip.util.DontTouch
import org.chipsalliance.diplomacy.lazymodule._
import org.chipsalliance.cde.config.Parameters
import sifive.fpgashells.shell.{IOPin, SDC}

class ChipTop(implicit p: Parameters) extends chipyard.ChipTop
