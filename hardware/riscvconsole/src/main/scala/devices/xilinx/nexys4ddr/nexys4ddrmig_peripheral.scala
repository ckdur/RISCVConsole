package riscvconsole.devices.xilinx.nexys4ddr

import chisel3._
import chisel3.experimental.IO
import chisel3.util._
import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._
import sifive.fpgashells.devices.xilinx.xilinxnexys4ddrmig._

case object Nexys4DDRMIGMem extends Field[Option[MemoryPortParams]](None)

trait HasNexys4DDRMIG { this: BaseSubsystem =>
  private val memPortParamsOpt = p(Nexys4DDRMIGMem)
  private val portName = "nexys4DDRmig"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val nexys4DDRMIGDev = memPortParamsOpt.zipWithIndex.map{ case (MemoryPortParams(memPortParams, _), i) =>
    require(memPortParams.beatBytes == 8, s"Nexys4DDRMIG does not support beatBytes${memPortParams.beatBytes} different to 4")

    val ddr = LazyModule(
      new XilinxNexys4DDRMIG(
        XilinxNexys4DDRMIGParams(
          AddressSet.misaligned(
            memPortParams.base,
            0x08000000L * 1 // 128MB for the Nexys4DDR,
          ))))

    mbus.coupleTo(s"memory_${portName}_${i}") {
      ddr.node := _
    }

    ddr
  }
}

trait HasNexys4DDRMIGModuleImp extends LazyModuleImp {
  val outer: HasNexys4DDRMIG
  val nexys4DDRMIGPorts = outer.nexys4DDRMIGDev.map { ddr =>
    val qsysio = IO(new XilinxNexys4DDRMIGIO(0x08000000L))
    qsysio <> ddr.module.io.port
    qsysio
  }
}