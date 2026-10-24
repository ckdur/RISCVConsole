# ADC characterization with the MSO (ASICONE SARADC)

Measurement of the 5-bit SAR ADC of the ASICONE chip on the IHP130 QFN48 adapter board: the DDS of
the VmMso scope drives the board input, RISCVConsoleCode captures 65536 ADC samples into `adc_buf`,
and the buffer is read back through JTAG. The report compares the result with the transistor-level
simulation of `saradc_auto` (`asicone_sg13g2/saradc_auto/repro/README.md`).

## Requirements

- VmMsoOpenSDK built in `../../../VmMsoOpenSDK` (or `make VMMSO_SDK=...`).
- RISC-V toolchain (`source ~/init_riscv-multi.sh`), openocd, and the firmware
  `../RISCVConsoleCode` (submodule, branch `ihp`, commit `0ef7961` "Adding the ADC fast sampling"),
  built with `make -C ../RISCVConsoleCode elf` into `../RISCVConsoleCode/build/out.elf`.
- Board UART on `/dev/ttyUSB1` (`UART_DEV=...` otherwise), JTAG through `../ftdi.cfg`.
- Python 3 with numpy and matplotlib; LaTeX (latexmk, siunitx, booktabs) for the reports.

## Measure

```bash
make                                   # builds mso_dds
./adc_sweep.sh results/sweep_YYYYMMDD  # ramp + sines at 1 10 50 100 200 300 500 650 kHz
```

The defaults are those of `results/sweep_20261022`: ramp 700 mV pk-pk at 450 mV, sines 360 mV pk-pk
at 565 mV, all frequencies coherent with the capture (odd prime number of cycles).
`adc_run.sh` does a single capture, `adc_analyze.py ramp|sine <prefix>` analyzes one.

## Reports

```bash
make -C report RESULTS=../results/sweep_20261022   # -> results/sweep_20261022/report/report.pdf
make -C pcbfix                                      # -> pcbfix/pcb_fix.pdf (front-end fix proposal)
```

The comparison section needs `results/<dir>/sim_summary.json`, written by
`saradc_auto/sim/report/make_sim_report.py` (step `report` of `saradc_auto/repro/char.sh`); without it
the report is built without the comparison.
