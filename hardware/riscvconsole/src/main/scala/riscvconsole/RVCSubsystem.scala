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

  protected val rocketTileParams = p(RocketTilesKey)
  private val rocketCrossings = perTileOrGlobalSetting(p(RocketCrossingKey), rocketTileParams.size)

  val allTilesInfo = (rocketTileParams) zip (rocketCrossings)

  val tiles = allTilesInfo.sortWith(_._1.hartId < _._1.hartId).map {
    case (param, crossing) => {

      val tile = param match {
        case r: RocketTileParams => {
          LazyModule(new RocketTile(r, crossing, PriorityMuxHartIdFromSeq(rocketTileParams), logicalTreeNode))
        }
      }
      connectMasterPortsToSBus(tile, crossing)
      connectSlavePortsToCBus(tile, crossing)
      connectInterrupts(tile, debugOpt, clintOpt, plicOpt)

      tile
    }
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
  with HasResetVectorWire
  with HasRVCTilesModuleImp
{
  tile_inputs.zip(outer.hartIdList).foreach { case(wire, i) =>
    wire.hartid := i.U
    wire.reset_vector := global_reset_vector
  }

  // create file with boom params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))

  // Generate C header with relevant information for Dromajo
  // This is included in the `dromajo_params.h` header file
  DromajoHelper.addArtefacts
}
