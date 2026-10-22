VMMSO_SDK ?= $(abspath ../../../VmMsoOpenSDK)

CXXFLAGS = -O2 -std=c++17 -Wall -I $(VMMSO_SDK)/include
LDFLAGS = -L $(VMMSO_SDK)/build -Wl,-rpath,$(VMMSO_SDK)/build -lvmmso -lpthread

mso_dds: mso_dds.cpp
	$(CXX) $(CXXFLAGS) -o $@ $< $(LDFLAGS)

.PHONY: clean
clean:
	rm -f mso_dds
