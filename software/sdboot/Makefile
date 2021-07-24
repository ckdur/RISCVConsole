BOOTROM_DIR?=$(abspath .)

XLEN?=32
CROSSCOMPILE?=riscv64-unknown-elf
CC=$(CROSSCOMPILE)-gcc
OBJCOPY=$(CROSSCOMPILE)-objcopy
OBJDUMP=$(CROSSCOMPILE)-objdump
CFLAGS_ARCH=-march=rv$(XLEN)imac
ifeq ($(XLEN),64)
CFLAGS_ARCH+= -mabi=lp64
else #32
CFLAGS_ARCH+= -mabi=ilp32
endif
CFLAGS=$(CFLAGS_ARCH) -mcmodel=medany -O2 -std=gnu11 -Wall -nostartfiles 
CFLAGS+= -fno-common -g -DENTROPY=0 -DNONSMP_HART=0 
CFLAGS+= -I $(BOOTROM_DIR)/include -I. -I./gpt -I./boot -I./sd -I./kprintf $(ADD_OPTS) 
LFLAGS=-static -nostdlib -L $(BOOTROM_DIR)/linker -T sdboot.elf.lds
SDBOOT_TARGET_ADDR?=0x80000000UL
SDBOOT_TARGET_JUMP?=0x81F00000UL
SDBOOT_SOURCE_ADDR?=0x20000000
BUILD_DIR?=./

dts := $(BUILD_DIR)/$(long_name).dts
dtb := $(BUILD_DIR)/$(long_name).dtb
clk := $(BUILD_DIR)/$(long_name).tl_clock.h

$(clk): $(dts)
	awk '/subsystem_sbus_clock {/ && !f{f=1; next}; f && match($$0, /^.*clock-frequency.*<(.*)>.*/, arr) { print "#define TL_CLK " arr[1] "UL"}' $< > $@.tmp
	awk '/cpu@/{++cnt} END {print "#define NUM_CORES",cnt, "\n#define MAX_HART_ID",cnt*2}' $< >> $@.tmp
	mv $@.tmp $@

$(dtb): $(dts)
	dtc -I dts -O dtb -o $@ $<

.PHONY: dtb
dtb: $(dtb)

elf := $(BUILD_DIR)/sdboot.elf
$(elf): $(dtb) head.S kprintf/kprintf.c sd/sd.c boot/boot.c gpt/gpt.c main.c $(clk)
	$(CC) $(CFLAGS) -include $(clk) -DDEVICE_TREE='"$(dtb)"' -DSDBOOT_TARGET_ADDR=$(SDBOOT_TARGET_ADDR) -DSDBOOT_TARGET_JUMP=$(SDBOOT_TARGET_JUMP) $(LFLAGS) -o $@ head.S sd/sd.c boot/boot.c gpt/gpt.c main.c kprintf/kprintf.c

.PHONY: elf
elf: $(elf)

bin := $(BUILD_DIR)/sdboot.bin
$(bin): $(elf)
	$(OBJCOPY) -O binary $< $@
	$(OBJDUMP) -d $^ > $@.dump
	- riscv32-unknown-elf-objdump -b binary -D $@ --adjust-vma=$(SDBOOT_SOURCE_ADDR) -m RISCV > $@.rv32.dump

.PHONY: bin
bin: $(bin)

hex := $(BUILD_DIR)/sdboot.hex
$(hex): $(bin)
	od -t x4 -An -w4 -v $< > $@

romgen := $(BUILD_DIR)/rom.v
$(romgen): $(hex)
	$(rocketchip_dir)/scripts/vlsi_rom_gen $(ROMCONF) $< > $@

.PHONY: hex
hex: $(hex)

.PHONY: clean
clean::
	rm -rf $(hex) $(elf)
