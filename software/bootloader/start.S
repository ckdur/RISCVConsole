// Comment

.section .init
.globl _start
_start:
  .cfi_startproc
	.cfi_undefined ra
.option push
.option norelax
  la gp, __global_pointer$ // auipc + (maybe) addi
.option pop
  la sp, _sp               // auipc + (maybe) addi
  
  // TODO: Do the load for C variables
  
  auipc ra, 0
  addi sp, sp, -16
#if __riscv_xlen == 32
  sw ra, 8(sp)
#else
  sd ra, 8(sp)
#endif
  
  li a0, 0x10001000 // Load the offset of the GPIO
  li a1, 0xFFFFFFFF // Load this constant
  sw a1, 0x08(a0) // Put all enables in 1
1:
  sw a1, 0x0C(a0) // Put all the GPIOs is a1 state
  xori a1, a1, -1 // 0xFFF (imm) arith, sign will be replicated in all (negate everything)
  li a2, 100000
  2: 
    addi a2, a2, -1
    bne zero, a2, 2b
  j 1b
  
1:
  j 1b
  
  .cfi_endproc
