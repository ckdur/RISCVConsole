package riscvconsole.devices.rocounter

import chisel3._
import chisel3.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.lazymodule._

// Frequency counter for the ring oscillators of the ASICONE chip (ro_reliability).
// Each channel counts the rising edges of the RO output pad (the ring divided by 32 on chip)
// during a gate window of the bus clock. Also drives the RO_RST_B and DUT_gate pins.
// Channel 0 is RO 101, channel 1 is RO 13.
class ROCounterIO(n: Int = 2) extends Bundle {
  val rstb = Output(Bool())
  val dut_gate = Output(UInt(n.W))
  val vout = Input(UInt(n.W))
}

case class ROCounterParams(address: BigInt, n: Int = 2, syncStages: Int = 3)

object ROCounterCtrlRegs {
  val ctrl  = 0x00 // [0] RO_RST_B, [8+i] DUT_gate of channel i
  val gate  = 0x04 // Gate window, in bus clock cycles
  val cmd   = 0x08 // W [0] start; R [0] busy, [1] done; RW [8] continuous
  val seq   = 0x0C // Windows completed since reset
  val level = 0x10 // Synchronized level of the RO outputs (debug)
  val count = 0x20 // Edges counted in the last window, one word per channel
}

abstract class ROCounter(busWidthBytes: Int, c: ROCounterParams, busFreqHz: BigInt)(implicit p: Parameters)
  extends IORegisterRouter(
    RegisterRouterParams(
      name = "rocounter",
      compat = Seq("console,rocounter0"),
      base = c.address,
      beatBytes = busWidthBytes),
    new ROCounterIO(c.n)) {

  lazy val module = new LazyModuleImp(this) {
    val portq = port

    // Pin control. RO_RST_B starts asserted (dividers in reset), DUT_gate starts low
    val rstb = RegInit(false.B)
    val gate = RegInit(0.U(c.n.W))
    portq.rstb := rstb
    portq.dut_gate := gate

    // The RO outputs are asynchronous: synchronize and detect rising edges.
    // Sampling at the bus clock, an input up to ~1/3 of it is counted correctly
    val vin = VecInit(portq.vout.asBools.map(v => SynchronizerShiftReg(v, c.syncStages)))
    val vprev = RegNext(vin)
    val rise = VecInit((vin zip vprev).map { case (a, b) => a && !b })

    // Gate window (default 1 s)
    val gateLen = RegInit(busFreqHz.U(32.W))
    val timer = RegInit(0.U(32.W))
    val running = RegInit(false.B)
    val done = RegInit(false.B)
    val continuous = RegInit(false.B)
    val seq = RegInit(0.U(32.W))
    val acc = RegInit(VecInit(Seq.fill(c.n)(0.U(32.W))))
    val result = RegInit(VecInit(Seq.fill(c.n)(0.U(32.W))))
    val start = WireDefault(false.B)

    when (start) {
      running := true.B
      done := false.B
      timer := 0.U
      acc.foreach(_ := 0.U)
    } .elsewhen (running) {
      when (timer === gateLen - 1.U) {
        (result zip acc zip rise).foreach { case ((r, a), e) => r := a + e }
        acc.foreach(_ := 0.U)
        timer := 0.U
        seq := seq + 1.U
        done := true.B
        running := continuous
      } .otherwise {
        (acc zip rise).foreach { case (a, e) => a := a + e }
        timer := timer + 1.U
      }
    }

    regmap(Seq(
      ROCounterCtrlRegs.ctrl -> Seq(
        RegField(1, rstb, RegFieldDesc("rstb", "RO_RST_B pin (dividers reset when 0)", reset = Some(0))),
        RegField(7),
        RegField(c.n, gate, RegFieldDesc("dut_gate", "DUT_gate pin of each RO", reset = Some(0)))
      ),
      ROCounterCtrlRegs.gate -> Seq(
        RegField(32, gateLen, RegFieldDesc("gate", "Gate window in bus clock cycles", reset = Some(busFreqHz)))
      ),
      ROCounterCtrlRegs.cmd -> Seq(
        RegField(1, RegReadFn(running), RegWriteFn((valid, data) => { when (valid && data(0)) { start := true.B }; true.B }),
          RegFieldDesc("start", "W: start a window. R: busy")),
        RegField.r(1, done, RegFieldDesc("done", "A window finished since the last start")),
        RegField(6),
        RegField(1, continuous, RegFieldDesc("continuous", "Restart the window automatically", reset = Some(0)))
      ),
      ROCounterCtrlRegs.seq -> Seq(RegField.r(32, seq, RegFieldDesc("seq", "Windows completed"))),
      ROCounterCtrlRegs.level -> Seq(RegField.r(c.n, vin.asUInt, RegFieldDesc("level", "Synchronized RO outputs")))
    ) ++ result.zipWithIndex.map { case (r, i) =>
      (ROCounterCtrlRegs.count + 4 * i) -> Seq(RegField.r(32, r, RegFieldDesc(s"count$i", s"Edges of RO $i in the last window")))
    }:_*)
  }
}

class TLROCounter(busWidthBytes: Int, params: ROCounterParams, busFreqHz: BigInt)(implicit p: Parameters)
  extends ROCounter(busWidthBytes, params, busFreqHz) with HasTLControlRegMap

case object PeripheryROCounterKey extends Field[Option[ROCounterParams]](None)

trait HasPeripheryROCounter { this: BaseSubsystem =>
  val rocounterNodes = p(PeripheryROCounterKey).map { ps =>
    val name = "rocounter"
    val cbus = locateTLBusWrapper(PBUS)
    val rocounterclock = LazyModule(new ClockSinkDomain(take = None))
    val rocounter = rocounterclock { LazyModule(new TLROCounter(cbus.beatBytes, ps, cbus.dtsFrequency.get)) }
    rocounter.suggestName(name)

    cbus.dtsClk.foreach(_.bind(rocounter.device))
    rocounterclock.clockNode := cbus.fixedClockNode

    cbus.coupleTo(s"device_named_${name}") { bus =>
      (rocounter.controlXing() := TLFragmenter(cbus) := bus)
    }

    rocounter.ioNode.makeSink()
  }

  val rocounterIO = InModuleBody { rocounterNodes.zipWithIndex.map { case(n, i) =>
    n.makeIO()(ValName(s"rocounter_$i")).asInstanceOf[ROCounterIO]
  }}
}

class WithROCounter extends Config((site, here, up ) => {
  case PeripheryROCounterKey => Some(ROCounterParams(0x64005000L))
})
