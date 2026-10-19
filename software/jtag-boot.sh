#!/usr/bin/env bash
# Load a payload ELF (e.g. RISCVConsoleCode/build/out.elf) into RAM through JTAG
# and start it, instead of copying it to the SD card.
#
# The core is reset and halted at the reset vector (before sdboot touches the SD card),
# the ELF is written to RAM, and execution resumes at the sdboot JTAG entry (0x10004),
# which jumps to the payload with a0 = hartid and a1 = DTB, same as the SD boot path.
#
# Usage:
#   ./jtag-boot.sh [payload.elf]          load and run
#   ./jtag-boot.sh -g [payload.elf]       load, then open gdb halted at the payload entry
#
# Environment:
#   OPENOCD_CFG   OpenOCD config for the JTAG adapter (default: ftdi.cfg next to this script)
#   GDB           gdb binary (default: riscv64-unknown-elf-gdb, or gdb-multiarch)
#   GDB_PORT      gdb port of OpenOCD (default: 3333)

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
OPENOCD_CFG=${OPENOCD_CFG:-$SCRIPT_DIR/ftdi.cfg}
GDB_PORT=${GDB_PORT:-3333}
JTAG_BOOT_ADDR=0x10004

USE_GDB=0
if [ "$1" = "-g" ]; then
  USE_GDB=1
  shift
fi
ELF=$(realpath "${1:-$SCRIPT_DIR/RISCVConsoleCode/build/out.elf}")

if [ ! -f "$ELF" ]; then
  echo "ELF not found: $ELF (build RISCVConsoleCode first)" >&2
  exit 1
fi

if [ $USE_GDB -eq 0 ]; then
  openocd -f "$OPENOCD_CFG" \
    -c "reset halt" \
    -c "load_image $ELF" \
    -c "resume $JTAG_BOOT_ADDR" \
    -c "shutdown"
  exit 0
fi

if [ -z "$GDB" ]; then
  if command -v riscv64-unknown-elf-gdb > /dev/null; then
    GDB=riscv64-unknown-elf-gdb
  else
    GDB=gdb-multiarch
  fi
fi

openocd -f "$OPENOCD_CFG" -c "gdb_port $GDB_PORT" &
OPENOCD_PID=$!
trap 'kill $OPENOCD_PID 2> /dev/null' EXIT
sleep 2

"$GDB" "$ELF" \
  -ex "set confirm off" \
  -ex "set pagination off" \
  -ex "target extended-remote localhost:$GDB_PORT" \
  -ex "monitor reset halt" \
  -ex "load" \
  -ex "set \$pc = $JTAG_BOOT_ADDR" \
  -ex "tbreak *_prog_start" \
  -ex "continue" \
  -ex "echo Payload loaded and halted at its entry. Set breakpoints, then 'continue'.\n"
