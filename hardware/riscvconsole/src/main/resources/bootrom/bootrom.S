#define JUMP_ADDR 0x40000000

// Just jump
.section .text.start, "ax", @progbits
.globl _start
_start:
  csrr a0, mhartid    // hartid for next level bootloader
  la a1, _dtb         // dtb address for next level bootloader
  li s0, JUMP_ADDR    // Load
  jr s0

// Just jump
.section .text.hang, "ax", @progbits
.globl _hang
_hang: // reset vector
  j _start

// Just jump
.section .text.hang80, "ax", @progbits
.globl _hang80
_hang80:
  j _start

.align 3
_dtb:

