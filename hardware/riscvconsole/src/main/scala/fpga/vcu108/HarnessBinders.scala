package riscvconsole.fpga.vcu108

import chisel3._
import chisel3.experimental.{Analog, BaseModule, attach}
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.tilelink.{TLBundle, TLEdgeParameters}
import sifive.blocks.devices.uart.UARTPortIO
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}
import chipyard._
import chipyard.harness._
import chipyard.iobinders._
import sifive.fpgashells.shell.{AnalogToUInt, IOPin, ShellSPIPortIO, UIntToAnalog}
import testchipip.serdes.{DecoupledPhitIO, HasClockIn, HasClockOut}

object ExtIntAdptIf {
  def apply: Seq[String] = Seq(
    // NOTE: This is according to the schematic. NOT THE CHIP
    "G6", "G7", "H7", "H8", "G9", "H10", "G10", "H11", // o_data[0..7]
    "G12", "G13", "H13", "H14", "G15", "G16", "H16", "H17", // o_data[8..15]
    "G18", "G19", "H19", "H20", "G21", "G22", "H22", "H23", // o_data[16..23]
    "G24", "G25", "H25", "H26", "G27", "G28", "H28", "H29", // o_data[24..31]

    "D8", "D9", "C10", "C11", "D11", "D12", "C14", "D14", // i_data[0..7]
    "C15", "D15", "D17", "C18", "D18", "C19", "D20", "D21", // i_data[8..15]
    "C22", "C23", "D23", "D24", "C26", "D26", "C27", "D27", // i_data[16..23]
    "E2", "E3", "F4", "F5", "E6", "E7", "F7", "F8", // i_data[24..31]

    "F13", // "rstx"
    "F14", // "csx"
    "F16", // "clk"
    "F19", // "sclk"
  )
  def spi: Seq[String] = Seq(
    // NOTE: This is according to the schematic. NOT THE CHIP
    // CEB, SCK, MOSI, MISO
    "H32", "H34", "H35", "H37"
  )
  def spicfg: Seq[String] = Seq(
    // NOTE: This is according to the schematic. NOT THE CHIP
    "H31", // sf_rst_n
    "G30", // pll_out_div
    "G31", // pll_sel
    "G33", // pll_err
    "G34", // pll_locked
    "G36", // pll_rst_n
    "G37", // pll_mux
  )
}

/*** UART ***/
class WithUART extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vcu108Outer.io_uart_bb.foreach(_.bundle <> port.io)
  }
})

object WithSPISDCardCounter {
  var cnt = 0
}

/*** SPI ***/
class WithSPISDCard extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    val ath = th.wrapper.asInstanceOf[VCU108FPGATestHarness]
    WithSPISDCardCounter.cnt match {
      case 0 =>
        th.vcu108Outer.io_spi_bb.foreach(_.bundle <> port.io)
      case 1 =>
        val harnessIO = IO(new Bundle {
          val spi_clk = Analog(1.W)
          val spi_cs = Analog(1.W)
          val spi_dat = Vec(2, Analog(1.W))
        }).suggestName(s"spi_second")
        port.io.dq.foreach(_.i := false.B)
        UIntToAnalog(port.io.cs(0), harnessIO.spi_cs, true.B)
        UIntToAnalog(port.io.sck, harnessIO.spi_clk, true.B)
        UIntToAnalog(port.io.dq(0).o, harnessIO.spi_dat(0), true.B)
        port.io.dq(1).i := AnalogToUInt(harnessIO.spi_dat(1))
        val elem = VCU108LOC2FMCGroup(FMC1Map.apply, ExtIntAdptIf.spi)
        val extio = elem.GetBindings
        assert(extio.length == 4, s"The requested GPIO does not match ExtInt (${extio.length} == ${port.io.getWidth})")

        val packageIOs = Seq(
          IOPin(harnessIO.spi_cs),
          IOPin(harnessIO.spi_clk),
          IOPin(harnessIO.spi_dat(0)),
          IOPin(harnessIO.spi_dat(1))
        )
        val packagePinsWithPackageIOs = extio zip packageIOs
        packagePinsWithPackageIOs foreach { case (pin, io) =>
          ath.xdc.addPackagePin(io, pin)
          ath.xdc.addIOStandard(io, elem.GetStandard)
        }
    }
    WithSPISDCardCounter.cnt = WithSPISDCardCounter.cnt + 1
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.vcu108Outer.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

class WithJTAG extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: JTAGPort, chipId: Int) => {
    val jtag_io = th.vcu108Outer.jtagPlacedOverlay.overlayOutput.jtag.getWrappedValue
    port.io.TCK := jtag_io.TCK
    port.io.TMS := jtag_io.TMS
    port.io.TDI := jtag_io.TDI
    port.io.reset.foreach(_ := th.referenceReset)
    jtag_io.TDO.data := port.io.TDO
    jtag_io.TDO.driven := true.B
    // ignore srst_n
    jtag_io.srst_n := DontCare

  }
})

class WithGPIOBinder extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: GPIOPort, chipId: Int) => {
    val ath = th.wrapper.asInstanceOf[VCU108FPGATestHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName(s"ext_gpio_${port.pinId}")
    attach(harnessIO, port.io)
    val gpioIO = IOPin(harnessIO)
    val elem = VCU108LOC2FMCGroup(FMC1Map.apply, Seq(ExtIntAdptIf.spicfg(port.pinId)))
    ath.xdc.addPackagePin(gpioIO, elem.GetBindings.head)
    ath.xdc.addIOStandard(gpioIO, elem.GetStandard)
  }
})

object CompareTLEdges {
  implicit class TLEdgeOps(val a: TLEdgeParameters) extends AnyVal {
    def isCompatible(b: TLEdgeParameters): Boolean = {
      a.bundle.addressBits >= b.bundle.addressBits &&
        a.bundle.dataBits == b.bundle.dataBits &&
        a.bundle.sourceBits == b.bundle.sourceBits &&
        // a.bundle.sinkBits == b.bundle.sinkBits && // NOTE: Sink actually doesn't matter. Is always 0 or ignored
        a.bundle.sizeBits == b.bundle.sizeBits &&
        a.bundle.hasBCE == b.bundle.hasBCE
    }

    def asString: String = {
      s"""Bundle:
         |  Address Bits: ${a.bundle.addressBits}
         |  Data Bits: ${a.bundle.dataBits}
         |  Source Bits: ${a.bundle.sourceBits}
         |  Sink Bits: ${a.bundle.sinkBits}
         |  Size Bits: ${a.bundle.sizeBits}
         |  Has BCE: ${a.bundle.hasBCE}
         |""".stripMargin
    }
  }
}

// Using the Terasic FMC2GPIO to cast the Serial TL
class WithSerialTLThroughFMC2GPIO(tieoffs: Option[Seq[Int]] = None, gpioNum: Int = 1, fmc: (String) => String = FMC1Map.apply) extends HarnessBinder({
  case (th: VCU108FPGATestHarnessImp, port: SerialTLPort, chipId: Int) if tieoffs.forall(_.contains(port.portId)) => {
    val ath = th.wrapper.asInstanceOf[VCU108FPGATestHarness]
    val harnessIO = IO(chiselTypeOf(port.io)).suggestName("serial_tl")
    harnessIO <> port.io
    harnessIO match {
      case io: DecoupledPhitIO => {
        val clkIO = io match {
          case io: HasClockOut => IOPin(io.clock_out)
          case io: HasClockIn => IOPin(io.clock_in)
        }
        val isIn = io match {
          case io: HasClockOut => false
          case io: HasClockIn => true
        }
        require((io.out.bits.phit.getWidth+io.in.bits.phit.getWidth+5) < 36, "Not enough pins on a single GPIO")
        // NOTE: This is for keeping the GPIO connector consistency
        // Just plug and play!
        val clkPkgIO = Seq(
          (clkIO, if(isIn) "clock_in" else "clock_out")
        )
        // Create a comm reset
        val serial_tl_reset = IO(if(isIn) Input(Bool()) else Output(Bool())).suggestName(s"serial_tl_reset")
        val serialResetIO = Seq((IOPin(serial_tl_reset), "serial_tl_reset"))
        if(isIn) {
          ath.ndreset.getWrappedValue := serial_tl_reset
        } else {
          serial_tl_reset := ath.pllReset.getWrappedValue
        }
        val outValidReadyPkgIO = Seq(
          (IOPin(io.out.valid), "out_valid"),
          (IOPin(io.out.ready), "out_ready")
        )
        val inValidReadyPkgIO = Seq(
          (IOPin(io.in.valid), "in_valid"),
          (IOPin(io.in.ready), "in_ready")
        )
        val outPhitPkgIO = Seq.tabulate(io.out.bits.phit.getWidth) {i =>
          (IOPin(io.out.bits.phit, i), s"out_bits_phit_${i}")
        }
        val inPhitPkgIO = Seq.tabulate(io.in.bits.phit.getWidth) {i =>
          (IOPin(io.in.bits.phit, i), s"in_bits_phit_${i}")
        }
        val pinsPkgIO = if(isIn) {
          clkPkgIO ++ serialResetIO ++ outValidReadyPkgIO ++ inValidReadyPkgIO ++ outPhitPkgIO ++ inPhitPkgIO
        } else {
          clkPkgIO ++ serialResetIO ++ inValidReadyPkgIO ++ outValidReadyPkgIO ++ inPhitPkgIO ++ outPhitPkgIO
        }

        // Attached to GPIO0. Change the "0" for another GPIO
        val packagePinsWithPackageIOs = pinsPkgIO.zipWithIndex.map{case(io, i) => (gpioNum -> i, io)}
        packagePinsWithPackageIOs foreach { case (pin, (io, name)) => {
          val pkgPin = TerasicFMC2GPIO(pin, fmc)
          ath.xdc.addPackagePin(io, pkgPin)
          ath.xdc.addIOStandard(io, VCU108LOC2FMCGroup(fmc, Seq()).GetStandard)
        }}
        println(s"TLSerial in GPIO ${gpioNum}, FMC 1")
        for(i <- 0 until 18) {
          def getPinName(j: Int): String = {
            packagePinsWithPackageIOs.filter(j == _._1._2).map(_._2._2).headOption.getOrElse(s"unused")
          }
          val l = getPinName(i * 2)
          val r = getPinName(i * 2 + 1)
          println(f"${l}%20s | +  + | ${r}")
          if(i == 4 || i == 12) {
            val vdd = if(i == 4) "+5V" else "+3.3V"
            val gnd = "GND"
            println(f"${vdd}%20s | +  + | ${gnd}")
          }
        }

        if(isIn) {
          ath.sdc.addClock("ser_tl_clock", clkIO, 20)
          ath.sdc.addGroup(pins = Seq(clkIO))
        }
      }
    }
  }
})
