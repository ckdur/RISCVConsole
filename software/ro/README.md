# Ring oscillator measurement (ASICONE ro_reliability)

Frequency measurement of the two ring oscillators of the ASICONE chip (RO13 and RO101) on the IHP130
QFN48 adapter board. RISCVConsoleCode configures the rings through the chip SPI and counts the edges of
their output pads (the ring divided by 32 on chip) with the `rocounter` peripheral of the SoC
(`hardware/riscvconsole/src/main/scala/devices/rocounter`, `0x64005000`). The simulation that predicts
the results is in `asicone_sg13g2/ro_reliability/sim` (`README.md` there).

| Path | What |
|------|------|
| `ro_run.sh` | one bench run: JTAG boot of the firmware, UART log, versions of firmware/repo/bitstream |
| `report/` | report generator (`make_report.py`, LaTeX): prediction, measurement, post-layout comparison |
| `results/ro_20261029/` | first measurement: `run*.uart.log`, `versions.txt`, and copies of `sim.csv` (schematic), `pex_summary.csv` and `pex_startup.csv` (post-layout) from `ro_reliability/sim` |

## Wiring (adapter J1 to ULX3S)

| Adapter J1 | Signal | ULX3S | FPGA ball | Direction |
|-----------:|--------|-------|-----------|-----------|
| 23 | `ro_rstb` | gn15 | P16 | FPGA -> chip |
| 24 | `ro_101_gate` | gn16 | M17 | FPGA -> chip |
| 25 | `ro_13_gate` | gn17 | L17 | FPGA -> chip |
| 35 | `ro_101_vout` | gn18 | H17 | chip -> FPGA |
| 36 | `ro_13_vout` | gp27 | D13 | chip -> FPGA |
| 26, 27 | `ro_101_df`, `ro_101_ds` | 1.2 V | | |
| 31, 32 | `ro_13_df`, `ro_13_ds` | 1.2 V | | |
| 28, 33 | `ro_101_ex`, `ro_13_ex` | open | | |

Plus the SPI (`sclk`, `mosi`, `cs`, J1 pins 6, 4, 8) and `adc_rst` (pin 17), wired as for the ADC
measurement (the SPI on gp21..gp24, `adc_rst` on gn26).

- `df`/`ds` are isolated in oscillation mode; they are tied to 1.2 V only so they do not float.
- `ex` is the DUT output (a node of the ring) on an analog pad. A wire on it holds the pad side of the
  587 ohm protection resistor near mid-supply, which cuts the swing of the DUT output and changes the
  frequency (either way, see the report). Keep it open.
- The analog pins (`df`, `ds`, `ex`) go straight to 1.2 V transistors. Never connect them to a 3.3 V
  FPGA pin.
- ROVDD and RO2VDD come from the 1.2 V regulator through JP3 and JP4; a bench supply on those jumpers
  sweeps the supply of each ring.
- The GPIO peripheral of the SoC is 16 pins wide now (gp5..gp20), to free the gn pins.

## Measure

```bash
source ~/init_openlane.sh; source ~/init_riscv-multi.sh; source ~/init_firtool_1_30.sh
cd fpga/ULX3S && make verilog && make bitstream && make program   # SoC with rocounter (check the .bit timestamp:
cd ../../software/RISCVConsoleCode && make elf && cd ..              #  a missing tool does not fail the build)
ro/ro_run.sh ro/results/<dir>                                        # -> runN.uart.log, versions.txt
```

The firmware (`test()` in `RISCVConsoleCode/src/test.c`) runs, all from `src/ro.c`:
- `ro_test()`: holds `adc_rst` high (the chip SPI is cleared while it is low), writes the configuration
  of both rings, releases `RO_RST_B` and counts for 1 s: both rings open (expect 0), RO101 alone, RO13
  alone, both (5 times). Prints edges, pad frequency and ring frequency (x32).
- `ro_diag()`: `RO_RST_B` low, header off, footer off, duty cycle, restarts in several ways (100 ms gates).
- `ro_scan()`: the 16 values of the RO register, with `DUT_gate` 0 and 1.

The ADC capture is disabled (`TEST_ADC=0`): it would release `adc_rst` and clear the RO configuration.

## Report

The simulation side, in `asicone_sg13g2/ro_reliability` (see `sim/README.md` there):

```bash
python3 sim/ro_sim.py board                          # schematic -> sim/outputs/board/sim.csv
source ~/init_openlane.sh && sim/pex/run_all.sh      # post-layout -> sim/pex/outputs/pex_{summary,startup}.csv
cp sim/outputs/board/sim.csv sim/pex/outputs/pex_summary.csv sim/pex/outputs/pex_startup.csv \
   ~/Documents/SymbioticEDA/RISCVConsole/software/ro/results/<dir>/
```

Then here:

```bash
make -C report RESULTS=../results/ro_20261029   # -> results/ro_20261029/report/report.pdf
```

Without `run*.uart.log` the report is the prediction only; without `pex_summary.csv` it has no
post-layout comparison.
