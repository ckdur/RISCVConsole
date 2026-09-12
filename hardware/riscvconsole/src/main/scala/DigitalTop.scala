package riscvconsole

import chipyard.DigitalTop
import org.chipsalliance.cde.config.Parameters

// The current DigitalTop now supports the new devices
class DigitalTop (implicit p: Parameters) extends chipyard.DigitalTop()(p)
  with riscvconsole.devices.codec.HasPeripheryCodec
  with riscvconsole.devices.fft.HasPeripheryFFT
