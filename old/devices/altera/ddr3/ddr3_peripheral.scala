package riscvconsole.devices.altera.ddr3

import chisel3._
import chisel3.experimental.{IO}
import chisel3.util._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._

case object QsysDDR3Mem extends Field[Option[MemoryPortParams]](None)

trait HasQsysDDR3 { this: BaseSubsystem =>
  private val memPortParamsOpt = p(QsysDDR3Mem)
  private val portName = "qsys_ddr3"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val ddr3Dev = memPortParamsOpt.zipWithIndex.map{ case (MemoryPortParams(memPortParams, _), i) =>
    val base = AddressSet.misaligned(memPortParams.base, memPortParams.size)
    require(memPortParams.beatBytes == 4, s"QsysDDR3 does not support beatBytes${memPortParams.beatBytes} different to 4")

    val qsys = LazyModule(new QsysPlatform(base))
    mbus.coupleTo(s"memory_${portName}_${i}") {
      qsys.node := _
    }

    qsys
  }
}

trait HasQsysDDR3ModuleImp extends LazyModuleImp {
  val outer: HasQsysDDR3
  val ddr3refclk = p(QsysDDR3Mem).map{A => IO(Input(Clock())) }
  val ddr3refrstn = p(QsysDDR3Mem).map{A => IO(Input(Bool())) }
  val ddr3Ports = outer.ddr3Dev.map { qsys =>
    val qsysio = IO(new QsysIO)
    ddr3refclk.foreach(qsys.module.io.ckrst.ddr_clk_clk := _)
    ddr3refrstn.foreach(qsys.module.io.ckrst.ddr_reset_reset_n := _)
    qsysio <> qsys.module.io.port
    qsysio
  }
}