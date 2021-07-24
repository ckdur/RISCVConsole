// See LICENSE for license details.
#include <platform.h>
#include <smp.h>
#include "common.h"

  .section .text.init
  .option norvc
  .globl _prog_start
_prog_start:
  csrr a0, mhartid
  li a1, NUM_CORES
1:bge a0, a1, 1b     // If the hart ID is greater or equals the NUMCORES, that means is a ghost
  smp_pause(s1, s2)
  la t0, trap_entry
  csrw mtvec, t0		// 0x800004a in fsbl-1.0
  li sp, (MEMORY_MEM2_ADDR + MEMORY_MEM2_SIZE)
  call main
  smp_resume(s1, s2)
  csrr a0, mhartid
  la a1, dtb
  li s1, PAYLOAD_JUMP
  jr s1

trap_entry:
  j trap_entry
  .section .rodata
dtb:
  .incbin DEVICE_TREE
