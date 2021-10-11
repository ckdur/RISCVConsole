#include "encoding.h"
#include "platform.h"
#include "time.h"

uint32_t metal_time(void) {

  uint32_t mtime_hi_0;
  uint32_t mtime_lo;
  uint32_t mtime_hi_1;
  do {
    mtime_hi_0 = CLINT_REG(CLINT_MTIME + 4);
    mtime_lo   = CLINT_REG(CLINT_MTIME + 0);
    mtime_hi_1 = CLINT_REG(CLINT_MTIME + 4);
  } while (mtime_hi_0 != mtime_hi_1);

  //uint64_t time = (((uint64_t) mtime_hi_1 << 32) | ((uint64_t) mtime_lo));
  
  return (uint32_t)(mtime_lo / TIMEBASE);
}

uint64_t metal_utime(void) {

  uint32_t mtime_hi_0;
  uint32_t mtime_lo;
  uint32_t mtime_hi_1;
  do {
    mtime_hi_0 = CLINT_REG(CLINT_MTIME + 4);
    mtime_lo   = CLINT_REG(CLINT_MTIME + 0);
    mtime_hi_1 = CLINT_REG(CLINT_MTIME + 4);
  } while (mtime_hi_0 != mtime_hi_1);

  return (((uint64_t) mtime_hi_1 << 32) | ((uint64_t) mtime_lo));
}
