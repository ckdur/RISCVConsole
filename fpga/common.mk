#########################################################################################
# general path variables
#########################################################################################
SUB_PROJECT?=chipyard
SBT_PROJECT?=riscvconsole
GENERATOR_PACKAGE?=chipyard
sim_name := none

# For simple tests... the regular chipyard config
ifeq ($(SUB_PROJECT),chipyard)
	MODEL             ?= TestHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= chipyard.harness
	CONFIG            ?= RocketConfig
	CONFIG_PACKAGE    ?= riscvconsole.config
	TB                ?= TestDriver
	TOP               ?= ChipTop
	BOARD             = none
	FPGA_BRAND        = none
endif
# For the RISCV console (in ULX3S)
ifeq ($(SUB_PROJECT),ulx3s)
	MODEL             ?= ULX3SHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= riscvconsole.fpga.ulx3s
	CONFIG            ?= RocketULX3SConfig
	CONFIG_PACKAGE    ?= riscvconsole.fpga.ulx3s
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             = ulx3s
	FPGA_BRAND        = nextpnr_ulx
endif
# For the RISCV console (in Arrow)
ifeq ($(SUB_PROJECT),arrow)
	MODEL             ?= ArrowHarness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= riscvconsole.fpga.arrow
	CONFIG            ?= RocketArrowConfig
	CONFIG_PACKAGE    ?= riscvconsole.fpga.arrow
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= arrow
	FPGA_BRAND        ?= altera
endif
# For the RISCV console (in DE2)
ifeq ($(SUB_PROJECT),DE2)
	MODEL             ?= DE2Harness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= riscvconsole.fpga.de2
	CONFIG            ?= RocketDE2Config
	CONFIG_PACKAGE    ?= riscvconsole.fpga.de2
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= DE2
	FPGA_BRAND        ?= altera
endif
# For the RISCV console (in TR4)
ifeq ($(SUB_PROJECT),TR4)
	MODEL             ?= TR4Harness
	VLOG_MODEL        ?= $(MODEL)
	MODEL_PACKAGE     ?= riscvconsole.fpga.tr4
	CONFIG            ?= RocketTR4Config
	CONFIG_PACKAGE    ?= riscvconsole.fpga.tr4
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= TR4
	FPGA_BRAND        ?= altera
endif
# For the RISCV console (in arty100t)
ifeq ($(SUB_PROJECT),arty100t)
	# TODO: Fix with Arty
	MODEL             ?= Arty100THarness
	VLOG_MODEL        ?= Arty100THarness
	MODEL_PACKAGE     ?= chipyard.fpga.arty100t
	CONFIG            ?= RocketArty100TConfig
	CONFIG_PACKAGE    ?= chipyard.fpga.arty100t
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= arty_a7_100
	FPGA_BRAND        ?= xilinx
endif
# For the RISCV console (in nexysvideo)
ifeq ($(SUB_PROJECT),nexysvideo)
	MODEL             ?= NexysVideoHarness
	VLOG_MODEL        ?= NexysVideoHarness
	MODEL_PACKAGE     ?= chipyard.fpga.nexysvideo
	CONFIG            ?= RocketNexysVideoConfig
	CONFIG_PACKAGE    ?= chipyard.fpga.nexysvideo
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= nexys_video
	FPGA_BRAND        ?= xilinx
endif

export USE_CHISEL6=1

include $(base_dir)/variables.mk

.PHONY: default
default: verilog

#########################################################################################
# misc. directories
#########################################################################################
fpga_dir := $(base_dir)/fpga/fpga-shells/$(FPGA_BRAND)
fpga_common_script_dir := $(fpga_dir)/common/tcl

#########################################################################################
# setup misc. sim files
#########################################################################################
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

#########################################################################################
# copy from other directory
#########################################################################################
all_vsrcs := \
	$(base_dir)/generators/rocket-chip-blocks/vsrc/SRLatch.v

#########################################################################################
# vivado rules
#########################################################################################
# combine all sources into single .f
synth_list_f := $(build_dir)/$(long_name).vsrcs.f
$(synth_list_f): $(sim_common_files) $(all_vsrcs)
	rm -f $@
	$(foreach file,$(all_vsrcs),echo "$(file)" >> $@;)
	cat $(sim_common_files) >> $@

ifeq ($(FPGA_BRAND),xilinx)
BIT_FILE := $(build_dir)/obj/$(MODEL).bit
$(BIT_FILE): $(synth_list_f)
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(fpga_common_script_dir)/vivado.tcl \
		-tclargs \
			-top-module "$(MODEL)" \
			-F "$(synth_list_f)" \
			-board "$(BOARD)" \
			-ip-vivado-tcls "$(shell find '$(build_dir)' -name '*.vivado.tcl')"

.PHONY: debug-bitstream
debug-bitstream: $(build_dir)/obj/post_synth.dcp
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(base_dir)/fpga/scripts/run_impl_bitstream.tcl \
		-tclargs \
			$(build_dir)/obj/post_synth.dcp \
			$(BOARD) \
			$(build_dir)/debug_obj \
			$(fpga_common_script_dir)

endif

ifeq ($(FPGA_BRAND),altera)
QSF_FILE := $(build_dir)/$(MODEL).qsf
$(QSF_FILE): $(synth_list_f)
	cd $(build_dir); quartus_sh \
		-t $(fpga_common_script_dir)/quartus.tcl \
		-top-module "$(MODEL)" \
		-F "$(synth_list_f)" \
        -ip-quartus-tcls "$(shell find '$(build_dir)' -name '*.quartus.tcl')" \
        -ip-quartus-qsys "$(shell find '$(build_dir)' -name '*.qsys')" \
        -ip-quartus-qsys-tcls "$(shell find '$(build_dir)' -name '*.qsys.tcl')" \
        -ip-quartus-sdc "$(shell find '$(build_dir)' -name '*.shell.sdc')" \
        -board "$(BOARD)" | tee $(build_dir)/quartus.log

BIT_FILE := $(build_dir)/output_files/$(MODEL).sof
$(BIT_FILE): $(QSF_FILE)
	cd $(build_dir); quartus_map $(MODEL)
	cd $(build_dir); quartus_fit $(MODEL)
	cd $(build_dir); quartus_asm $(MODEL)
	cd $(build_dir); quartus_sta $(MODEL)
endif

ifeq ($(FPGA_BRAND),nextpnr_ulx)
synthesis: $(build_dir)/obj/$(MODEL).json

export synth_list_f
export MODEL
export build_dir
$(build_dir)/obj/$(MODEL).json: $(synth_list_f) $(common_fpga_dir)/ULX3S/scripts/yosys.tcl
	yosys --tcl-scriptfile $(common_fpga_dir)/ULX3S/scripts/yosys.tcl | tee $(build_dir)/yosys.log

pnr: $(build_dir)/obj/$(MODEL).config

$(build_dir)/obj/$(MODEL).config: $(build_dir)/obj/$(MODEL).json $(build_dir)/$(long_name).shell.lpf
	nextpnr-ecp5 --85k --json $(build_dir)/obj/$(MODEL).json \
		--package CABGA381 \
		--lpf $(build_dir)/$(long_name).shell.lpf \
		--textcfg $(build_dir)/obj/$(MODEL).config | tee $(build_dir)/nextpnr.log

BIT_FILE := $(build_dir)/obj/$(MODEL).bit
$(BIT_FILE): $(build_dir)/obj/$(MODEL).config
	ecppack $(build_dir)/obj/$(MODEL).config $(BIT_FILE)

program:
	fujprog $(build_dir)/obj/$(MODEL).bit
endif


.PHONY: bitstream
bitstream: $(BIT_FILE)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir)

