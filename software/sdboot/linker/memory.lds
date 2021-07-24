MEMORY
{
  debug_ctrl (rwx) : ORIGIN = 0x0, LENGTH = 0x1000
  error_mem (rw) : ORIGIN = 0x3000, LENGTH = 0x1000
  test_ctrl (rw) : ORIGIN = 0x4000, LENGTH = 0x1000
  maskrom_mem (rx) : ORIGIN = 0x20000000, LENGTH = 0x4000
  clint_ctrl (rw) : ORIGIN = 0x2000000, LENGTH = 0x10000
  plic_ctrl (rw) : ORIGIN = 0xc000000, LENGTH = 0x4000000
  memory_mem (rwx) : ORIGIN = 0x82200000, LENGTH = 0x4000
}
