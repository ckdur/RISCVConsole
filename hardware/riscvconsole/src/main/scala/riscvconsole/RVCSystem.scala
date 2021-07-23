package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import freechips.rocketchip.subsystem._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink.{TLFragmenter, TLRAM}
import riscvconsole.devices.sdram._
import testchipip._

case class SRAMConfig
(
  address: BigInt,
  size: BigInt
)
case object SRAMKey extends Field[Seq[SRAMConfig]](Nil)

class RVCSystem(implicit p: Parameters) extends RVCSubsystem
  with HasPeripheryGPIO
  with HasPeripheryUART
  with HasSDRAM
  with CanHaveMasterAXI4MemPort
  with CanHavePeripherySerial
{
  val spiDevs = p(PeripherySPIKey).map { ps =>
    SPIAttachParams(ps).attachTo(this)
  }
  val spiNodes = spiDevs.map ( _.ioNode.makeSink() )
  spiDevs.zipWithIndex.foreach { case (ps, i) =>
    val mmc = new MMCDevice(ps.device, 5) // Only the first one is mmc
    ResourceBinding {
      Resource(mmc, "reg").bind(ResourceAddress(0))
    }
  }

  // Add the chosen, for the bootargs to be output in the console at boot
  val chosen = new Device {
    def describe(resources: ResourceBindings): Description = {
      Description("chosen", Map("bootargs" -> Seq(ResourceString("console=hvc0 earlycon=sbi"))))
    }
  }
  ResourceBinding {
    Resource(chosen, "bootargs").bind(ResourceString(""))
  }

  val maskromparam = p(PeripheryMaskROMKey)
  val maskrom = maskromparam.map{
    case par =>
      MaskROM.attach(par, pbus)
  }

  val srams = p(SRAMKey).zipWithIndex.map { case(sramcfg, i) =>
    val sram = LazyModule(new TLRAM(AddressSet.misaligned(sramcfg.address, sramcfg.size).head))
    val mbus = locateTLBusWrapper(MBUS)
    mbus.coupleTo(s"sram_${i}") { bus => sram.node := TLFragmenter(4, mbus.blockBytes) := bus }
    sram
  }
  override lazy val module = new RVCSystemModuleImp(this)
}

class RVCSystemModuleImp[+L <: RVCSystem](_outer: L) extends RVCSubsystemModuleImp(_outer)
  with HasPeripheryGPIOModuleImp
  with HasPeripheryUARTModuleImp
  with HasSDRAMModuleImp
  with CanHavePeripherySerialModuleImp
  with HasRTCModuleImp
{
  val spi  = outer.spiNodes.zipWithIndex.map  { case(n,i) => n.makeIO()(ValName(s"spi_$i")).asInstanceOf[SPIPortIO] }
  global_reset_vector := outer.maskromparam(0).address.U
}