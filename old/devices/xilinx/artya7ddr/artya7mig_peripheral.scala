package riscvconsole.devices.xilinx.artya7ddr

import chisel3._
import chisel3.experimental.IO
import chisel3.util._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._
import sifive.fpgashells.devices.xilinx.xilinxarty100tmig._

case object ArtyA7MIGMem extends Field[Option[MemoryPortParams]](None)

trait HasArtyA7MIG { this: BaseSubsystem =>
  private val memPortParamsOpt = p(ArtyA7MIGMem)
  private val portName = "artya7mig"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val artyA7MIGDev = memPortParamsOpt.zipWithIndex.map{ case (MemoryPortParams(memPortParams, _), i) =>
    require(memPortParams.beatBytes == 8, s"ArtyA7MIG does not support beatBytes${memPortParams.beatBytes} different to 4")

    val ddr = LazyModule(
      new XilinxArty100TMIG(
        XilinxArty100TMIGParams(
          AddressSet.misaligned(
            memPortParams.base,
            0x10000000L * 1 // 256MB for the Arty7DDR,
          ))))

    mbus.coupleTo(s"memory_${portName}_${i}") {
      ddr.node := _
    }

    ddr
  }
}

trait HasArtyA7MIGModuleImp extends LazyModuleImp {
  val outer: HasArtyA7MIG
  val artyA7MIGPorts = outer.artyA7MIGDev.map { ddr =>
    val qsysio = IO(new XilinxArty100TMIGIO(0x10000000L))
    qsysio <> ddr.module.io.port
    qsysio
  }
}