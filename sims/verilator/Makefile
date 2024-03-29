# Configurations for this specific simulation
SBT_PROJECT       ?= riscvconsole
MODEL             ?= RVCHarness
VLOG_MODEL        ?= RVCHarness
MODEL_PACKAGE     ?= riscvconsole.system
CONFIG            ?= RVCHarnessConfig
CONFIG_PACKAGE    ?= riscvconsole.system
GENERATOR_PACKAGE ?= riscvconsole
TB                ?= TestDriver
TOP               ?= RVCSystem

#########################################################################################
# verilator makefile
#########################################################################################
ifeq ($(shell which verilator),)
$(error Did not find Verilator in PATH. Make sure all requirements are installed)
endif

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ../..)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(base_dir)/variables.mk

#########################################################################################
# name of simulator (used to generate *.f arguments file)
#########################################################################################
sim_name = verilator

#########################################################################################
# vcs simulator types and rules
#########################################################################################
sim_prefix = simulator
sim = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)
sim_debug = $(sim_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)-debug

WAVEFORM_FLAG=-v$(sim_out_name).vcd

.PHONY: default debug
default: $(sim)
debug: $(sim_debug)

#########################################################################################
# setup misc. sim files
#########################################################################################

SIM_FILE_REQS += \
	$(CHIPYARD_RSRCS_DIR)/csrc/emulator.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/verilator.h \

# the following files are needed for emulator.cc to compile
SIM_FILE_REQS += \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/SimSerial.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/testchip_tsi.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/testchip_tsi.h \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/SimDRAM.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm.h \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm.cc \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm_dramsim2.h \
	$(TESTCHIP_RSRCS_DIR)/testchipip/csrc/mm_dramsim2.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/SimDTM.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/SimJTAG.cc \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/remote_bitbang.h \
	$(ROCKETCHIP_RSRCS_DIR)/csrc/remote_bitbang.cc

SIM_FILE_REQS += \
	$(ROCKETCHIP_RSRCS_DIR)/vsrc/EICG_wrapper.v

# copy files but ignore *.h files in *.f (match vcs)
$(sim_files): $(SIM_FILE_REQS) | $(build_dir)
	cp -f $^ $(build_dir)
	$(foreach file,\
		$^,\
		$(if $(filter %.h,$(file)),\
			echo "-FI $(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;,\
			echo "$(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;))

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# verilator binary and flags
#########################################################################################
VERILATOR := verilator --cc --exe

CXXFLAGS := \
	$(CXXFLAGS) -O1 -std=c++11 -g \
	-I$(RISCV)/include \
	-I$(dramsim_dir) \
	-D__STDC_FORMAT_MACROS \
	$(EXTRA_SIM_CC_FLAGS)

LDFLAGS := \
	$(LDFLAGS) \
	-L$(sim_dir) \
	-lpthread

VERILATOR_CC_OPTS = \
	-O3 \
	-CFLAGS "$(CXXFLAGS) -DTEST_HARNESS=V$(VLOG_MODEL) -DVERILATOR" \
	-CFLAGS "-I$(build_dir) -include $(build_dir)/$(long_name).plusArgs -include $(build_dir)/verilator.h" \
	-LDFLAGS "$(LDFLAGS)" \
	$(RISCV)/lib/libfesvr.a \
	$(dramsim_lib)

# default flags added for ariane
ARIANE_VERILATOR_FLAGS = \
	--unroll-count 256 \
	-Werror-PINMISSING \
	-Werror-IMPLICIT \
	-Wno-PINCONNECTEMPTY \
	-Wno-ASSIGNDLY \
	-Wno-DECLFILENAME \
	-Wno-UNUSED \
	-Wno-UNOPTFLAT \
	-Wno-BLKANDNBLK \
	-Wno-style \
	-Wall

# normal flags used for chipyard builds (that are incompatible with ariane)
CHIPYARD_VERILATOR_FLAGS = \
	--assert

# Use --timescale to approximate timescale behavior of pre-4.034
TIMESCALE_OPTS := 
VERILATOR_NONCC_OPTS = \
	$(TIMESCALE_OPTS) \
	--top-module $(VLOG_MODEL) \
	--vpi \
	-Wno-fatal \
	$(shell if ! grep -iq "module.*ariane" $(build_dir)/*.*v; then echo "$(CHIPYARD_VERILATOR_FLAGS)"; else echo "$(ARIANE_VERILATOR_FLAGS)"; fi) \
	--output-split 10000 \
	--output-split-cfuncs 100 \
	--max-num-width 1048576 \
	-f $(sim_common_files) \
	$(sim_vsrcs) \
	$(ROM_FILE)

VERILATOR_DEFINES = \
	+define+PRINTF_COND=\$$c\(\"verbose\",\"\&\&\"\,\"done_reset\"\) \
	+define+STOP_COND=\$$c\(\"done_reset\"\)

VERILATOR_OPTS = $(VERILATOR_CC_OPTS) $(VERILATOR_NONCC_OPTS) $(VERILATOR_DEFINES) $(EXTRA_SIM_SOURCES)

#########################################################################################
# verilator build paths and file names
#########################################################################################
model_dir = $(build_dir)/$(long_name)
model_dir_debug = $(build_dir)/$(long_name).debug

model_header = $(model_dir)/V$(VLOG_MODEL).h
model_header_debug = $(model_dir_debug)/V$(VLOG_MODEL).h

model_mk = $(model_dir)/V$(VLOG_MODEL).mk
model_mk_debug = $(model_dir_debug)/V$(VLOG_MODEL).mk

#########################################################################################
# build makefile fragment that builds the verilator sim rules
#########################################################################################
$(model_mk): $(sim_vsrcs) $(ROM_FILE) $(sim_common_files) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir)
	mkdir -p $(model_dir)
	$(VERILATOR) $(VERILATOR_OPTS) -o $(sim) -Mdir $(model_dir) -CFLAGS "-include $(model_header)"
	touch $@

$(model_mk_debug): $(sim_vsrcs) $(ROM_FILE) $(sim_common_files) $(EXTRA_SIM_REQS)
	rm -rf $(model_dir_debug)
	mkdir -p $(model_dir_debug)
	$(VERILATOR) $(VERILATOR_OPTS) -o $(sim_debug) --trace -Mdir $(model_dir_debug) -CFLAGS "-include $(model_header_debug)"
	touch $@

#########################################################################################
# invoke make to make verilator sim rules
#########################################################################################
$(sim): $(model_mk) $(dramsim_lib)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir) -f V$(VLOG_MODEL).mk

$(sim_debug): $(model_mk_debug) $(dramsim_lib)
	$(MAKE) VM_PARALLEL_BUILDS=1 -C $(model_dir_debug) -f V$(VLOG_MODEL).mk

#########################################################################################
# create a verilator vpd rule
#########################################################################################
.PRECIOUS: $(output_dir)/%.vpd %.vcd
$(output_dir)/%.vpd: $(output_dir)/% $(sim_debug)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) -v$@.vcd $(PERMISSIVE_OFF) $< </dev/null 2> >(spike-dasm > $<.out) | tee $<.log)

$(output_dir)/none.vpd: $(sim_debug)
	mkdir -p $(output_dir)
	rm -f $@.vcd && mkfifo $@.vcd
	vcd2vpd $@.vcd $@ > /dev/null &
	(set -o pipefail && $(sim_debug) $(PERMISSIVE_ON) $(SIM_FLAGS) $(EXTRA_SIM_FLAGS) $(VERBOSE_FLAGS) -v$@.vcd $(PERMISSIVE_OFF) none </dev/null 2> >(spike-dasm > $(output_dir)/none.out) | tee $(output_dir)/none.log)

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir) $(sim_prefix)-*
