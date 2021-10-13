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
	rm -rf $(gen_dir)

