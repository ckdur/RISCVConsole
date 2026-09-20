yosys -import
plugin -i slang
yosys -import

proc load_vsrc_manifest {vsrc_manifest} {
  global env_var_srcs
  global PRJ_DIR
  set VERILOG_FILES ""
  set SVERILOG_FILES ""
  set VHD_FILES ""
  set SOURCE_FILES ""

  set fp [open $vsrc_manifest r]
  set files [lsearch -not -exact -all -inline [split [read $fp] "\n"] {}]
  set relative_files {}
  foreach path $files {
    if {[string match {/*} $path]} {
      lappend relative_files $path
    } elseif {![string match {#*} $path]} {
      lappend relative_files [file join [file dirname $vsrc_manifest] $path]
    }
  }
  # Read environment variable vsrcs and append to relative_files
  if {[info exists env_var_srcs]} {
    if {[info exists ::env($env_var_srcs)]} {
      set resources $::env($env_var_srcs)
      set relative_files [list {*}$relative_files {*}$resources]
    }
  }
  # Iterate the files in relative_files
  foreach fi $relative_files {
    if {[regexp {^.*\.(v|vh)$} $fi]} {
      lappend VERILOG_FILES $fi
    } elseif {[regexp {^.*\.(sv|svh)$} $fi]} {
      lappend SVERILOG_FILES $fi
    } elseif {[regexp {^.*\.(vhd|vhdl)$} $fi]} {
      lappend VHD_FILES $fi
    } else {
      lappend SOURCE_FILES $fi
    }
  }
  close $fp
  return [list $VERILOG_FILES $SVERILOG_FILES $VHD_FILES $SOURCE_FILES]
}
lassign [load_vsrc_manifest $env(synth_list_f)] VERILOG_FILES SVERILOG_FILES VHD_FILES SOURCE_FILES

read_slang -DSYNTHESIS --top $env(MODEL) --empty-blackboxes {*}$VERILOG_FILES {*}$SVERILOG_FILES
synth_ecp5 -json $env(build_dir)/obj/$env(MODEL).json

