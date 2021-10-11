RISCV_PREFIX=riscv64-unknown-elf-
CC=$(RISCV_PREFIX)gcc
OBJCOPY=$(RISCV_PREFIX)objcopy
OBJDUMP=$(RISCV_PREFIX)objdump

LINKER_SCRIPT=link.ld

CFLAGS=-g -march=rv32imac -mabi=ilp32 -mcmodel=medany -I. -I./i2c -I../sdboot/kprintf -I../sdboot/include
LDFLAGS=-march=rv32imac -mabi=ilp32 -mcmodel=medany -T $(LINKER_SCRIPT) -nostartfiles -nostdlib --specs=nano.specs

all: $(BUILD_DIR)/bootloader.hex

elf: $(BUILD_DIR)/bootloader.elf

%.o : %.S
	$(CC) $(CFLAGS) -c -o $@ $<

%.o : %.c
	$(CC) $(CFLAGS) -c -o $@ $<

# The linker step
$(BUILD_DIR)/bootloader.elf: start.o main.o print.o i2c/driver.o i2c/time.o
	$(CC) $^ -o $@ $(LDFLAGS)

bin: $(BUILD_DIR)/bootloader.bin

$(BUILD_DIR)/bootloader.bin: $(BUILD_DIR)/bootloader.elf
	$(OBJCOPY) -O binary $< $@
	$(OBJDUMP) -d $^ > $@.dump
#	- riscv32-unknown-elf-objdump -b binary -D $@ --adjust-vma=$(SDBOOT_SOURCE_ADDR) -m RISCV > $@.rv32.dump

hex: $(BUILD_DIR)/bootloader.hex

$(BUILD_DIR)/bootloader.hex: $(BUILD_DIR)/bootloader.bin
	od -t x4 -An -w4 -v $< > $@

clean:
	rm -rf *.elf *.o i2c/*.o *.d *.hex *.bin

.PHONY: clean

