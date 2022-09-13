module RealGCDBB (
  input i_clk,
  input i_rst,
  input [15:0] i_a,
  input [15:0] i_b,
  input i_in_valid,
  output o_in_ready,
  output o_out_valid,
  output [15:0] o_c
);
  
  reg [15:0] x;
  reg [15:0] y;
  reg p;
  
  // "x" and "y" logic
  always @(posedge i_clk) begin
    if(p) begin
      if(x > y) begin
        x <= y;
        y <= x;
      end else begin
        y <= y - x;
      end
    end else if(i_in_valid && !p) begin
      x <= i_a;
      y <= i_b;
    end
  end
  
  // "p" logic
  always @(posedge i_clk) begin
    if(i_rst) begin
      p <= 1'b0;
    end else if(o_out_valid) begin
      p <= 1'b0;
    end else if(i_in_valid && !p) begin
      p <= 1'b1;
    end
  end
  
  // Outputs assignments
  assign o_c = x;
  assign o_out_valid = y == 16'd0 && p;
  assign o_in_ready = !p;

endmodule
