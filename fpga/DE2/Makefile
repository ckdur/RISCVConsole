#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

SUB_PROJECT ?= DE2
sim_name = verilator

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# BOOTROM
#########################################################################################
#bldr_dir=$(soft_dir)/bootloader
#HEX_FILE=$(build_dir)/bootloader.hex
SDBOOT_TARGET_JUMP=0x80000000UL

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

.PHONY: clean
clean:
	rm -rf $(build_dir)

#########################################################################################
# Common rules for the Quartus
#########################################################################################
VSRCS := $(sim_vsrcs) $(top_and_harness_files) $(ROM_FILE)
TOP_F := $(sim_top_blackboxes) $(sim_harness_blackboxes)

add_file := $(build_dir)/pll.quartus.tcl
$(build_dir)/pll.quartus.tcl: pll/pll.quartus.tcl
	cp $< $@
	cp -r pll $(build_dir)/pll

BUILD_DIR=$(build_dir)
FPGA_DIR=$(base_dir)/hardware/fpga-shells/altera
export FPGA_BOARD=DE2
TCL_SHELL=$(sim_dir)/DE2.shell.quartus.tcl
TCL_QSYS_MAIN=$(sim_dir)/main.qsys
SDC_FILE=$(sim_dir)/constraints.sdc
include $(base_dir)/fpga/altera.mk

