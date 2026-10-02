package riscvconsole.devices.adcfifo

import chisel3._
import chisel3.util._
import freechips.rocketchip.interrupts.HasInterruptSources
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._
import sifive.blocks.util._

class ADCFIFOIO(bits: Int = 5) extends Bundle {
  val valid = Output(Bool())
  val rst_n = Output(Bool())
  val clk = Output(Clock())
  val io = Flipped(ValidIO(UInt(bits.W)))
}

case class ADCFIFOParams(address: BigInt, bits: Int = 5, freqMHz: Double = 10)

object ADCFIFOCtrlRegs {
  val data = 0x00
  val ctrl = 0x04
}

abstract class ADCFIFO(busWidthBytes: Int, c: ADCFIFOParams)(implicit p: Parameters)
  extends IORegisterRouter(
    RegisterRouterParams(
      name = "adcfifo",
      compat = Seq("console,adcfifo0"),
      base = c.address,
      beatBytes = busWidthBytes),
    new ADCFIFOIO(c.bits)) {
  // Require an external clock
  val adcClock = ClockSinkNode(freqMHz = c.freqMHz)

  lazy val module = new LazyModuleImp(this) {
    val portq = port //.asInstanceOf[ADCFIFOIO] // Re-interpret cast

    // Connect the requested clock
    val (adcClk, _) = adcClock.in(0)
    portq.clk := adcClk.clock

    // Polarity of the clock
    val cpol = RegInit(false.B)

    // Catched reset
    val crst = adcClk.reset.asBool // ResetCatchAndSync(portq.clk, reset.asBool) // NOTE: Do not need to sync here
    val prst = RegInit(false.B)
    portq.rst_n := crst || prst

    // The valid to the ADC
    val valid = RegInit(false.B)
    portq.valid := valid

    // To make the sampling clock async. This clock is requested
    val q = Module(new AsyncQueue(UInt(c.bits.W)))
    q.io.enq_clock := (adcClk.clock.asBool ^ cpol).asClock
    q.io.enq_reset := crst
    q.io.enq.valid := portq.io.valid
    q.io.enq.bits := portq.io.bits

    q.io.deq_clock := clock
    q.io.deq_reset := reset.asBool

    regmap(Seq(
      ADCFIFOCtrlRegs.data -> NonBlockingDequeue(q.io.deq),
      ADCFIFOCtrlRegs.ctrl -> Seq(
        RegField(1, valid, RegFieldDesc("Valid", "ADC Persistent Valid", reset = Some(0))),
        RegField(7),
        RegField(1, prst, RegFieldDesc("Reset", "ADC Reset Trigger", reset = Some(0))),
        RegField(7),
        RegField(1, cpol, RegFieldDesc("CPOL", "Clock Polarity", reset = Some(0)))
      )
    ):_*)
  }
}

class TLADCFIFO(busWidthBytes: Int, params: ADCFIFOParams)(implicit p: Parameters)
  extends ADCFIFO(busWidthBytes, params) with HasTLControlRegMap

case object PeripheryADCFIFOKey extends Field[Option[ADCFIFOParams]](None)

trait HasPeripheryADCFIFO { this: BaseSubsystem =>
  val adcfifoNodes = p(PeripheryADCFIFOKey).map { ps =>
    val name = "adcfifo"
    val cbus = locateTLBusWrapper(PBUS)
    val adcfifoclock = LazyModule(new ClockSinkDomain(take = None))
    val adcfifo = adcfifoclock { LazyModule(new TLADCFIFO(cbus.beatBytes, ps)) }
    adcfifo.suggestName(name)

    cbus.dtsClk.foreach(_.bind(adcfifo.device))
    adcfifoclock.clockNode := cbus.fixedClockNode

    cbus.coupleTo(s"device_named_${name}") { bus =>
      (adcfifo.controlXing() := TLFragmenter(cbus) := bus)
    }

    adcfifo.adcClock := ClockGroup() := allClockGroupsNode

    adcfifo.ioNode.makeSink()
  }

  val adcfifoIO = InModuleBody { adcfifoNodes.zipWithIndex.map { case(n, i) =>
    n.makeIO()(ValName(s"adcfifo_$i")).asInstanceOf[ADCFIFOIO]
  }}
}

class WithADCFIFO extends Config((site, here, up ) => {
  case PeripheryADCFIFOKey => Some(ADCFIFOParams(0x64003000L))
})
