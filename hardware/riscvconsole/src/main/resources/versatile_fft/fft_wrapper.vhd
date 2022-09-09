-------------------------------------------------------------------------------
-- Title      : fft_wrapper
-- Project    : DP RAM based FFT processor
-------------------------------------------------------------------------------
-- File       : fft_engine.vhd
-- Author     : Ckristian Duran ckdur.iso <at> gmail.com
-- Company    : 
-- License    : BSD
-- Created    : 2021-01-06
-- Platform   : 
-- Standard   : VHDL'93
-------------------------------------------------------------------------------
-- Description: This file just wraps the FFT engine to be implemented in Verilog.
-------------------------------------------------------------------------------
-- Copyright (c) 2021 
-------------------------------------------------------------------------------
-- Revisions  :
-- Date        Version  Author  Description
-- 2021-01-06  1.0      ckdur   Created
-------------------------------------------------------------------------------
library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
use ieee.math_real.all;
use ieee.math_complex.all;
library work;
use work.icpx.all;

entity fft_wrapper is
  generic (
    LOG2_FFT_LEN : integer := 10 );
  port (
    din       : in  std_logic_vector(ICPX_WIDTH*2-1 downto 0);
    addr_in   : in  std_logic_vector(LOG2_FFT_LEN-1 downto 0);
    wr_in     : in  std_logic;
    dout      : out std_logic_vector(ICPX_WIDTH*2-1 downto 0);
    addr_out  : in  std_logic_vector(LOG2_FFT_LEN-1 downto 0);
    ready     : out std_logic;
    busy      : out std_logic;
    start     : in  std_logic;
    rst_n     : in  std_logic;
    syn_rst_n : in  std_logic;
    clk       : in  std_logic);

end fft_wrapper;

architecture fft_wrapper_beh of fft_wrapper is
  component fft_engine
    generic (
      LOG2_FFT_LEN : integer := 8 );
    port (
      din       : in  icpx_number;
      addr_in   : in  integer;
      wr_in     : in  std_logic;
      dout      : out icpx_number;
      addr_out  : in  integer;
      ready     : out std_logic;
      busy      : out std_logic;
      start     : in  std_logic;
      rst_n     : in  std_logic;
      syn_rst_n : in  std_logic;
      clk       : in  std_logic);
  end component;
  
  signal addr_in_enc : integer;
  signal addr_out_enc : integer;
  signal din_enc : icpx_number;
  signal dout_enc : icpx_number;
  
  begin
  
  addr_in_enc <= to_integer(unsigned(addr_in));
  addr_out_enc <= to_integer(unsigned(addr_out));
  dout <= icpx2stlv(dout_enc);
  din_enc <= stlv2icpx(din);
  
  fft_1 : fft_engine
    generic map (
      LOG2_FFT_LEN => LOG2_FFT_LEN)
    port map (
      din       => din_enc,
      addr_in   => addr_in_enc,
      wr_in     => wr_in,
      dout      => dout_enc,
      addr_out  => addr_out_enc,
      ready     => ready,
      busy      => busy,
      start     => start,
      rst_n     => rst_n,
      syn_rst_n => syn_rst_n,
      clk       => clk);
end fft_wrapper_beh;

