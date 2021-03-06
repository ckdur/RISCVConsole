#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
soft_dir=$(base_dir)/software
bldr_dir=$(soft_dir)/bootloader
sim_dir=$(abspath .)

SUB_PROJECT ?= ulx3s
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

#########################################################################################
# Synthesis
#########################################################################################
synthesis: $(build_dir)/$(MODEL).json

$(build_dir)/$(MODEL).json: $(sim_vsrcs) $(top_and_harness_files) $(ROM_FILE)
	yosys -p "read_verilog `cat $(top_and_harness_files)` $(ROM_FILE) $(EICG_wrapper); synth_ecp5 -noccu2 -nomux -nodram -json $(build_dir)/$(MODEL).json" | tee $(build_dir)/yosys.log

pnr: $(build_dir)/$(MODEL).config

$(build_dir)/$(MODEL).config: $(build_dir)/$(MODEL).json $(sim_dir)/ulx3s_v20.lpf
	nextpnr-ecp5 --85k --json $(build_dir)/$(MODEL).json \
		--lpf $(sim_dir)/ulx3s_v20.lpf \
		--textcfg $(build_dir)/$(MODEL).config | tee $(build_dir)/nextpnr.log

pack: $(build_dir)/$(MODEL).bit

$(build_dir)/$(MODEL).bit: $(build_dir)/$(MODEL).config
	ecppack $(build_dir)/$(MODEL).config $(build_dir)/$(MODEL).bit

program: $(build_dir)/$(MODEL).bit
	fujprog $(build_dir)/$(MODEL).bit

.PHONY: clean program
clean:
	rm -rf $(gen_dir)

