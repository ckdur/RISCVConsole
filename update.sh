#!/bin/bash
git submodule update --init
cd hardware/chipyard
./scripts/init-submodules-no-riscv-tools.sh
cd ../..

