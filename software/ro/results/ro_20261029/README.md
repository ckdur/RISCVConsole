# First RO measurement, 2026-10-29

ULX3S with the rocounter SoC (gate 1 s / 100 ms at 50 MHz), adapter wired as in `../../README.md`,
ROVDD and RO2VDD from the on-board 1.2 V (JP3, JP4), room temperature. Firmware RISCVConsoleCode
`src/ro.c`. The report: `make -C ../../report RESULTS=../results/ro_20261029`.

| File | What |
|------|------|
| `run1.uart.log` | `ro_test()`: both off, each alone, both on (x5) |
| `run2.uart.log` | `ro_test()` + `ro_diag()`: RO_RST_B, header/footer off, duty, restarts |
| `run3.uart.log` | `ro_diag()`: restarts from footer off, short open, header-off state |
| `run4.uart.log` | `ro_scan()`: all 16 configurations, DUT_gate 0 and 1 |
| `run5.uart.log`, `run5.jtag.log` | `ro_run.sh` with the final firmware: `ro_test()`, `ro_diag()`, `ro_scan()` |
| `versions.txt` | firmware, repository and bitstream of `run5` (runs 1-4 were captured by hand, before `ro_run.sh`) |
| `sim.csv` | schematic prediction, `ro_reliability/sim/ro_sim.py board` |
| `pex_summary.csv` | post-layout prediction, `ro_reliability/sim/pex/run_all.sh` |
| `pex_startup.csv` | edge rate against time of the RO101 all-zero starts (`edges.py`, same script) |

## Results (ring = pad x 32)

| | Measured, alone | Schematic, TT | Post-layout, TT | Post-layout, SS |
|-|-----------------|---------------|-----------------|-----------------|
| RO13 | 130.8 MHz | 223.9 | 188.8 | 169.3 |
| RO101 | 135.4 MHz | 32.3 | 27.7 | 25.0 |

- Oscillation only with configuration 0x06 / 0x0E (`RO_control`=1, `n_RO_control`=0, `DUT_Footer`=1),
  as designed. `RO_RST_B` low stops the outputs. Duty about 50 % on both pads. Repeatability about 0.1 %.
- `DUT_Header` (bit 3) has no effect (0x0E runs like 0x06). Inside the macros the header gate is on
  the `DUT_Header` pin (extraction of the chip GDS), so the chip-level connection from `R[3]`/`R[11]`
  is suspect.
- RO13 is 0.69 of the post-layout TT, below SS; the post-layout ring would run at the measured frequency
  with about 0.97 V. RO13 slows 4 % when RO101 runs: the supply path (shared regulator, JP3/JP4, one
  ground pad) is suspect.
- RO101 at 4.9x the post-layout fundamental is impossible with one edge (37 ps per stage). With RO13's
  0.69 factor its fundamental would be 19.2 MHz, and the measurement is 7.05x that: either about 7 edges
  circulate (harmonic mode) or a loop of about 14 stages oscillates. The simulation does not keep a
  harmonic mode: an all-zero start begins with 3 edges (4 without wiring C) that die within 0.75 us,
  ending at the fundamental. On the board every restart (132, six ways) gives the same frequency.
