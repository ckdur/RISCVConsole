#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
soft_dir=$(base_dir)/software
bldr_dir=$(soft_dir)/bootloader
sim_dir=$(abspath .)

SUB_PROJECT ?= arrow
sim_name = verilator

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# vcs simulator types and rules
#########################################################################################
.PHONY: default
default: $(sim_vsrcs) $(top_and_harness_files)

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# Rule for ROM file
#########################################################################################
ROM_FILE ?= $(build_dir)/$(long_name).rom.v
ROM_CONF_FILE ?= $(build_dir)/$(long_name).rom.conf
$(ROM_FILE): $(ROM_CONF_FILE) $(bldr_dir)/bootloader.hex
	python $(base_dir)/hardware/vlsi_rom_gen $(ROM_CONF_FILE) $(bldr_dir)/bootloader.hex > $(ROM_FILE)

$(ROM_CONF_FILE): $(FIRRTL_FILE)
	touch $(ROM_CONF_FILE)

# The EICG_wrapper needs to be added manually, because verilator...
EICG_wrapper = $(build_dir)/EICG_wrapper.v

$(bldr_dir)/bootloader.hex:
	make -C $(bldr_dir) hex

.PHONY: clean program
clean:
	rm -rf $(gen_dir)

