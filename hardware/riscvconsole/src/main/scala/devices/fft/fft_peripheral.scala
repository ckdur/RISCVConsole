package riscvconsole.devices.fft

import chisel3._

import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryFFTKey extends Field[Seq[FFTParams]](Nil)

trait HasPeripheryFFT { this: BaseSubsystem =>
  val fftNodes = p(PeripheryFFTKey).map { ps =>
    val codec = FFTAttachParams(ps).attachTo(this)
    codec
  }
}

trait HasPeripheryFFTBundle {
}

trait HasPeripheryFFTModuleImp extends LazyModuleImp with HasPeripheryFFTBundle {
  val outer: HasPeripheryFFT
}
