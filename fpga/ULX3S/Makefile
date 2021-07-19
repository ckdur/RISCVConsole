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
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# vcs simulator types and rules
#########################################################################################
.PHONY: default
default: $(sim_vsrcs) $(top_and_harness_files)

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

program:
	fujprog $(build_dir)/$(MODEL).bit

.PHONY: clean program
clean:
	rm -rf $(gen_dir)

