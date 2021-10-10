package riscvconsole.devices.codec

import chisel3._

import freechips.rocketchip.config.Field
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.BaseSubsystem

case object PeripheryCodecKey extends Field[Seq[CodecParams]](Nil)

trait HasPeripheryCodec { this: BaseSubsystem =>
  val codecNodes = p(PeripheryCodecKey).map { ps =>
    val codec = CodecAttachParams(ps).attachTo(this)
    codec.ioNode.makeSink()
  }
}

trait HasPeripheryCodecBundle {
  val codec: Seq[CodecIO]
}

trait HasPeripheryCodecModuleImp extends LazyModuleImp with HasPeripheryCodecBundle {
  val outer: HasPeripheryCodec
  val codec = outer.codecNodes.zipWithIndex.map { case(n,i) => n.makeIO()(ValName(s"codec_$i")) }
}
