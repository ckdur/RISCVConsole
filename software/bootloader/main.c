#include <inttypes.h>
#include "print.h"
#include "platform.h"
#include "i2c.h"
#include "time.h"

volatile uint32_t* gpio = (volatile uint32_t*)0x10001000;

#define CODEC_DATA(addr, data, ref) ref[0] = ((((addr) & 0x7F) << 1) | (((data) >> 8) & 0x1)); ref[1] = ((data) & 0xFF);
#define CODEC_ADDR 0x1A

uint8_t reverseBits(uint8_t num)
{
    unsigned int count = sizeof(num) * 8 - 1;
    uint8_t reverse_num = num;
      
    num >>= 1; 
    while(num)
    {
       reverse_num <<= 1;       
       reverse_num |= num & 1;
       num >>= 1;
       count--;
    }
    reverse_num <<= count;
    return reverse_num;
}

void codec_init() {
  i2c0_init((void*)I2C_CTRL_ADDR, 1000000, METAL_I2C_MASTER);
  char buf[2];
  
  // Do a software reset
  CODEC_DATA(0x0F, 0, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // Enable all power, except OUT (R6 D4)
  CODEC_DATA(0x06, 1 << 4, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // Configuration
  // 1. Digital Audio IF. Data word length = 24 bits (1,0 in R7 D3,D2). Format = I2S mode (10 in R7, D1,D0)
  // 1b. Enable Master Mode in 1 also. (1 in R7, D6)
  CODEC_DATA(0x07, 1 << 6 | 2 << 2 | 2 << 0, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // 2. Left ADC volume. No BOTH, No LinMute, Vol = 0dB (010111 in R0 D5-0)
  CODEC_DATA(0x00, 0 << 8 | 0 << 7 | 0x17, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // 3. Right ADC volume. No BOTH, No LinMute, Vol = 0dB (010111 in R0 D5-0)
  CODEC_DATA(0x01, 0 << 8 | 0 << 7 | 0x17, buf)
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // 4. Enable USB mode (1 in R8 D0). Sampling will be 96Khz (SR 0111 in R8 D5-2) Dividers in zero are ok.
  CODEC_DATA(0x08, 7 << 2 | 1, buf)
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // 5. Sample from mic (INSEL 1 R4 D2). No mute, no boost, no sidetone. no DACSEL, and no Bypass
  CODEC_DATA(0x04, 1 << 2, buf)
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // Wait 34ms
  uint64_t dest = metal_utime() + 34000;
  while(metal_utime() <= dest);
  // Active (R9 D0 in 1)
  CODEC_DATA(0x09, 1, buf)
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
  // Enable all power, 0
  CODEC_DATA(0x06, 0, buf) 
  i2c0_write((void*)I2C_CTRL_ADDR, CODEC_ADDR, 2, buf, METAL_I2C_STOP_ENABLE);
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
    CODEC_REG(CODEC_REG_CTRL) = CODEC_CTRL_READ_AUD_IN;
    j = CODEC_REG(CODEC_REG_IN_L);
  	print_hex(j, 8);
  	print_str("\n");
    
  	stat = ~stat;
  	gpio[3] = stat & 0xF;
  }
	return 0;
}
