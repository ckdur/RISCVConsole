// See LICENSE.Sifive for license details.
#include <platform.h>
#include <smp.h>
#include "common.h"

  .section .text.init
  .option norvc
  .globl _prog_start
_prog_start:
  j _sd_boot // reset vector (0x10000): boot from SD card

  // JTAG boot entry, fixed at ROM_BASE + 4 (0x10004).
  // After loading the payload to PAYLOAD_DEST through the debugger,
  // resume here so it gets the same a0/a1 as the SD boot path.
  .globl _jtag_boot
_jtag_boot:
  csrr a0, mhartid // hartid for next level bootloader
  la a1, dtb // dtb address for next level bootloader
  li s1, PAYLOAD_DEST
  jr s1

_sd_boot:
  smp_pause(s1, s2)
  li sp, (PAYLOAD_DEST + 0x1fff000)
  call main
  smp_resume(s1, s2)
  csrr a0, mhartid // hartid for next level bootloader
  la a1, dtb // dtb address for next level bootloader
  li s1, PAYLOAD_DEST
  jr s1

  .section .dtb
  .align 3
dtb:
