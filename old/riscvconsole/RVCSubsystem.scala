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
import riscvconsole.devices.debug._

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

  // NOTE: The Reset Vector is assigned by the MaskROM for now
  //val maskROMResetVectorSourceNode = BundleBridgeSource[UInt]()
  //tileResetVectorNexusNode := maskROMResetVectorSourceNode
}

trait HasRVCTilesModuleImp extends LazyModuleImp with DebugJTAGOnlyModuleImp
{
  val outer: HasRVCTiles with HasTileInterruptSources with HasTileInputConstants

  // NOTE: Just a copy of HasTilesModuleImp
  val reset_vector = outer.tileResetVectorIONodes.zipWithIndex.map { case (n, i) => n.makeIO(s"reset_vector_$i") }
  val tile_hartids = outer.tileHartIdIONodes.zipWithIndex.map { case (n, i) => n.makeIO(s"tile_hartids_$i") }

  val meip = if(outer.meipNode.isDefined) Some(IO(Input(Vec(outer.meipNode.get.out.size, Bool())))) else None
  meip.foreach { m =>
    m.zipWithIndex.foreach{ case (pin, i) =>
      (outer.meipNode.get.out(i)._1)(0) := pin
    }
  }
  val seip = if(outer.seipNode.isDefined) Some(IO(Input(Vec(outer.seipNode.get.out.size, Bool())))) else None
  seip.foreach { s =>
    s.zipWithIndex.foreach{ case (pin, i) =>
      (outer.seipNode.get.out(i)._1)(0) := pin
    }
  }
  val nmi = outer.tiles.zip(outer.tileNMIIONodes).zipWithIndex.map { case ((tile, n), i) => tile.tileParams.core.useNMI.option(n.makeIO(s"nmi_$i")) }
  // End: Just a copy of HasTilesModuleImp

  // TODO: The reset_vector and tile_hartids are exported as IO.
  // create file with core params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))
  // Generate C header with relevant information for Dromajo
  // This is included in the `dromajo_params.h` header file
  DromajoHelper.addArtefacts(InSubsystem)

  // NOTE: The Reset Vector is assigned by the MaskROM for now
  //outer.maskROMResetVectorSourceNode.bundle := p(TEEHWResetVector).U
}


class RVCSubsystem(implicit p: Parameters) extends BaseSubsystem
  with HasRVCTiles
{
  override lazy val module = new RVCSubsystemModuleImp(this)

  def getOMInterruptDevice(resourceBindingsMap: ResourceBindingsMap): Seq[OMInterrupt] = Nil
}

class RVCSubsystemModuleImp[+L <: RVCSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasRVCTilesModuleImp

