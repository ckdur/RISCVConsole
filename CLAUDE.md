# RISCVConsole: notes for Claude

RISC-V SoC (Chipyard) for FPGA boards. Besides the SoC, this repo hosts the bench measurement of the
5-bit SAR ADC of the ASICONE chip (IHP sg13g2): the ULX3S runs RISCVConsoleCode, which drives the
ADC on the adapter board and captures its codes.

## Build environment
- Toolchain: `source ~/init_riscv-multi.sh`; firtool: `source ~/init_firtool_1_30.sh`.
- Edit the Scala sources in `hardware/chipyard/generators/riscvconsole` (the copy the build reads),
  then mirror the change to `hardware/riscvconsole` (the tracked one).

## ADC measurement (this repo)
- `software/mso/`: MSO DDS stimulus (`mso_dds.cpp`), capture scripts (`adc_run.sh`, `adc_sweep.sh`),
  analysis (`adc_analyze.py`), the PDF report (`report/`), the front-end fix proposal (`pcbfix/`) and
  the data (`results/sweep_20261022/`). How to run it: `software/mso/README.md`.
- Firmware: `software/RISCVConsoleCode` (submodule, branch `ihp`), ADC code in `src/adc.c`.
- SoC side: `hardware/riscvconsole/src/main/scala/devices/adcfifo/adcfifo.scala` (ULX3S harness in
  `fpga/ulx3s/`).
- The report compares with the simulation through `results/sweep_20261022/sim_summary.json`, written by
  `saradc_auto/sim/report/make_sim_report.py` (copy it here, then `make -C software/mso/report`).

## Where the other files are
All under `~/Documents/SymbioticEDA/`.

### Chip: `asicone_sg13g2/` (github ckdur/asicone_sg13g2)
- `chip/`: top-level flow of the fabricated chip `asicone_202508`: `rtl/asicone_202508.v` (the ADC is
  instance `adc` of `SARADC`), `padring/`, `sealring/`, `pnr/outputs/`, `signoff/outputs/`.
- Fabricated layout: `chip/signoff/outputs/asicone_202508.gds` (2025-09-12; `.fill.gds` with fill);
  the `SARADC` cell (566.44 x 361.08 um) sits at (265, 220). Netlist `asicone_202508.spice`.
- Pinout / bonding: `asicone_sg13g2/QFN48.svg`. Other blocks: `digital/` (SPI), `ro_reliability/`
  (ring oscillators), `lib/` (sg13g2 custom cells and IO).
- Caution: the chip flow reads `saradc_auto/pnr/outputs` and `saradc_auto/signoff/outputs`, which now
  hold the new hierarchical ADC layout. Re-running the chip signoff no longer gives the fabricated chip.

### ADC: `asicone_sg13g2/saradc_auto/` (submodule, github ckdur/saradc_auto)
Read its `CLAUDE.md` first; it holds the current state.
- Design: `rtl/` (`saradc.v`, `sar_logic_wreset.v`, analog wrappers; `rtl/hier/` for the
  hierarchical flow), custom cells in `cells/` (`SARADC_CELL_INVX16_ASCAP` unit cap,
  `SARADC_CELL_INVX0_ASSW` switch, `sg13g2f` floating-body standard cells).
- Flows: `syn/`, `pnr/` (hierarchical, current), `pnr_legacy/` (the flat flow of the tapeout),
  `signoff/`.
- Taped-out ADC results (git-ignored, never delete): `signoff/outputs.tapeout/SARADC.gds`,
  `pnr_legacy/outputs.tapeout/` (DEF, CDL), `syn/outputs.tapeout/`.
- Simulation: `src/adc_char.py`, `sim/` (`sim/README.md`, report in `sim/report/`); extraction in
  `pex/` (`pex/README.md`); DAC wiring analysis in `docs/dac_wiring.md`.

### Adapter board: `pcbs/IHP130_ASICONE_ADP_QFN48_0p5Q_Adapter/` (gitlab.symbioticeda.com pcbs)
- KiCad project: `IHP130_ASICONE_ADP_QFN48_0p5Q_Adapter.kicad_{pro,sch,pcb}`, libraries in `Libs/`,
  BOM and placement in the `*_BOM*.xlsx` / `*_CPL*.xlsx` files.
- Board conditions used everywhere: VDD 1.2 V, VREFP 0.8 V, VREFN 0.4 V, input common mode 0.6 V,
  ADC clock 10 MHz (7 clocks per conversion). The front end (LMV358 op-amps, fitted GBW ~1.4 MHz)
  limits the high-frequency ENOB and causes the measured H2; see `software/mso/report/frontend.tex`
  and `software/mso/pcbfix/pcb_fix.tex`.

## Conventions
- Commit only when the user asks; new work goes on a branch (the ADC work is on
  `adc-sim-comparison`). End commit messages with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
