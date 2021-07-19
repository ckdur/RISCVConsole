#define GPIO_ADDR 0x10001000
#include <inttypes.h>

volatile uint32_t* gpio = (volatile uint32_t*)0x10001000;

int main(int argc, int argv) {
    gpio[2] = 0xF; // 0x8 output_en (XXXXXXXXXX)
    gpio[3] = 0x0; // 0xC output_en (XXXXXXXXXX)
    int stat = 0;
    while(1) {
      for(int i = 0; i < 100000; i++);
    	stat = ~stat;
    	gpio[3] = stat & 0xF;
    }
	return 0;
}
