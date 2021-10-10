#include "encoding.h"
#include "platform.h"
#include "time.h"

uint32_t metal_time(void) {
    return (uint32_t)(rdtime() / TIMEBASE);
}
