##############################################################################
## Preset global variables and attributes
##############################################################################
set build_dir $env(build_dir)
set MODEL $env(MODEL)
yosys -import

###############################################################
#  Slurp up the data file
###############################################################
set fp [open $env(synth_list_f) r]
# set ALL_VSRC [read $fp]
set ALL_VSRC [split [string trim [read $fp]]]
close $fp
puts "all the verilog: $ALL_VSRC"

################################################################
## Verilog read
################################################################
read_verilog $ALL_VSRC


################################################################
## Verilog read
################################################################
synth_ecp5 -json $build_dir/$MODEL.json


