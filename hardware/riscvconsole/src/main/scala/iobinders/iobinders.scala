package riscvconsole.iobinders

import chipyard.iobinders._
import chisel3._
import org.chipsalliance.diplomacy.lazymodule._
import sifive.blocks.devices.spi.{HasPeripherySPIFlash, PeripherySPIFlashKey}

class WithSPIFlashIOPunchthrough extends OverrideLazyIOBinder({
  (system: HasPeripherySPIFlash) => {
    InModuleBody {
      val qspi = system.qspi
      val ports = qspi.zipWithIndex.map({ case (s, i) =>
        val io_spi = IO(s.cloneType).suggestName(s"fspi_$i")
        io_spi <> s
        SPIPort(() => io_spi) // Treating it as a regular SPI
      })
      (ports, Nil)
    }
  }
})
