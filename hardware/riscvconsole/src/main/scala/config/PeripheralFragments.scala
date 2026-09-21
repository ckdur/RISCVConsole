package riscvconsole.config

import sys.process._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import org.chipsalliance.cde.config._

class WithJustJumpBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val make = s"make -C generators/riscvconsole/src/main/resources/bootrom img"
    require(make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = SystemFileName(s"./generators/riscvconsole/src/main/resources/sdboot/bootrom.rv${site(MaxXLen)}.img"))
  }
})

class WithSDBootBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val make = s"make -C generators/riscvconsole/src/main/resources/sdboot bin"
    require(make.! == 0, "Failed to build sdboot")
    p.copy(hang = 0x10000, contentFileName = SystemFileName(s"./generators/riscvconsole/src/main/resources/sdboot/build/sdboot.rv${site(MaxXLen)}.bin"))
  }
})
