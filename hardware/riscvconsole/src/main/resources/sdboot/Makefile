# RISCV environment variable must be set
ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
BUILD_DIR := $(ROOT_DIR)/build

CC=$(RISCV)/bin/riscv64-unknown-elf-gcc
OBJCOPY=$(RISCV)/bin/riscv64-unknown-elf-objcopy
OBJDUMP=$(RISCV)/bin/riscv64-unknown-elf-objdump
CFLAGS_RV64=-march=rv64ima_zicsr_zifencei -mcmodel=medany -O2 -std=gnu11 -Wall -nostartfiles
CFLAGS_RV64+= -fno-common -g -DENTROPY=0 -mabi=lp64 -DNONSMP_HART=0
CFLAGS_RV64+= -I $(ROOT_DIR)/include -I.
CFLAGS_RV32=-march=rv32ima_zicsr_zifencei -mcmodel=medany -O2 -std=gnu11 -Wall -nostartfiles
CFLAGS_RV32+= -fno-common -g -DENTROPY=0 -mabi=ilp32 -DNONSMP_HART=0
CFLAGS_RV32+= -I $(ROOT_DIR)/include -I.
LFLAGS=-static -nostdlib -L $(ROOT_DIR)/linker -T sdboot.elf.lds

# default to 50MHz but really should be overridden
PBUS_CLK ?= 50

default: elf bin dump

elf := $(BUILD_DIR)/sdboot.rv32.elf $(BUILD_DIR)/sdboot.rv64.elf
%.rv64.elf: head.S kprintf.c sd.c
	mkdir -p $(BUILD_DIR)
	$(CC) $(CFLAGS_RV64) -DTL_CLK="$(PBUS_CLK)UL" $(LFLAGS) -o $@ head.S sd.c kprintf.c

%.rv32.elf: head.S kprintf.c sd.c
	mkdir -p $(BUILD_DIR)
	$(CC) $(CFLAGS_RV32) -DTL_CLK="$(PBUS_CLK)UL" $(LFLAGS) -o $@ head.S sd.c kprintf.c

.PHONY: elf
elf: $(elf)

bin := $(BUILD_DIR)/sdboot.rv32.bin $(BUILD_DIR)/sdboot.rv64.bin
%.bin: %.elf
	mkdir -p $(BUILD_DIR)
	$(OBJCOPY) -O binary --change-addresses=-0x10000 $< $@

.PHONY: bin
bin: $(bin)

dump := $(BUILD_DIR)/sdboot.rv32.dump $(BUILD_DIR)/sdboot.rv64.dump
%.dump: %.elf
	$(OBJDUMP) -d $< > $@

.PHONY: dump
dump: $(dump)

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
