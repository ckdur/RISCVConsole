package riscvconsole.fpga.tr4

import chisel3._
import chipyard.harness._
import chipyard.iobinders._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.diplomacy.lazymodule.LazyRawModuleImp
import org.chipsalliance.diplomacy.nodes.HeterogeneousBag
import sifive.fpgashells.shell.IOPin
import sifive.fpgashells.shell.altera._
import testchipip.serdes._

class WithTR4UARTTSI(uartBaudRate: BigInt = 115200) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTTSIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    ath.io_uart_bb.bundle <> port.io.uart
    ath.other_leds(1) := port.io.dropped
    ath.other_leds(2) := port.io.tsi2tl_state(0)
    // TODO: Do not exist
    //ath.other_leds(3) := port.io.tsi2tl_state(1)
    //ath.other_leds(4) := port.io.tsi2tl_state(2)
    //ath.other_leds(5) := port.io.tsi2tl_state(3)
  }
})

object WithSPISDCardCounter {
  var cnt = 0
}

class WithTR4SPIBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SPIPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    WithSPISDCardCounter.cnt match {
      case 0 =>
        ath.io_qspi_bb.foreach(_.bundle <> port.io)
    }
    WithSPISDCardCounter.cnt = WithSPISDCardCounter.cnt + 1
  }
})

class WithTR4SPIFlashBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SPIFlashPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    ath.io_qspi_bb(port.spiId).bundle <> port.io
  }
})

class WithTR4UARTBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: UARTPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    ath.io_uart_bb.bundle <> port.io
  }
})

class WithTR4GPIOBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: GPIOPinsPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    ath.io_gpio_bb(port.gpioId).bundle <> port.io
  }
})

class WithTR4JTAGBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: JTAGPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    // port.io.reset.foreach(_ := false.B) // NOTE: Configs with TR4 should not have external reset for jtag
    port.io.TCK := ath.jtagOverlay.getWrappedValue.TCK
    port.io.TMS := ath.jtagOverlay.getWrappedValue.TMS
    port.io.TDI := ath.jtagOverlay.getWrappedValue.TDI
    ath.jtagOverlay.getWrappedValue.TDO.data := port.io.TDO
    ath.jtagOverlay.getWrappedValue.TDO.driven := true.B
    ath.ndreset.getWrappedValue := /*port.io.ndreset || */!ath.jtagOverlay.getWrappedValue.srst_n
    // ath.jtagOverlay.getWrappedValue.srst_n is ignored
  }
})

class WithTR4DDRMemBinder extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: TLMemPort, chipId: Int) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
    val bundles = ath.ddrClient.get.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io

    // Some status reporting
    ath.other_leds(1) := ath.ddrOverlays.get.asInstanceOf[DDRTR4PlacedOverlay].getStatus.mem_status_local_cal_success
    ath.other_leds(2) := ath.ddrOverlays.get.asInstanceOf[DDRTR4PlacedOverlay].getStatus.mem_status_local_init_done
  }
})

class WithSerialTLThroughGPIO(tieoffs: Option[Seq[Int]] = None, gpioNum: Int = 0, usehsmc: Option[HasAlteraHSMCLocs] = Some(new TR4HSMCB)) extends HarnessBinder({
  case (th: HasHarnessInstantiators, port: SerialTLPort, chipId: Int) if tieoffs.forall(_.contains(port.portId)) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[TR4Harness]
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
          serial_tl_reset := ath.resetPin.getWrappedValue
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
          val obj = usehsmc.map{hsmc =>
            TR4HSMC2GPIOGroup(() => hsmc, Seq(pin))
          }.getOrElse(TR4GPIOGroup(Seq(pin))) // Conversion from map (x -> y) to the actual pin
          ath.io_tcl.addPackagePin(io, obj.GetBindings.head)
          ath.io_tcl.addIOStandard(io, obj.GetStandard)
        }}
        println(s"TLSerial in GPIO ${gpioNum}, HSMC ${usehsmc.map(_.getClass.getSimpleName).getOrElse("None")}")
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
