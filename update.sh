#!/bin/bash

# Just update the submodules that are necesary
git submodule update --init
(cd hardware/chipyard && ./scripts/init-submodules-no-riscv-tools.sh)

# Link the things
(cd hardware/chipyard && git apply ../../build.sbt.patch)
(cd hardware/chipyard/fpga && ln -sf ../../fpga-shells fpga-shells-new)
(cd hardware/chipyard/generators && ln -sf ../../riscvconsole riscvconsole)
