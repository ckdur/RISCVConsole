#include "print.h"
#include "platform.h"


volatile uint32_t* uart = (volatile uint32_t*)UART_CTRL_ADDR;


#define IN_INIT_SECTION __attribute__((section(".init")))

void IN_INIT_SECTION print_init()
{
	uart[2] = 1; // TXCTRL <- enable
	uart[3] = 1; // RXCTRL <- enable
}

void IN_INIT_SECTION print_str(const char *s)
{
  while (*s != '\0') {
    while (uart[0] & 0x80000000) ;
    uart[0] = *s;

    if (*s == '\n') {
      while (uart[0] & 0x80000000) ;
      uart[0] = '\r';
    }

    ++s;
  }
}

void IN_INIT_SECTION print_chr(char ch)
{
	while (uart[0] & 0x80000000) ;
  uart[0] = ch;
}

void IN_INIT_SECTION print_dec(unsigned int val)
{
	char buffer[10];
	char *p = buffer;
	while (val || p == buffer) {
		*(p++) = val % 10;
		val = val / 10;
	}
	while (p != buffer) {
	  while (uart[0] & 0x80000000) ;
    uart[0] = '0' + *(--p);
	}
}

void IN_INIT_SECTION print_hex(unsigned int val, int digits)
{
	for (int i = (4*digits)-4; i >= 0; i -= 4)
	{
	  while (uart[0] & 0x80000000) ;
    uart[0] = "0123456789ABCDEF"[(val >> i) % 16];
	}
}
int IN_INIT_SECTION time_custom()
{
	int cycles;
	asm volatile ("rdcycle %0" : "=r"(cycles));
	return cycles;
}

int IN_INIT_SECTION insn_custom()
{
	int insns;
	asm volatile ("rdinstret %0" : "=r"(insns));
	return insns;
}
