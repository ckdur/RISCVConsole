#include <inttypes.h>
#include "print.h"
#include "platform.h"
#include "i2c.h"
#include "time.h"

volatile uint32_t* gpio = (volatile uint32_t*)0x10001000;

#define CODEC_DATA(addr, data, ref) ref[0] = ((((addr) >> 9) & 0x7F) << 1) | (((data) >> 8) & 0x1); ref[1] = (data) & 0xFF;

void codec_init() {
  i2c0_init((void*)I2C_CTRL_ADDR, 1000000, METAL_I2C_MASTER);
  char buf[2];
  
  CODEC_DATA(0x06, 0, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, 0x1A, 2, buf, METAL_I2C_STOP_ENABLE);
  CODEC_DATA(0x07, 2 << 2 | 3 << 0, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, 0x1A, 2, buf, METAL_I2C_STOP_ENABLE);
  CODEC_DATA(0x00, 0 << 8 | 0 << 7 | 0x17, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, 0x1A, 2, buf, METAL_I2C_STOP_ENABLE);
  CODEC_DATA(0x01, 0 << 8 | 0 << 7 | 0x17, buf)
  i2c0_write((void*)I2C_CTRL_ADDR, 0x1A, 2, buf, METAL_I2C_STOP_ENABLE);
}

int main(int argc, int argv) {
  gpio[2] = 0xF; // 0x8 output_en (XXXXXXXXXX)
  gpio[3] = 0x0; // 0xC output    (XXXXXXXXXX)
  int stat = 0;
  print_init();
  print_str("Hello! PCM Demo\n");
  codec_init();
  int j;
  uint32_t timeout;
  while(1) {
    timeout = metal_time() + 1;
    while(!(CODEC_REG(CODEC_REG_STATUS) & CODEC_STAT_AUD_IN_AVAIL)) {
      if(metal_time() > timeout) {
        print_str("Timeout\n");
        break;
      }
    }
    CODEC_REG(CODEC_REG_STATUS) = CODEC_CTRL_READ_AUD_IN;
    j = CODEC_REG(CODEC_REG_OUT_L);
  	print_hex(j, 8);
  	print_str("\n");
    
  	stat = ~stat;
  	gpio[3] = stat & 0xF;
  }
	return 0;
}
