#  Slurp up the data file
set fp [open $env(top_and_harness_files) r]
set ALL_VSRC [read $fp]
close $fp

yosys::read_verilog $ALL_VSRC
yosys::synth_ecp5 -json $env(build_dir)/$env(MODEL).json

