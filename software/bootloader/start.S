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
  
  /* Load data section */
	la a0, _data_lma
	la a1, _data
	la a2, _edata
	bgeu a1, a2, 2f
1:
	lw t0, (a0)
	sw t0, (a1)
	addi a0, a0, 4
	addi a1, a1, 4
	bltu a1, a2, 1b
2:

	/* Clear bss section */
	la a0, __bss_start
	la a1, _end
	bgeu a0, a1, 2f
1:
	sw zero, (a0)
	addi a0, a0, 4
	bltu a0, a1, 1b
2:

  /* Call global constructors */
	//la a0, __libc_fini_array
	//call atexit
	//call __libc_init_array
  
  auipc ra, 0
  addi sp, sp, -16
#if __riscv_xlen == 32
  sw ra, 8(sp)
#else
  sd ra, 8(sp)
#endif
  
  /* argc = argv = 0 */
	li a0, 0
	li a1, 0
	call main
	//tail exit
1:
  j 1b
  
  .cfi_endproc
