#!/usr/bin/env bash
# One ADC measurement: drive the input with the MSO DDS, boot RISCVConsoleCode through JTAG,
# log its UART report and dump adc_buf through JTAG.
#
# Usage: ./adc_run.sh <out_prefix> [freq_hz] [ampl_mv] [bias_mv] [wave]
# Produces <out_prefix>.bin (raw ADC codes), <out_prefix>.uart.log, <out_prefix>.scope.csv, <out_prefix>.mso.log
#
# Environment:
#   UART_DEV   board UART (default: /dev/ttyUSB1)

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SW_DIR=$(dirname "$SCRIPT_DIR")
ELF=$SW_DIR/RISCVConsoleCode/build/out.elf
UART_DEV=${UART_DEV:-/dev/ttyUSB1}

OUT=$(realpath -m "${1:?out_prefix required}")
FREQ=${2:-1000}
AMPL=${3:-400}
BIAS=${4:-600}
WAVE=${5:-sine}

ADC_BUF=0x$(riscv64-unknown-elf-nm "$ELF" | awk '$3=="adc_buf"{print $1}')
ADC_BUF_SIZE=65536

# DDS on, held long enough for the boot and the dump
"$SCRIPT_DIR/mso_dds" "$FREQ" "$AMPL" "$BIAS" 60 "$OUT.scope.csv" "$WAVE" > "$OUT.mso.log" 2>&1 &
MSO_PID=$!
# Wait for the MSO process to exit, so the next run can open the device
trap 'kill $UART_PID 2> /dev/null; kill $MSO_PID 2> /dev/null; wait $MSO_PID 2> /dev/null' EXIT
for i in $(seq 100); do
  grep -q "DDS running" "$OUT.mso.log" && break
  if ! kill -0 $MSO_PID 2> /dev/null; then
    cat "$OUT.mso.log" >&2
    echo "mso_dds failed, no stimulus" >&2
    exit 1
  fi
  sleep 0.1
done
grep -q "DDS running" "$OUT.mso.log" || { echo "mso_dds did not start" >&2; exit 1; }
sleep 1

stty -F "$UART_DEV" 115200 raw -echo
cat "$UART_DEV" > "$OUT.uart.log" &
UART_PID=$!

"$SW_DIR/jtag-boot.sh" "$ELF" > "$OUT.jtag.log" 2>&1
sleep 3

openocd -f "$SW_DIR/ftdi.cfg" -c "halt" -c "dump_image $OUT.bin $ADC_BUF $ADC_BUF_SIZE" -c "shutdown" >> "$OUT.jtag.log" 2>&1

cat "$OUT.mso.log"
tr -d '\r' < "$OUT.uart.log" | sed -n '/ADC capture/,/Finished/p'
