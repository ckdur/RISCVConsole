#!/bin/bash
git submodule update --init
cd hardware/chipyard/
./scripts/init-submodules-no-riscv-tools.sh
cd ../../
THISPWD=$(pwd)
git -C ./hardware/chipyard/generators/rocket-chip-blocks apply $(THISPWD)/rocket-chip-blocks.patch

