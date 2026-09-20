# Install the utilities - ULX3S toolchain

Please choose an install folder. For the purposes of this guide, we will use the `ULX3S_INSTALL_DIR` variable:

```bash
export ULX3S_INSTALL_DIR=${HOME}/local
export PATH=$PATH:${ULX3S_INSTALL_DIR}/bin
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${ULX3S_INSTALL_DIR}/lib:${ULX3S_INSTALL_DIR}/lib64
```

## Yosys

```bash
git clone https://github.com/YosysHQ/yosys.git
cd yosys
git submodule update --init --recursive
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=${ULX3S_INSTALL_DIR}
make -j$(nproc)
make install
```

## sv-elab AKA yosys-slang (For System-verilog support)

```bash
git clone https://github.com/povik/sv-elab.git
cd sv-elab
git submodule update --init --recursive
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=${ULX3S_INSTALL_DIR}
make -j$(nproc)
make install
```

## NextPNR (nextpnr-ecp5) and ecppack

```bash
git clone --recursive https://github.com/YosysHQ/prjtrellis.git
cd prjtrellis/libtrellis
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=${ULX3S_INSTALL_DIR}
make -j$(nproc)
make install

git clone https://github.com/YosysHQ/nextpnr.git
cd nextpnr
git submodule update --init --recursive
mkdir -p build && cd build
cmake .. -DARCH=ecp5 -DTRELLIS_INSTALL_PREFIX=${ULX3S_INSTALL_DIR} -DCMAKE_INSTALL_PREFIX=${ULX3S_INSTALL_DIR}
make -j$(nproc)
sudo make install
```

## fujprog (Can be done also with OpenFPGALoader)

```bash
git clone https://github.com/kost/fujprog.git
cd fujprog
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=${ULX3S_INSTALL_DIR}
make -j$(nproc)
make install
```

