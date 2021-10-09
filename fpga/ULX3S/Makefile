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
# setup misc. sim files
#########################################################################################

SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/EICG_wrapper.v

# copy files but ignore *.h files in *.f (match vcs)
$(sim_files): $(SIM_FILE_REQS) | $(build_dir)
	cp -f $^ $(build_dir)
	$(foreach file,\
		$^,\
		$(if $(filter %.h,$(file)),\
			,\
			echo "$(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;))

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# vcs simulator types and rules
#########################################################################################
.PHONY: default
default: $(sim_vsrcs) $(top_and_harness_files) $(ROM_FILE)

#########################################################################################
# Synthesis
#########################################################################################
synthesis: $(build_dir)/$(MODEL).json

$(build_dir)/$(MODEL).json: $(sim_vsrcs) $(top_and_harness_files) $(ROM_FILE)
	yosys -p "read_verilog `cat $(top_and_harness_files)` $(ROM_FILE) $(EICG_wrapper); synth_ecp5 -json $(build_dir)/$(MODEL).json" | tee $(build_dir)/yosys.log

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

