#!/usr/bin/env bash
# Full ADC characterization into one results directory: a ramp (histogram linearity)
# and a sine at each frequency of the sweep. Every frequency is coherent with the capture
# (an integer number of periods in ADC_BUF_SIZE samples, odd prime for the sines).
#
# Usage: ./adc_sweep.sh <results_dir> [sine_ampl_mv] [sine_bias_mv] [ramp_ampl_mv] [ramp_bias_mv]
# Environment:
#   SWEEP_KHZ   target sine frequencies in kHz (default: "1 10 50 100 200 300 500 650")
#
# Writes <results_dir>/runs.csv (name,wave,freq_hz,ampl_mv,bias_mv,cycles) used by report/make_report.py

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
OUT=$(realpath -m "${1:?results_dir required}")
SINE_AMPL=${2:-360}
SINE_BIAS=${3:-565}
RAMP_AMPL=${4:-700}
RAMP_BIAS=${5:-450}
SWEEP_KHZ=${SWEEP_KHZ:-"1 10 50 100 200 300 500 650"}

ADC_FS=$(python3 -c "print(1e7 / 7)")
ADC_N=65536

mkdir -p "$OUT"
echo "name,wave,freq_hz,ampl_mv,bias_mv,cycles" > "$OUT/runs.csv"

# Number of periods in the capture: 10 for the ramp, nearest odd prime for the sines
cycles_for() {
  python3 - "$1" "$ADC_FS" "$ADC_N" <<'EOF'
import sys
f, fs, n = float(sys.argv[1]), float(sys.argv[2]), int(sys.argv[3])
k = max(3, round(f * n / fs))
isprime = lambda m: m > 1 and all(m % d for d in range(2, int(m ** 0.5) + 1))
d = 0
while True:
    for c in (k - d, k + d):
        if c > 2 and isprime(c):
            print(c)
            sys.exit()
    d += 1
EOF
}

run() {  # name wave cycles ampl bias
  local freq
  freq=$(python3 -c "print($3 * $ADC_FS / $ADC_N)")
  echo "=== $1: $2 $freq Hz ($3 cycles) ampl=$4 mV bias=$5 mV"
  "$SCRIPT_DIR/adc_run.sh" "$OUT/$1" "$freq" "$4" "$5" "$2" | grep -E "CH1|ADC capture|Min"
  echo "$1,$2,$freq,$4,$5,$3" >> "$OUT/runs.csv"
}

run ramp ramp 10 "$RAMP_AMPL" "$RAMP_BIAS"
for khz in $SWEEP_KHZ; do
  run "sine_${khz}k" sine "$(cycles_for "$(python3 -c "print($khz * 1000)")")" "$SINE_AMPL" "$SINE_BIAS"
done
