##############################################################################
## Preset global variables and attributes
##############################################################################
set build_dir $env(build_dir)
set MODEL $env(MODEL)
yosys -import
plugin -i systemverilog
yosys -import

###############################################################
#  Slurp up the data file
###############################################################
set fp [open $env(synth_list_f) r]
# set ALL_VSRC [read $fp]
set ALL_VSRC [split [string trim [read $fp]]]
close $fp
set ALL_VERILOG [lsort -unique [lsearch -all -inline $ALL_VSRC *.v]]
set ALL_SVERILOG [lsort -unique [lsearch -all -inline $ALL_VSRC *.sv]]
set ALL_VHDL [lsort -unique [lsearch -all -inline $ALL_VSRC *.(vhdl|hdl)]]

################################################################
## Verilog read
################################################################
foreach f_name $ALL_VERILOG {
  puts "attempting to read verilog $f_name"
  read_verilog $f_name
}

foreach f_name $ALL_SVERILOG {
  puts "attempting to read systemverilog $f_name"
#  read_verilog -sv $f_name
  read_systemverilog $f_name
}

foreach f_name $ALL_VHDL {
#  puts "attempting to read VHDL $f_name"
#  ghdl $f_name
}

################################################################
## Synthesize and create the netlist file
################################################################
synth_ecp5 -top $MODEL -json $build_dir/$MODEL.json

