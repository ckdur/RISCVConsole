package riscvconsole.system

import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.subsystem._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import freechips.rocketchip.devices.tilelink._

class RVCSystem(implicit p: Parameters) extends RVCSubsystem
  with HasPeripheryGPIO
  with HasPeripheryUART
  with CanHaveMasterAXI4MemPort
{
  val maskromparam = p(PeripheryMaskROMKey)
  val maskrom = maskromparam.map{
    case par =>
      MaskROM.attach(par, pbus)
  }
  override lazy val module = new RVCSystemModuleImp(this)
}

class RVCSystemModuleImp[+L <: RVCSystem](_outer: L) extends RVCSubsystemModuleImp(_outer)
  with HasPeripheryGPIOModuleImp
  with HasPeripheryUARTModuleImp
{
  global_reset_vector := outer.maskromparam(0).address.U
}