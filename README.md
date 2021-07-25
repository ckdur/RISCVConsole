# RISCV Console - An attempt to do a game console

This project contains a chipyard-based hardware compilation for the ULX3S board.
It runs linux until certain point, and only supports bootrom bootloaders.
The intention is probably run some programs to do graphics via HDMI.

## Instructions

Clone using:

```shell
git clone https://github.com/ckdur/RISCVconsole.git
cd RISCVconsole
git submodule update --init
cd hardware/chipyard
./scripts/init-submodules-no-riscv-tools.sh
```

To create the .bit file, please run:

```shell
export RISCV=/path/to/riscv32imac
cd fpga/ULX3S
make pack
sudo make program
```

## Linux

For linux compiling, we offer also a repository named `linux-custom`:

```shell
git clone https://github.com/ckdur/linux-custom.git
cd linux-custom
git submodule update --init --recursive

# Creating buildroot
export XLEN=32
export RISCV=/path/to/riscv32imac
./run_buildroot.sh

# Creating linux
./run_linux.sh

# Creating OpenSBI
./run_opensbi.sh
```

From here, we format an SD card, and create a partition with the bootloader:

```shell
git clone https://github.com/tmagik/gptfdisk.git
cd gptfdisk
make all
sudo ./gptfdisk /dev/sdX
```

Steps:
- Create GPT partition (n)
- Can be anywhere, but the size has to be less than 32M (or 65535 sectors)
- Use code 5202 for identify the partition as the bootloader (Stage 2 bootloader)

Then, copy the firmware of the opensbi to the partition

```shell
cd linux-custom
sudo dd if=./opensbi/build/platform/ratona/firmware/fw_payload.bin of=/dev/sdX1 conv=fsync bs=4096
```
