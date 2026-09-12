package riscvconsole.shell.latticeLib

import chisel3._
import chisel3.experimental._
import chisel3.util.HasBlackBoxInline
import riscvconsole.devices.sdram.SDRAMIf
import sifive.blocks.devices.pinctrl._

class BB extends BlackBox{
  val io = IO(new Bundle{
    val B = Analog(1.W)
    val T = Input(Bool())
    val I = Input(Bool())
    val O = Output(Bool())
  })

  def asInput() : Bool = {
    io.T := true.B
    io.I := false.B
    io.O
  }

  def asOutput(o: Bool) : Unit = {
    io.T := false.B
    io.I := o
  }

  def fromBase(e: BasePin) : Unit = {
    io.T := !e.o.oe
    io.I := e.o.oval
    e.i.ival := io.O
  }

  def attachTo(analog: Analog) : Unit = {
    attach(analog, io.B)
  }
}

object BB {
  def apply : BB = {
    Module(new BB)
  }

  def apply(analog: Analog) : Bool = {
    val m = Module(new BB)
    m.attachTo(analog)
    m.asInput()
  }

  def apply(analog: Analog, i: Bool) : Unit = {
    val m = Module(new BB)
    m.attachTo(analog)
    m.asOutput(i)
  }

  def apply(analog: Analog, e: BasePin) : Unit = {
    val m = Module(new BB)
    m.attachTo(analog)
    m.fromBase(e)
  }
}

case class ecp5pllConfig
(
  in_hz: BigInt = 25000000,
  out0_hz: BigInt = 0,
  out0_deg: BigInt = 0,
  out0_tol_hz: BigInt = 0,
  out1_hz: BigInt = 0,
  out1_deg: BigInt = 0,
  out1_tol_hz: BigInt = 0,
  out2_hz: BigInt = 0,
  out2_deg: BigInt = 0,
  out2_tol_hz: BigInt = 0,
  out3_hz: BigInt = 0,
  out3_deg: BigInt = 0,
  out3_tol_hz: BigInt = 0,
  reset_en: Boolean = false,
  standby_en: Boolean = false,
  dynamic_en: Boolean = false
)

class WRAP_EHXPLLL(params: Map[String, Param], cfg: ecp5pllConfig) extends BlackBox(params) with HasBlackBoxInline {
  val io = IO(new Bundle {
    val RST = Input(Bool())
    val STDBY = Input(Bool())
    val CLKI = Input(Clock())
    val CLKOPA = Output(Clock())
    val CLKOS = Output(Clock())
    val CLKOS2 = Output(Clock())
    val CLKOS3 = Output(Clock())
    val CLKINTFB = Output(Clock())
    val PHASESEL1 = Input(Bool())
    val PHASESEL0 = Input(Bool())
    val PHASEDIR = Input(Bool())
    val PHASESTEP = Input(Bool())
    val PHASELOADREG = Input(Bool())
    val PLLWAKESYNC = Input(Bool())
    val ENCLKOP = Input(Bool())
    val ENCLKOS = Input(Bool())
    val ENCLKOS2 = Input(Bool())
    val ENCLKOS3 = Input(Bool())
    val LOCK = Output(Bool())
  })
  val parStr = params.map{
    case(str:String, par: IntParam) =>
      s"parameter integer ${str} = ${par.value}"
    case(str:String, par: StringParam) =>
      s"""parameter ${str} = "${par.value}""""
    case(str:String, par: DoubleParam) =>
      s"parameter double ${str} = ${par.value}"
    case _ => ""
  }.mkString(",\n    ")
  val parInstStr = params.map {
    case (str: String, par: Param) =>
      s".${str}(${str})"
    case _ => ""
  }.mkString(", ")
  setInline("WRAP_EHXPLLL.v",
    s"""module WRAP_EHXPLLL #(
       |    ${parStr}
       |)(
       |    input RST,
       |    input STDBY,
       |    input CLKI,
       |    output CLKOPA,
       |    output CLKOS,
       |    output CLKOS2,
       |    output CLKOS3,
       |    output CLKINTFB,
       |    input PHASESEL1,
       |    input PHASESEL0,
       |    input PHASEDIR,
       |    input PHASESTEP,
       |    input PHASELOADREG,
       |    input PLLWAKESYNC,
       |    input ENCLKOP,
       |    input ENCLKOS,
       |    input ENCLKOS2,
       |    input ENCLKOS3,
       |    input LOCK
       |);
       |  wire CLKOP;
       |  (* FREQUENCY_PIN_CLKI="${cfg.in_hz.doubleValue()/1000000.0}" *)
       |  (* FREQUENCY_PIN_CLKOP="${cfg.out0_hz.doubleValue()/1000000.0}" *)
       |  (* FREQUENCY_PIN_CLKOS="${cfg.out1_hz.doubleValue()/1000000.0}" *)
       |  (* FREQUENCY_PIN_CLKOS2="${cfg.out2_hz.doubleValue()/1000000.0}" *)
       |  (* FREQUENCY_PIN_CLKOS3="${cfg.out3_hz.doubleValue()/1000000.0}" *)
       |  (* ICP_CURRENT="12" *) (* LPF_RESISTOR="8" *) (* MFG_ENABLE_FILTEROPAMP="1" *) (* MFG_GMCREF_SEL="2" *)
       |  EHXPLLL
       |  #(
       |    ${parInstStr}
       |  )
       |  pll_inst
       |  (
       |    .RST(RST),
       |    .STDBY(STDBY),
       |    .CLKI(CLKI),
       |    .CLKOP(CLKOP),
       |    .CLKOS(CLKOS),
       |    .CLKOS2(CLKOS2),
       |    .CLKOS3(CLKOS3),
       |    .CLKFB(CLKOP),
       |    .CLKINTFB(CLKINTFB),
       |    .PHASESEL1(PHASESEL1),
       |    .PHASESEL0(PHASESEL0),
       |    .PHASEDIR(PHASEDIR),
       |    .PHASESTEP(PHASESTEP),
       |    .PHASELOADREG(PHASELOADREG),
       |    .PLLWAKESYNC(PLLWAKESYNC),
       |    .ENCLKOP(ENCLKOP),
       |    .ENCLKOS(ENCLKOS),
       |    .ENCLKOS2(ENCLKOS2),
       |    .ENCLKOS3(ENCLKOS3),
       |    .LOCK(LOCK)
       |  );
       |  assign CLKOPA = CLKOP;
       |endmodule
       |""".stripMargin)
}

class ecp5pll(params: Map[String, Param], cfg: ecp5pllConfig) extends Module {
  val io = IO(new Bundle{
    val clk_o = Output(Vec(4, Clock()))
    val standby = Input(Bool())
    val phasesel = Input(UInt(2.W))
    val phasedir = Input(Bool())
    val phasestep = Input(Bool())
    val phaseloadreg = Input(Bool())
    val locked = Output(Bool())
  })
  val PHASESEL_HW = WireInit(io.phasesel - 1.U(2.W))
  val bb = Module(new WRAP_EHXPLLL(params, cfg))
  bb.io.RST := false.B
  bb.io.STDBY := false.B
  bb.io.CLKI := clock
  io.clk_o(0) := bb.io.CLKOPA
  io.clk_o(1) := bb.io.CLKOS
  io.clk_o(2) := bb.io.CLKOS2
  io.clk_o(3) := bb.io.CLKOS3
  bb.io.PHASESEL1 := PHASESEL_HW(1)
  bb.io.PHASESEL0 := PHASESEL_HW(0)
  bb.io.PHASEDIR := io.phasedir
  bb.io.PHASESTEP := io.phasestep
  bb.io.PHASELOADREG := io.phaseloadreg
  bb.io.PLLWAKESYNC := false.B
  bb.io.ENCLKOP := false.B
  bb.io.ENCLKOS := false.B
  bb.io.ENCLKOS2 := false.B
  bb.io.ENCLKOS3 := false.B
  io.locked := bb.io.LOCK
}

object ecp5pll {
  val PFD_MIN: BigInt = 3125000
  val PFD_MAX: BigInt = 400000000
  val VCO_MIN: BigInt = 400000000
  val VCO_MAX: BigInt = 800000000
  val VCO_OPTIMAL: BigInt = (VCO_MIN+VCO_MAX)/2

  def F_ecp5pll(cfg: ecp5pllConfig) : (BigInt, BigInt, BigInt) = {
    import cfg._

    var input_div: BigInt = 0
    var input_div_min: BigInt = 0
    var input_div_max: BigInt = 0
    var output_div: BigInt = 0
    var output_div_min: BigInt = 0
    var output_div_max: BigInt = 0
    var feedback_div: BigInt = 0
    var feedback_div_min: BigInt = 0
    var feedback_div_max: BigInt = 0
    var fvco: BigInt = 0
    var fout: BigInt = 0
    var error: BigInt = 0
    var error_prev: BigInt = 0
    var params_fvco: BigInt = 0
    var div1: BigInt = 0
    var div2: BigInt = 0
    var div3: BigInt = 0

    var params_refclk_div: BigInt = 0
    var params_feedback_div: BigInt = 0
    var params_output_div: BigInt = 0

    params_fvco = 0
    error_prev = 999999999
    input_div_min = in_hz / PFD_MAX
    if (input_div_min < 1) input_div_min = 1
    input_div_max = in_hz / PFD_MIN
    if (input_div_max > 128) input_div_max = 128
    for(input_div <- input_div_min to input_div_max) {
      if (out0_hz / 1000000 * input_div < 2000) feedback_div = out0_hz * input_div / in_hz
      else feedback_div = out0_hz / in_hz * input_div
      feedback_div_min = feedback_div
      feedback_div_max = feedback_div + 1
      if (feedback_div_min < 1) feedback_div_min = 1
      if (feedback_div_max > 80) feedback_div_max = 80
      for(feedback_div <- feedback_div_min to feedback_div_max) {
        output_div_min = (VCO_MIN / feedback_div) / (in_hz / input_div)
        if (output_div_min < 1) output_div_min = 1
        output_div_max = (VCO_MAX / feedback_div) / (in_hz / input_div)
        if (output_div_max > 128) output_div_max = 128
        fout = in_hz * feedback_div / input_div
        for(output_div <- output_div_min to output_div_max) {
          fvco = fout * output_div;
          error = (fout-out0_hz).abs +
            (if(out1_hz > 0) (fvco/(if(fvco >= out1_hz) fvco/out1_hz else 1)-out1_hz).abs else 0) +
            (if(out2_hz > 0) (fvco/(if(fvco >= out2_hz) fvco/out2_hz else 1)-out2_hz).abs else 0) +
            (if(out3_hz > 0) (fvco/(if(fvco >= out3_hz) fvco/out3_hz else 1)-out3_hz).abs else 0)
          if( error < error_prev
            || (error == error_prev && (fvco-VCO_OPTIMAL).abs < (params_fvco-VCO_OPTIMAL).abs) ) {
            error_prev = error
            params_refclk_div = input_div
            params_feedback_div = feedback_div
            params_output_div = output_div
            params_fvco = fvco
          }
        }
      }
    }
    (params_refclk_div, params_feedback_div, params_output_div)
  }

  def F_primary_phase(output_div: BigInt, deg: BigInt) : BigInt = {
    val phase_compensation: BigInt = (output_div+1)/2*8-8+output_div/2*8 // output_div/2*8 = 180 deg shift
    var phase_count_x8: BigInt  = phase_compensation + 8 * output_div * deg / 360
    if(phase_count_x8 > 1023)
      phase_count_x8 = phase_count_x8 % (output_div*8) // wraparound 360 deg
    phase_count_x8;
  }
  def F_secondary_divisor(sfreq: BigInt, params_fvco: BigInt) : BigInt = {
    if(sfreq > 0 && params_fvco >= sfreq) params_fvco/sfreq else 1
  }
  def F_secondary_phase(sfreq: BigInt, sphase: BigInt, params_fvco: BigInt) : BigInt = {
    var div: BigInt = 0
    var freq: BigInt = 0
    var phase_compensation: BigInt = 0
    var phase_count_x8: BigInt = 0

    if(sfreq > 0) {
      div = 1
      if (params_fvco >= sfreq) div = params_fvco / sfreq
      freq = params_fvco / div
      phase_compensation = div * 8 - 8
      phase_count_x8 = phase_compensation + 8 * div * sphase / 360
      if (phase_count_x8 > 1023) phase_count_x8 = phase_count_x8 % (div * 8) // wraparound 360 deg
    }
    phase_count_x8
  }
  def apply(cfg: ecp5pllConfig): ecp5pll = {
    import cfg._
    val (params_refclk_div, params_feedback_div, params_output_div) = F_ecp5pll(cfg)
    val params_fout = in_hz * params_feedback_div / params_refclk_div
    val params_fvco = params_fout * params_output_div
    val params_primary_cphase = F_primary_phase(params_output_div, out0_deg) / 8
    val params_primary_fphase = F_primary_phase(params_output_div, out0_deg) % 8

    val params_secondary1_div = F_secondary_divisor(out1_hz, params_fvco)
    val params_secondary1_cphase = F_secondary_phase(out1_hz, out1_deg, params_fvco) / 8
    val params_secondary1_fphase = F_secondary_phase(out1_hz, out1_deg, params_fvco) % 8
    val params_secondary2_div = F_secondary_divisor(out2_hz, params_fvco)
    val params_secondary2_cphase = F_secondary_phase(out2_hz, out2_deg, params_fvco) / 8
    val params_secondary2_fphase = F_secondary_phase(out2_hz, out2_deg, params_fvco) % 8
    val params_secondary3_div = F_secondary_divisor(out3_hz, params_fvco)
    val params_secondary3_cphase = F_secondary_phase(out3_hz, out3_deg, params_fvco) / 8
    val params_secondary3_fphase = F_secondary_phase(out3_hz, out3_deg, params_fvco) % 8

    // check if generated frequencies are out of range
    val error_out0_hz = (out0_hz - params_fout).abs > out0_tol_hz
    val error_out1_hz = if (out1_hz > 0) (out1_hz - params_fvco / params_secondary1_div).abs > out1_tol_hz
    else false
    val error_out2_hz = if (out2_hz > 0) (out2_hz - params_fvco / params_secondary2_div).abs > out2_tol_hz
    else false
    val error_out3_hz = if (out3_hz > 0) (out3_hz - params_fvco / params_secondary3_div).abs > out3_tol_hz
    else false
    // diamond: won't compile this, comment it out. Workaround follows using division by zero

    require(!error_out0_hz, s"out0_hz (${out0_hz}) tolerance exceeds out0_tol_hz (${out0_tol_hz})")
    require(!error_out1_hz, s"out1_hz (${out1_hz}) tolerance exceeds out1_tol_hz (${out1_tol_hz})")
    require(!error_out2_hz, s"out2_hz (${out2_hz}) tolerance exceeds out2_tol_hz (${out2_tol_hz})")
    require(!error_out3_hz, s"out3_hz (${out3_hz}) tolerance exceeds out3_tol_hz (${out3_tol_hz})")

    val params = Map(
      "CLKI_DIV" -> IntParam(params_refclk_div),
      "CLKFB_DIV" -> IntParam(params_feedback_div),
      "FEEDBK_PATH" -> StringParam("CLKOP"),

      "OUTDIVIDER_MUXA" -> StringParam("DIVA"),
      "CLKOP_ENABLE" -> StringParam("ENABLED"),
      "CLKOP_DIV" -> IntParam(params_output_div),
      "CLKOP_CPHASE" -> IntParam(params_primary_cphase),
      "CLKOP_FPHASE" -> IntParam(params_primary_fphase),

      "OUTDIVIDER_MUXB" -> StringParam("DIVB"),
      "CLKOS_ENABLE" -> StringParam(if(out1_hz > 0) "ENABLED" else "DISABLED"),
      "CLKOS_DIV" -> IntParam(params_secondary1_div),
      "CLKOS_CPHASE" -> IntParam(params_secondary1_cphase),
      "CLKOS_FPHASE" -> IntParam(params_secondary1_fphase),

      "OUTDIVIDER_MUXC" -> StringParam("DIVC"),
      "CLKOS2_ENABLE" -> StringParam(if(out2_hz > 0) "ENABLED" else "DISABLED"),
      "CLKOS2_DIV" -> IntParam(params_secondary2_div),
      "CLKOS2_CPHASE" -> IntParam(params_secondary2_cphase),
      "CLKOS2_FPHASE" -> IntParam(params_secondary2_fphase),

      "OUTDIVIDER_MUXD" -> StringParam("DIVD"),
      "CLKOS3_ENABLE" -> StringParam(if(out3_hz > 0) "ENABLED" else "DISABLED"),
      "CLKOS3_DIV" -> IntParam(params_secondary3_div),
      "CLKOS3_CPHASE" -> IntParam(params_secondary3_cphase),
      "CLKOS3_FPHASE" -> IntParam(params_secondary3_fphase),

      "INTFB_WAKE" -> StringParam("DISABLED"),
      "STDBY_ENABLE" -> StringParam(if(standby_en) "ENABLED" else "DISABLED"),
      "PLLRST_ENA" -> StringParam(if(reset_en) "ENABLED" else "DISABLED"),
      "DPHASE_SOURCE" -> StringParam(if(dynamic_en) "ENABLED" else "DISABLED"),
      "PLL_LOCK_MODE" -> IntParam(0)
    )
    Module(new ecp5pll(params, cfg))
  }
}

class ULX3SSDRAM extends Bundle {
  val sdram_clk_o = Output(Bool())
  val sdram_cke_o = Output(Bool())
  val sdram_cs_o = Output(Bool())
  val sdram_ras_o = Output(Bool())
  val sdram_cas_o = Output(Bool())
  val sdram_we_o = Output(Bool())
  val sdram_dqm_o = Output(UInt(2.W))
  val sdram_addr_o = Output(UInt(13.W))
  val sdram_ba_o = Output(UInt(2.W))
  val sdram_data_io = Vec(16, Analog(1.W))
  def from_SDRAMIf(io: SDRAMIf) = {
    sdram_clk_o := io.sdram_clk_o
    sdram_cke_o := io.sdram_cke_o
    sdram_cs_o := io.sdram_cs_o
    sdram_ras_o := io.sdram_ras_o
    sdram_cas_o := io.sdram_cas_o
    sdram_we_o := io.sdram_we_o
    sdram_dqm_o := io.sdram_dqm_o
    sdram_addr_o := io.sdram_addr_o
    sdram_ba_o := io.sdram_ba_o
    io.sdram_data_i := VecInit((io.sdram_data_o.asBools() zip sdram_data_io).map{
      case (o, an) =>
        val b = Module(new BB)
        b.io.T := !io.sdram_drive_o
        b.io.I := o
        attach(b.io.B, an)
        b.io.O
    }).asUInt()
  }
}
