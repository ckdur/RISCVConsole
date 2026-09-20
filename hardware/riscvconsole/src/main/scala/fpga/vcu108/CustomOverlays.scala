package riscvconsole.fpga.vcu108

import chisel3._

import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.tilelink.{TLInwardNode, TLAsyncCrossingSink}
import freechips.rocketchip.prci._
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxvcu108mig.{XilinxVCU108MIGPads, XilinxVCU108MIGParams, XilinxVCU108MIG}

//case object ExtIntAdptIfOverlayKey extends Field[Seq[DesignPlacer[GPIODirectXilinxDesignInput, GPIOShellInput, GPIODirectXilinxOverlayOutput]]](Nil)
//case object ExtSPIOverlayKey extends Field[Seq[DesignPlacer[GPIODirectXilinxDesignInput, GPIOShellInput, GPIODirectXilinxOverlayOutput]]](Nil)
//case object ExtSPICFGOverlayKey extends Field[Seq[DesignPlacer[GPIODirectXilinxDesignInput, GPIOShellInput, GPIODirectXilinxOverlayOutput]]](Nil)

class SysClock2VCU108PlacedOverlay(val shell: VCU108ShellBasicOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 300, jitterPS = 50)(ValName(name)) }

  // ug1066 page 40
  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "G22")
    shell.xdc.addPackagePin(io.n, "G21")
    shell.xdc.addIOStandard(io.p, "LVDS")
    shell.xdc.addIOStandard(io.n, "LVDS")
  } }
}
class SysClock2VCU108ShellPlacer(shell: VCU108ShellBasicOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VCU108ShellBasicOverlays]
{
    def place(designInput: ClockInputDesignInput) = new SysClock2VCU108PlacedOverlay(shell, valName.name, designInput, shellInput)
}

case object VCU108DDR2Size extends Field[BigInt](0x40000000L * 2) // 2GB
class DDR2VCU108PlacedOverlay(val shell: VCU108FPGATestHarness, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxVCU108MIGPads](name, designInput, shellInput)
{
  val size = p(VCU108DDRSize)

  val migParams = XilinxVCU108MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxVCU108MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := designInput.wrangler := ddrUI

  // since this uses a separate clk/rst need to put an async crossing
  val asyncSink = LazyModule(new TLAsyncCrossingSink())
  val migClkRstNode = BundleBridgeSource(() => new Bundle {
    val clock = Output(Clock())
    val reset = Output(Bool())
  })
  val topMigClkRstIONode = shell { migClkRstNode.makeSink() }

  def overlayOutput = DDROverlayOutput(ddr = mig.node)
  def ioFactory = new XilinxVCU108MIGPads(size)

  InModuleBody {
    ioNode.bundle <> mig.module.io

    // setup async crossing
    asyncSink.module.clock := migClkRstNode.bundle.clock
    asyncSink.module.reset := migClkRstNode.bundle.reset
  }

  shell { InModuleBody {
    require (shell.sys_clock2.get.isDefined, "Use of DDRVCU108Overlay depends on SysClock2VCU108Overlay")
    val (sys, _) = shell.sys_clock2.get.get.overlayOutput.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)

    // connect the async fifo sync to sys_clock2
    topMigClkRstIONode.bundle.clock := sys.clock
    topMigClkRstIONode.bundle.reset := sys.reset

    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.c0_ddr4_ui_clk
    ui.reset := /*!port.mmcm_locked ||*/ port.c0_ddr4_ui_clk_sync_rst
    port.c0_sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset // pllReset
    port.c0_ddr4_aresetn := !(ar.reset.asBool)

    // This was just copied from the SiFive example, but it's hard to follow.
    // The pins are emitted in the following order:
    // adr[0->13], we_n, cas_n, ras_n, bg, ba[0->1], reset_n, act_n, ck_c, ck_t, cke, cs_n, odt, dq[0->63], dqs_c[0->7], dqs_t[0->7], dm_dbi_n[0->7]
    val allddrpins = Seq(
      "AM27",	"AT25",	"AN25",	"AN26",	"AR25",	"AU28",	"AU27",	"AR28",	"AP25",	"AM26",	"AP26",	"AN28",	"AR27",	"AP28",
      "AL27",	"AP27",	"AM28",	"AV28",
      "AU26",	"AV26",
      "BF40",	"AW28",	"AT27",	"AT26",	"AY29",	"BB29",	"BF29",
      "BE30",	"BE33",	"BD30",	"BD33",	"BD31",	"BC33",	"BD32",	"BC31",	"BA31",	"AY33",	"BA30",	"AW31",	"AW32",	"BB33",	"AY32",	"BA32",
      "AT31",	"AV31",	"AV30",	"AU33",	"AU31",	"AU32",	"AW30",	"AU34",	"AT29",	"AT34",	"AT30",	"AR33",	"AR30",	"AN30",	"AP30",	"AN31",
      "BF34",	"BF36",	"BC35",	"BE37",	"BE34",	"BD36",	"BF37",	"BC36",	"BD37",	"BE38",	"BD38",	"BD40",	"BB38",	"BB39",	"BC39",	"BC38",
      "AW40",	"BA40",	"AY39",	"AY38",	"AY40",	"BA39",	"BB36",	"BB37",	"AV38",	"AU38",	"AU39",	"AW35",	"AU40",	"AV40",	"AW36",	"AV39",
      "BF31",	"BA34",	"AV29",	"AP32",	"BF35",	"BF39",	"BA36",	"AW38",
      "BF30",	"AY34",	"AU29",	"AP31",	"BE35",	"BE39",	"BA35",	"AW37",
      "BE32",	"BB31",	"AV33",	"AR32",	"BC34",	"BE40",	"AY37",	"AV35",
    )

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.c0_ddr4_ui_clk))
}

class DDR2VCU108ShellPlacer(shell: VCU108FPGATestHarness, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[VCU108FPGATestHarness] {
  def place(designInput: DDRDesignInput) = new DDR2VCU108PlacedOverlay(shell, valName.name, designInput, shellInput)
}
