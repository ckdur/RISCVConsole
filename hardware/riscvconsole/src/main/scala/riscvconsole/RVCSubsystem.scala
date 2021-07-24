package riscvconsole.system


import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.devices.tilelink.{CanHavePeripheryCLINT, CanHavePeripheryPLIC}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{PriorityMuxHartIdFromSeq, RocketTile, RocketTileParams}
import freechips.rocketchip.util._
import testchipip.DromajoHelper

trait HasRVCTiles extends HasTiles
  with CanHavePeripheryPLIC
  with CanHavePeripheryCLINT
  with HasPeripheryDebug
{ this: BaseSubsystem =>

  val module: HasRVCTilesModuleImp

  Seq(PBUS, FBUS, MBUS, CBUS).foreach { loc =>
    tlBusWrapperLocationMap.lift(loc).foreach { _.clockGroupNode := asyncClockGroupsNode }
  }

  def coreMonitorBundles = tiles.map {
    case r: RocketTile => r.module.core.rocketImpl.coreMonitorBundle
  }.toList
}

trait HasRVCTilesModuleImp extends HasTilesModuleImp
  with HasPeripheryDebugModuleImp
{
  val outer: HasRVCTiles
}


class RVCSubsystem(implicit p: Parameters) extends BaseSubsystem
  with HasRVCTiles
{
  override lazy val module = new RVCSubsystemModuleImp(this)

  def getOMInterruptDevice(resourceBindingsMap: ResourceBindingsMap): Seq[OMInterrupt] = Nil
}

class RVCSubsystemModuleImp[+L <: RVCSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasRVCTilesModuleImp
{
  // create file with core params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))
  // Generate C header with relevant information for Dromajo
  // This is included in the `dromajo_params.h` header file
  DromajoHelper.addArtefacts(InSubsystem)
}
