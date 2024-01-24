#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

SUB_PROJECT ?= ulx3s
sim_name = verilator

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# Rule for ROM file
#########################################################################################
soft_dir=$(base_dir)/software
bldr_dir?=$(soft_dir)/sdboot
HEX_FILE?=$(build_dir)/sdboot.hex
ROM_FILE ?= $(build_dir)/$(long_name).rom.v
ROM_CONF_FILE ?= $(build_dir)/$(long_name).rom.conf
SDBOOT_TARGET_JUMP?=0x81F00000UL
ROM_SCRIPT = $(base_dir)/hardware/vlsi_rom_gen_fpga
#ROM_SCRIPT = $(base_dir)/hardware/vlsi_rom_gen
$(ROM_FILE): $(ROM_CONF_FILE) $(HEX_FILE)
	python2 $(ROM_SCRIPT) $(ROM_CONF_FILE) $(HEX_FILE) > $(ROM_FILE)

$(ROM_CONF_FILE): $(FIRRTL_FILE)
	touch $(ROM_CONF_FILE)

$(HEX_FILE):
	make -C $(bldr_dir) BUILD_DIR=$(build_dir) SDBOOT_TARGET_JUMP=$(SDBOOT_TARGET_JUMP) long_name=$(long_name) clean
	make -C $(bldr_dir) BUILD_DIR=$(build_dir) SDBOOT_TARGET_JUMP=$(SDBOOT_TARGET_JUMP) long_name=$(long_name) hex

#########################################################################################
# setup misc. sim files
#########################################################################################

SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/EICG_wrapper.v

# copy files but ignore *.h files in *.f (match vcs)
$(sim_files): $(SIM_FILE_REQS) $(ALL_MODS_FILELIST) | $(GEN_COLLATERAL_DIR)
	-cp -f $(SIM_FILE_REQS) $(GEN_COLLATERAL_DIR)
	touch $@
	$(foreach file,\
		$(SIM_FILE_REQS),\
		$(if $(filter %.h,$(file)),\
			,\
			echo "$(addprefix $(GEN_COLLATERAL_DIR)/, $(notdir $(file)))" >> $@;))

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

# combine all sources into single .f
synth_list_f := $(build_dir)/$(long_name).vsrcs.f
export synth_list_f
$(synth_list_f): $(sim_common_files) $(all_vsrcs) $(ROM_FILE)
	$(foreach file,$(all_vsrcs),echo "$(file)" >> $@;)
	cat $(sim_common_files) >> $@
	echo $(ROM_FILE) >> $@

#########################################################################################
# Synthesis
#########################################################################################
synthesis: $(build_dir)/$(MODEL).json

export build_dir
export MODEL
$(build_dir)/$(MODEL).json: $(synth_list_f)
	yosys -h tcl $(sim_dir)/yosys.tcl | tee $(build_dir)/yosys.log
#	yosys -p "read_verilog `cat $(top_and_harness_files)` $(ROM_FILE) $(EICG_wrapper); synth_ecp5 -json $(build_dir)/$(MODEL).json" | tee $(build_dir)/yosys.log

pnr: $(build_dir)/$(MODEL).config

$(build_dir)/$(MODEL).config: $(build_dir)/$(MODEL).json $(sim_dir)/ulx3s_v20.lpf
	nextpnr-ecp5 --85k --json $(build_dir)/$(MODEL).json \
		--lpf $(sim_dir)/ulx3s_v20.lpf \
		--textcfg $(build_dir)/$(MODEL).config | tee $(build_dir)/nextpnr.log

pack: $(build_dir)/$(MODEL).bit

$(build_dir)/$(MODEL).bit: $(build_dir)/$(MODEL).config
	ecppack $(build_dir)/$(MODEL).config $(build_dir)/$(MODEL).bit

program:
	fujprog $(build_dir)/$(MODEL).bit

.PHONY: clean program
clean:
	rm -rf $(gen_dir)

