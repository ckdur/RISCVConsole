// See LICENSE for license details.
#include <stdint.h>

#include <platform.h>

#include "common.h"
#include "sd.h"
#include "boot.h"
#include <gpt/gpt.h>

#define DEBUG
#include "kprintf.h"

extern const gpt_guid gpt_guid_sifive_bare_metal;
int main(void)
{
	REG32(uart, UART_REG_TXCTRL) = UART_TXEN;
	spi = (void *)(SPI_CTRL_ADDR); // Default to the SPI

	sd_init(CORE_CLK_KHZ);
	
	int error = boot_load_gpt_partition((void*) PAYLOAD_DEST, &gpt_guid_sifive_bare_metal);
	
	if(error) {
	  copy();
	}

	kputs("\nBOOTING RATONA:\n");

	__asm__ __volatile__ ("fence.i" : : : "memory");
	return 0;
}
