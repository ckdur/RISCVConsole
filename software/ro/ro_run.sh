#!/usr/bin/env bash
# One RO measurement: boot RISCVConsoleCode through JTAG (test() runs ro_test, ro_diag and ro_scan)
# and log its UART report.
#
# Usage: ./ro_run.sh <results_dir> [name]
# Produces <results_dir>/<name>.uart.log (default name: run<N>), <results_dir>/<name>.jtag.log,
# and appends the versions used to <results_dir>/versions.txt.
#
# Requires the SoC with rocounter programmed (fpga/ULX3S: make bitstream && make program, with
# ~/init_openlane.sh) and the firmware built (make -C ../RISCVConsoleCode elf).
#
# Environment:
#   UART_DEV   board UART (default: /dev/ttyUSB1)
#   TIMEOUT    seconds to wait for "Finished!" (default: 60)

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SW_DIR=$(dirname "$SCRIPT_DIR")
ELF=$SW_DIR/RISCVConsoleCode/build/out.elf
UART_DEV=${UART_DEV:-/dev/ttyUSB1}
TIMEOUT=${TIMEOUT:-60}

OUT=$(realpath -m "${1:?results_dir required}")
mkdir -p "$OUT"
N=$(ls "$OUT"/run*.uart.log 2>/dev/null | wc -l)
NAME=${2:-run$((N + 1))}

{
  echo "== $NAME $(date -Iseconds)"
  echo "firmware: $(git -C "$SW_DIR/RISCVConsoleCode" describe --always --dirty) ($(sha256sum "$ELF" | cut -c1-16))"
  echo "RISCVConsole: $(git -C "$SW_DIR/.." describe --always --dirty)"
  BIT=$(ls "$SW_DIR"/../fpga/ULX3S/generated-src/*/obj/*.bit 2>/dev/null | head -1)
  [ -n "$BIT" ] && echo "bitstream: $(basename "$BIT") $(date -r "$BIT" -Iseconds) ($(sha256sum "$BIT" | cut -c1-16))"
} >> "$OUT/versions.txt"

stty -F "$UART_DEV" 115200 raw -echo
cat "$UART_DEV" > "$OUT/$NAME.uart.raw" &
UART_PID=$!
trap 'kill $UART_PID 2> /dev/null' EXIT

"$SW_DIR/jtag-boot.sh" "$ELF" > "$OUT/$NAME.jtag.log" 2>&1
timeout "$TIMEOUT" tail -f "$OUT/$NAME.uart.raw" | grep -q -m1 "Finished!" || echo "no 'Finished!' within $TIMEOUT s" >&2
kill $UART_PID 2> /dev/null || true
tr -d '\r' < "$OUT/$NAME.uart.raw" > "$OUT/$NAME.uart.log"
rm -f "$OUT/$NAME.uart.raw"
sed -n '/RO test/,/Finished!/p' "$OUT/$NAME.uart.log"
