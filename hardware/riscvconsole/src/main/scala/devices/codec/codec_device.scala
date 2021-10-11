package riscvconsole.devices.codec

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.diplomaticobjectmodel.DiplomaticObjectModelAddressing
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMDevice, OMInterrupt, OMMemoryRegion, OMRegister}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalModuleTree, LogicalTreeNode}
import riscvconsole.devices.codec.codec_param.AUDIO_DATA_WIDTH


case class CodecParams(address: BigInt)

class CodecIO extends Bundle {
  val AUD_BCLK = new Bidir
  val AUD_ADCLRCK = new Bidir
  val AUD_DACLRCK = new Bidir
  val AUD_ADCDAT = Input(Bool())
  val AUD_DACDAT = Output(Bool())
}

case class OMCodec
(
  memoryRegions: Seq[OMMemoryRegion],
  interrupts: Seq[OMInterrupt],
  _types: Seq[String] = Seq("OMCodec", "OMDevice", "OMComponent"),
) extends OMDevice

object CodecCtrlRegs {
  val out_l       = 0x00
  val out_r       = 0x04
  val in_l        = 0x08
  val in_r        = 0x0c
  val ctrl        = 0x10
  val status      = 0x14
}

abstract class Codec(busWidthBytes: Int, c: CodecParams)(implicit p: Parameters)
  extends IORegisterRouter(
    RegisterRouterParams(
      name = "codec",
      compat = Seq("console,codec0"),
      base = c.address,
      beatBytes = busWidthBytes),
    new CodecIO)
    with HasInterruptSources {

  def nInterrupts = 2
  lazy val module = new LazyModuleImp(this) {
    val codec = Module(new codec)

    // Analog interface
    codec.io.AUD_ADCDAT := port.AUD_ADCDAT
    port.AUD_DACDAT := codec.io.AUD_DACDAT
    port.AUD_BCLK <> codec.io.AUD_BCLK
    port.AUD_ADCLRCK <> codec.io.AUD_ADCLRCK
    port.AUD_DACLRCK <> codec.io.AUD_DACLRCK

    // Clears
    val clear_audio_out_memory = WireInit(false.B)
    val clear_audio_in_memory = WireInit(false.B)
    codec.io.clear_audio_in_memory := clear_audio_in_memory
    codec.io.clear_audio_out_memory := clear_audio_out_memory

    // FIFOs
    val left_channel_audio_out = Reg(UInt(AUDIO_DATA_WIDTH.W))
    val right_channel_audio_out = Reg(UInt(AUDIO_DATA_WIDTH.W))
    val write_audio_out = WireInit(false.B)
    val audio_out_allowed = codec.io.audio_out_allowed
    codec.io.left_channel_audio_out := left_channel_audio_out
    codec.io.right_channel_audio_out := right_channel_audio_out
    codec.io.write_audio_out := write_audio_out

    val left_channel_audio_in = codec.io.left_channel_audio_in
    val right_channel_audio_in = codec.io.right_channel_audio_in
    val read_audio_in = WireInit(false.B)
    val audio_in_available = codec.io.audio_in_available
    codec.io.read_audio_in := read_audio_in

    // Interrupts
    val int_en_out = RegInit(false.B)
    val int_en_in = RegInit(false.B)
    interrupts(0) := int_en_out & audio_out_allowed
    interrupts(1) := int_en_in & audio_in_available

    // Mapping
    val ctrlFields = Seq(
      RegField(1, write_audio_out),
      RegField(1, read_audio_in),
      RegField(6),
      RegField(1, clear_audio_out_memory),
      RegField(1, clear_audio_in_memory),
      RegField(6),
      RegField(1, int_en_out),
      RegField(1, int_en_in),
    )
    val statusFields = Seq(
      RegField.r(1, audio_out_allowed),
      RegField.r(1, audio_in_available),
    )
    val mapping = Seq(
      CodecCtrlRegs.out_l -> Seq(RegField(32, left_channel_audio_out,
        RegFieldDesc("left_channel_audio_out", "Data Output Left Channel"))),
      CodecCtrlRegs.out_r -> Seq(RegField(32, right_channel_audio_out,
        RegFieldDesc("right_channel_audio_out", "Data Output Right Channel"))),
      CodecCtrlRegs.in_l -> Seq(RegField.r(32, left_channel_audio_in,
        RegFieldDesc("left_channel_audio_in", "Data Input Left Channel"))),
      CodecCtrlRegs.in_r -> Seq(RegField.r(32, right_channel_audio_in,
        RegFieldDesc("right_channel_audio_in", "Data Input Right Channel"))),
      CodecCtrlRegs.ctrl -> ctrlFields,
      CodecCtrlRegs.status -> statusFields,
    )
    regmap(mapping :_*)
    val omRegMap = OMRegister.convert(mapping:_*)
  }

  val logicalTreeNode = new LogicalTreeNode(() => Some(device)) {
    def getOMComponents(resourceBindings: ResourceBindings, children: Seq[OMComponent] = Nil): Seq[OMComponent] = {
      Seq(
        OMCodec(
          memoryRegions = DiplomaticObjectModelAddressing.getOMMemoryRegions("Codec", resourceBindings, Some(module.omRegMap)),
          interrupts = DiplomaticObjectModelAddressing.describeGlobalInterrupts(device.describe(resourceBindings).name, resourceBindings),
        )
      )
    }
  }
}

class TLCodec(busWidthBytes: Int, params: CodecParams)(implicit p: Parameters)
  extends Codec(busWidthBytes, params) with HasTLControlRegMap

object Codec {
  val nextId = {
    var i = -1; () => {
      i += 1; i
    }
  }
}

case class CodecAttachParams
(
  device: CodecParams,
  controlWhere: TLBusWrapperLocation = PBUS,
  blockerAddr: Option[BigInt] = None,
  controlXType: ClockCrossingType = NoCrossing,
  intXType: ClockCrossingType = NoCrossing)
{
  def attachTo(where: Attachable)(implicit p: Parameters): TLCodec = where {
    val name = s"codec_${Codec.nextId()}"
    val cbus = where.locateTLBusWrapper(controlWhere)
    val codecClockDomainWrapper = LazyModule(new ClockSinkDomain(take = None))
    val codec = codecClockDomainWrapper { LazyModule(new TLCodec(cbus.beatBytes, device)) }
    codec.suggestName(name)

    cbus.coupleTo(s"device_named_$name") { bus =>

      val blockerOpt = blockerAddr.map { a =>
        val blocker = LazyModule(new TLClockBlocker(BasicBusBlockerParams(a, cbus.beatBytes, cbus.beatBytes)))
        cbus.coupleTo(s"bus_blocker_for_$name") { blocker.controlNode := TLFragmenter(cbus) := _ }
        blocker
      }

      codecClockDomainWrapper.clockNode := (controlXType match {
        case _: SynchronousCrossing =>
          cbus.dtsClk.foreach(_.bind(codec.device))
          cbus.fixedClockNode
        case _: RationalCrossing =>
          cbus.clockNode
        case _: AsynchronousCrossing =>
          val codecClockGroup = ClockGroup()
          codecClockGroup := where.asyncClockGroupsNode
          blockerOpt.map { _.clockNode := codecClockGroup } .getOrElse { codecClockGroup }
      })

      (codec.controlXing(controlXType)
        := TLFragmenter(cbus)
        := blockerOpt.map { _.node := bus } .getOrElse { bus })
    }

    (intXType match {
      case _: SynchronousCrossing => where.ibus.fromSync
      case _: RationalCrossing => where.ibus.fromRational
      case _: AsynchronousCrossing => where.ibus.fromAsync
    }) := codec.intXing(intXType)

    LogicalModuleTree.add(where.logicalTreeNode, codec.logicalTreeNode)

    codec
  }
}