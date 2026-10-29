package riscvconsole.iobinders

import chipyard.iobinders._
import chisel3._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import riscvconsole.devices.adcfifo.HasPeripheryADCFIFO
import riscvconsole.devices.rocounter.HasPeripheryROCounter
import sifive.blocks.devices.spi.{HasPeripherySPIFlash, PeripherySPIFlashKey, SPIPortIO}

class WithSPIFlashIOPunchthrough extends OverrideLazyIOBinder({
  (system: HasPeripherySPIFlash) => {
    InModuleBody {
      val qspi = system.qspi
      val ports = qspi.zipWithIndex.map({ case (s: SPIPortIO, i) =>
        val io_spi = IO(s.cloneType).suggestName(s"fspi_$i")
        io_spi <> s
        SPIPort(() => io_spi) // Treating it as a regular SPI
      })
      (ports, Nil)
    }
  }
})

// Exports the debug module ndreset (dmcontrol.ndmreset) so the harness can reset the system with it.
// Composes with WithDebugIOCells, which needs externalReset = true so the JTAG DTM and the
// debug module outer side are not reset by ndreset itself
class WithDebugNDResetPunchthrough extends ComposeLazyIOBinder({
  (system: HasPeripheryDebug) => {
    InModuleBody {
      val ports = system.debug.getWrappedValue.map { debug =>
        val io_ndreset = IO(Output(Bool())).suggestName("ndreset")
        io_ndreset := debug.ndreset
        DebugResetPort(() => io_ndreset)
      }
      (ports.toSeq, Nil)
    }
  }
})

class WithADCFIFOIOPunchthrough extends OverrideIOBinder({
  (system: HasPeripheryADCFIFO) => {
    val ports = system.adcfifoIO.zipWithIndex.map { case (s, i) =>
      val io_adcfifo = IO(s.cloneType).suggestName("adcfifo")
      io_adcfifo <> s
      ADCFIFOPort(() => io_adcfifo)
    }
    (ports.toSeq, Nil)
  }
})

class WithROCounterIOPunchthrough extends OverrideIOBinder({
  (system: HasPeripheryROCounter) => {
    val ports = system.rocounterIO.zipWithIndex.map { case (s, i) =>
      val io_rocounter = IO(s.cloneType).suggestName("rocounter")
      io_rocounter <> s
      ROCounterPort(() => io_rocounter)
    }
    (ports.toSeq, Nil)
  }
})
