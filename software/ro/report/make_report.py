#!/usr/bin/env python3
"""Report of the expected results of the ring oscillators of ASICONE.

  make_report.py <results_dir>

Reads from <results_dir>:
  sim.csv           schematic prediction (ro_reliability/sim/ro_sim.py)
  pex_summary.csv   post-layout prediction, optional (ro_reliability/sim/pex/run_all.sh)
  pex_startup.csv   start-up of the all-zero starts, optional (same script, edges.py)
  run*.uart.log     bench logs, optional (../ro_run.sh)
writes the figures and the LaTeX macros into <results_dir>/report, then compiles report.tex there
with latexmk. Without the bench logs the report is the prediction only.
"""
import csv
import glob
import os
import re
import statistics
import shutil
import subprocess
import sys

import matplotlib
matplotlib.use("pdf")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
FIG_W = 6.0
BUS_HZ = 50_000_000      # ULX3S bus clock, also the default gate (1 s)
DIV = 32                 # on-chip divider before the output pad
NAMES = {101: "RO101", 13: "RO13"}

plt.rcParams.update({
    "font.size": 8, "axes.titlesize": 8, "axes.labelsize": 8, "legend.fontsize": 7,
    "xtick.labelsize": 7, "ytick.labelsize": 7, "axes.grid": True, "grid.alpha": 0.3,
    "lines.linewidth": 1.2, "lines.markersize": 4, "savefig.bbox": "tight",
})


def load(path):
    rows = []
    with open(path) as f:
        for r in csv.DictReader(f):
            for k in ("n", "temp", "pad", "div"):
                r[k] = int(r[k])
            for k in ("vdd", "cext", "f_ring", "f_pad", "i_avg"):
                r[k] = float(r[k])
            rows.append(r)
    return rows


def pick(rows, **kw):
    out = [r for r in rows if all(r[k] == v for k, v in kw.items())]
    return out


def one(rows, **kw):
    base = dict(corner="mos_tt", vdd=1.2, temp=27, pad=1, cext=0.0, div=0)
    base.update(kw)
    m = pick(rows, **base)
    return m[0] if m else None


def save(fig, build, name):
    os.makedirs(os.path.join(build, "fig"), exist_ok=True)
    fig.savefig(os.path.join(build, "fig", name + ".pdf"))
    plt.close(fig)


def fig_vdd(rows, build):
    fig, axs = plt.subplots(1, 2, figsize=(FIG_W, 2.4))
    for ax, n in zip(axs, (13, 101)):
        pts = sorted([r for r in rows if r["n"] == n and r["corner"] == "mos_tt" and r["temp"] == 27
                      and r["pad"] == 1 and r["cext"] == 0 and r["div"] == 0], key=lambda r: r["vdd"])
        v = [r["vdd"] for r in pts]
        ax.plot(v, [r["f_ring"] / 1e6 for r in pts], "o-", color="C0")
        ax.set_xlabel("RO supply (V)")
        ax.set_ylabel("Ring frequency (MHz)")
        ax.set_title(NAMES[n])
        sec = ax.secondary_yaxis("right", functions=(lambda x: x / DIV, lambda x: x * DIV))
        sec.set_ylabel("Pad frequency (MHz)")
    fig.tight_layout()
    save(fig, build, "vdd")


def fig_load(rows, build):
    fig, ax = plt.subplots(figsize=(FIG_W, 2.2))
    for n, c in ((13, "C0"), (101, "C1")):
        ref = one(rows, n=n)
        pts = [ref] + sorted(pick(rows, group="load", n=n), key=lambda r: r["cext"])
        ax.plot([r["cext"] * 1e12 for r in pts], [r["f_ring"] / ref["f_ring"] for r in pts], "o-",
                color=c, label=NAMES[n])
    ax.set_xlabel("Capacitance on the extra_load pin, outside the pad (pF)")
    ax.set_ylabel("Frequency / pin open")
    ax.legend()
    save(fig, build, "load")


def mhz(f, d=1):
    return f"{f / 1e6:.{d}f}"


def khz(f):
    return f"{f / 1e3:.0f}"


def write_tex(rows, build, results_dir):
    L = []
    def macro(name, val):
        L.append(f"\\newcommand{{\\{name}}}{{{val}}}")

    macro("ResultsDir", os.path.basename(os.path.normpath(results_dir)).replace("_", "\\_"))
    for n, tag in ((13, "A"), (101, "B")):
        tt, ss, ff = (one(rows, n=n, corner=k) for k in ("mos_tt", "mos_ss", "mos_ff"))
        nopad = one(rows, n=n, pad=0)
        dtt = one(rows, n=n, div=1)
        macro(f"Ring{tag}", mhz(tt["f_ring"]))
        macro(f"RingMin{tag}", mhz(min(r["f_ring"] for r in pick(rows, group="corner", n=n, pad=1))))
        macro(f"RingMax{tag}", mhz(max(r["f_ring"] for r in pick(rows, group="corner", n=n, pad=1))))
        macro(f"Pad{tag}", mhz(tt["f_pad"], 3))
        macro(f"PadDiv{tag}", mhz(dtt["f_pad"], 3) if dtt else "--")
        macro(f"Count{tag}", f"{round(tt['f_pad']):,}".replace(",", "\\,"))
        macro(f"Cur{tag}", f"{tt['i_avg'] * 1e3:.2f}")
        macro(f"NoPad{tag}", mhz(nopad["f_ring"]))
        macro(f"PadLoss{tag}", f"{(1 - tt['f_ring'] / nopad['f_ring']) * 100:.0f}")
        macro(f"Stage{tag}", f"{1e12 / (2 * n * tt['f_ring']):.0f}")
        macro(f"SsPct{tag}", f"{(ss['f_ring'] / tt['f_ring'] - 1) * 100:+.0f}")
        macro(f"FfPct{tag}", f"{(ff['f_ring'] / tt['f_ring'] - 1) * 100:+.0f}")
        v = sorted(pick(rows, n=n, corner="mos_tt", temp=27, pad=1, cext=0.0, div=0), key=lambda r: r["vdd"])
        # Sensitivity to the supply around 1.2 V
        lo = [r for r in v if abs(r["vdd"] - 1.1) < 1e-6][0]
        hi = [r for r in v if abs(r["vdd"] - 1.3) < 1e-6][0]
        macro(f"VddSens{tag}", f"{(hi['f_ring'] - lo['f_ring']) / 0.2 / tt['f_ring'] * 100 / 10:.1f}")
        t0, t75 = one(rows, n=n, temp=0), one(rows, n=n, temp=75)
        macro(f"TempSens{tag}", f"{(t75['f_ring'] - t0['f_ring']) / 75 / tt['f_ring'] * 100:.2f}")
    a, b = one(rows, n=13), one(rows, n=101)
    macro("RatioAB", f"{a['f_ring'] / b['f_ring']:.2f}")
    ratios = [one(rows, n=13, corner=k)["f_ring"] / one(rows, n=101, corner=k)["f_ring"]
              for k in ("mos_tt", "mos_ss", "mos_ff", "mos_sf", "mos_fs")]
    macro("RatioMin", f"{min(ratios):.2f}")
    macro("RatioMax", f"{max(ratios):.2f}")
    macro("BusHz", f"{BUS_HZ:,}".replace(",", "\\,"))

    # Corner table
    T = ["\\begin{tabular}{l" + "rrrr" * 2 + "}", "\\toprule",
         " & \\multicolumn{4}{c}{RO13} & \\multicolumn{4}{c}{RO101} \\\\",
         "\\cmidrule(lr){2-5}\\cmidrule(lr){6-9}",
         "Case & Ring & Pad & Count & $I_{DD}$ & Ring & Pad & Count & $I_{DD}$ \\\\",
         " & (MHz) & (kHz) & (1 s) & (mA) & (MHz) & (kHz) & (1 s) & (mA) \\\\", "\\midrule"]
    cases = [("TT", dict(corner="mos_tt")), ("SS", dict(corner="mos_ss")), ("FF", dict(corner="mos_ff")),
             ("SF", dict(corner="mos_sf")), ("FS", dict(corner="mos_fs")),
             ("TT, no pad on extra\\_load", dict(pad=0)),
             ("TT, \\SI{0}{\\celsius}", dict(temp=0)), ("TT, \\SI{75}{\\celsius}", dict(temp=75)),
             ("TT, \\SI{2}{pF} on the pin", dict(cext=2e-12)), ("TT, \\SI{10}{pF} on the pin", dict(cext=10e-12))]
    for label, kw in cases:
        cells = [label]
        for n in (13, 101):
            r = one(rows, n=n, **kw)
            cells += [mhz(r["f_ring"]), khz(r["f_pad"]), f"{round(r['f_pad']):,}".replace(",", "\\,"),
                      f"{r['i_avg'] * 1e3:.2f}"] if r else ["--"] * 4
        T.append(" & ".join(cells) + " \\\\")
    T += ["\\bottomrule", "\\end{tabular}"]
    open(os.path.join(build, "table_cases.tex"), "w").write("\n".join(T) + "\n")

    # VDD table
    T = ["\\begin{tabular}{r" + "rrr" * 2 + "}", "\\toprule",
         " & \\multicolumn{3}{c}{RO13} & \\multicolumn{3}{c}{RO101} \\\\",
         "\\cmidrule(lr){2-4}\\cmidrule(lr){5-7}",
         "Supply (V) & Ring (MHz) & Pad (kHz) & $I_{DD}$ (mA) & Ring (MHz) & Pad (kHz) & $I_{DD}$ (mA) \\\\",
         "\\midrule"]
    for v in sorted({r["vdd"] for r in rows}):
        cells = [f"{v:.1f}"]
        for n in (13, 101):
            r = one(rows, n=n, vdd=v)
            cells += [mhz(r["f_ring"]), khz(r["f_pad"]), f"{r['i_avg'] * 1e3:.2f}"] if r else ["--"] * 3
        T.append(" & ".join(cells) + " \\\\")
    T += ["\\bottomrule", "\\end{tabular}"]
    open(os.path.join(build, "table_vdd.tex"), "w").write("\n".join(T) + "\n")

    # Console output expected from ro_test() (src/ro.c) at TT, 1.2 V, gate of 1 s
    def line(n, f):
        c = round(f)
        return f"  {NAMES[n]}: {c} edges, pad {c} Hz, ring {c * DIV} Hz"
    fa, fb = one(rows, n=13)["f_pad"], one(rows, n=101)["f_pad"]
    head = f"(gate {BUS_HZ} cycles at {BUS_HZ} Hz):"
    C = ["RO test",
         "Both off " + head, line(101, 0), line(13, 0),
         "RO101 on " + head, line(101, fb), line(13, 0),
         "RO13 on " + head, line(101, 0), line(13, fa),
         "Both on " + head]
    for _ in range(5):
        C += [line(101, fb), line(13, fa)]
    C += ["RO test done"]
    open(os.path.join(build, "console.txt"), "w").write("\n".join(C) + "\n")

    open(os.path.join(build, "results.tex"), "w").write("\n".join(L) + "\n")


# ---------------------------------------------------------------- measurement and post-layout

def parse_runs(results):
    """Bench logs: ro_test blocks (pad edges in 1 s), ro_diag restarts and checks, ro_scan table."""
    m = {"alone": {}, "both": {101: [], 13: []}, "restarts": {101: [], 13: []}, "checks": {}, "scan": {},
         "runs": []}
    for path in sorted(glob.glob(os.path.join(results, "run*.uart.log"))):
        txt = open(path).read()
        m["runs"].append(os.path.basename(path))
        block = None
        for line in txt.splitlines():
            h = re.match(r"(Both off|RO101 on|RO13 on|Both on) \(gate (\d+) cycles", line)
            if h:
                block = h.group(1)
                continue
            r = re.match(r"\s+RO(101|13): (\d+) edges", line)
            if r and block:
                n, c = int(r.group(1)), int(r.group(2))
                if block == "Both on":
                    m["both"][n].append(c)
                elif block == f"RO{n} on":
                    m["alone"].setdefault(n, []).append(c)
                continue
            d = re.match(r"\s+(.+?)\s*: RO101 (\d+), RO13 (\d+) edges/(\d+) cycles; duty (\d+), (\d+)", line)
            if d:
                g = int(d.group(4))
                m["checks"][d.group(1)] = (int(d.group(2)) * 50e6 / g, int(d.group(3)) * 50e6 / g,
                                           int(d.group(5)) / 10, int(d.group(6)) / 10)
                block = None
                continue
            for a, b in (re.findall(r"\b(\d{5,})/(\d{5,})\b", line) if re.match(r"\s+\d+/\d+", line) else []):
                m["restarts"][101].append(int(a) * 10)
                m["restarts"][13].append(int(b) * 10)
            sc = re.match(r"\s+0x([0-9a-f]+): (\d+)/(\d+) \| (\d+)/(\d+)", line)
            if sc:
                m["scan"][int(sc.group(1), 16)] = tuple(int(x) * 10 for x in sc.groups()[1:])
            if line.startswith(("RO test done", "RO diagnostics", "RO config scan")):
                block = None
    return m if m["runs"] else None


def load_pex(results):
    path = os.path.join(results, "pex_summary.csv")
    if not os.path.exists(path):
        return None
    rows = []
    for r in csv.DictReader(open(path)):
        r["vdd"], r["temp"] = float(r["vdd"]), float(r["temp"])
        r["pex"], r["f_ring"], r["f_out"] = int(r["pex"]), float(r["f_ring"]), float(r["f_out"])
        rows.append(r)
    return rows


def pex1(table, cell, **kw):
    base = dict(corner="mos_tt", vdd=1.2, temp=27.0, start="footer", pex=1)
    base.update(kw)
    for r in table:
        if r["cell"] == cell and all(r[k] == v for k, v in base.items()):
            return r
    return None


def fig_vdd_pex(pex, meas13, build):
    pts = sorted([r for r in pex if r["cell"] == "top_13" and r["corner"] == "mos_tt" and r["temp"] == 27
                  and r["pex"] == 1 and r["start"] == "footer"], key=lambda r: r["vdd"])
    fig, ax = plt.subplots(figsize=(FIG_W, 2.3))
    ax.plot([r["vdd"] for r in pts], [r["f_ring"] / 1e6 for r in pts], "o-", label="RO13, post-layout, TT")
    for k, ls in (("mos_ss", "v"), ("mos_ff", "^")):
        r = pex1(pex, "top_13", corner=k)
        if r:
            ax.plot([1.2], [r["f_ring"] / 1e6], ls, color="C0", label=f"RO13, post-layout, {k[4:].upper()}")
    ax.axhline(meas13 / 1e6, color="C3", ls="--", label="RO13, measured")
    ax.set_xlabel("RO supply (V)")
    ax.set_ylabel("Ring frequency (MHz)")
    ax.legend()
    save(fig, build, "vdd_pex")


def equiv_vdd(pex, f):
    """Supply at which the TT post-layout RO13 runs at f (linear interpolation)."""
    pts = sorted([(r["vdd"], r["f_ring"]) for r in pex if r["cell"] == "top_13" and r["corner"] == "mos_tt"
                  and r["temp"] == 27 and r["pex"] == 1 and r["start"] == "footer"])
    for (v0, f0), (v1, f1) in zip(pts, pts[1:]):
        if f0 <= f <= f1:
            return v0 + (f - f0) / (f1 - f0) * (v1 - v0)
    return None


def write_meas_tex(m, pex, rows, build, results):
    L = []
    def macro(name, val):
        L.append(f"\\newcommand{{\\{name}}}{{{val}}}")
    macro("MeasRuns", ", ".join(r.replace("_", "\\_") for r in m["runs"]))
    for n, tag in ((13, "A"), (101, "B")):
        al = m["alone"][n]
        bo = m["both"][n]
        rs = m["restarts"][n]
        macro(f"MeasAlone{tag}", f"{statistics.mean(al) / 1e6:.3f}")
        macro(f"MeasBoth{tag}", f"{statistics.mean(bo) / 1e6:.3f}")
        macro(f"MeasBothSd{tag}", f"{statistics.pstdev(bo) / statistics.mean(bo) * 100:.3f}")
        macro(f"MeasRingAlone{tag}", f"{statistics.mean(al) * DIV / 1e6:.1f}")
        macro(f"MeasRingBoth{tag}", f"{statistics.mean(bo) * DIV / 1e6:.1f}")
        macro(f"MeasRestarts{tag}", f"{len(rs)}")
        macro(f"MeasRestartSpread{tag}", f"{(max(rs) - min(rs)) / statistics.mean(rs) * 100:.2f}")
    a13, a101 = statistics.mean(m["alone"][13]), statistics.mean(m["alone"][101])
    b13, b101 = statistics.mean(m["both"][13]), statistics.mean(m["both"][101])
    macro("MeasBothShiftA", f"{(b13 / a13 - 1) * 100:+.1f}")
    macro("MeasBothShiftB", f"{(b101 / a101 - 1) * 100:+.1f}")
    macro("MeasRatio", f"{a13 / a101:.2f}")
    ch = m["checks"]
    duty = [v for k, v in ch.items() if k.startswith("both on")]
    if duty:
        macro("MeasDutyA", f"{duty[0][3]:.1f}")
        macro("MeasDutyB", f"{duty[0][2]:.1f}")
    hdr = ch.get("DUT header off")
    if hdr:
        on = ch["both on"]
        macro("HdrOffShiftA", f"{(hdr[1] / on[1] - 1) * 100:+.1f}")
        macro("HdrOffShiftB", f"{(hdr[0] / on[0] - 1) * 100:+.1f}")

    # Configuration scan table
    T = ["\\begin{tabular}{cccccrrrr}", "\\toprule",
         " & \\multicolumn{4}{c}{Register bits} & \\multicolumn{2}{c}{DUT\\_gate = 0} & \\multicolumn{2}{c}{DUT\\_gate = 1} \\\\",
         "\\cmidrule(lr){2-5}\\cmidrule(lr){6-7}\\cmidrule(lr){8-9}",
         "Value & Header & Footer & RO\\_ctl & nRO\\_ctl & RO101 & RO13 & RO101 & RO13 \\\\", "\\midrule"]
    for cfg in sorted(m["scan"]):
        v = m["scan"][cfg]
        bits = [(cfg >> 3) & 1, (cfg >> 2) & 1, (cfg >> 1) & 1, cfg & 1]
        T.append(f"\\texttt{{0x{cfg:02x}}} & " + " & ".join(map(str, bits)) + " & "
                 + " & ".join(f"{x:,}".replace(",", "\\,") for x in v) + " \\\\")
    T += ["\\bottomrule", "\\end{tabular}"]
    open(os.path.join(build, "table_scan.tex"), "w").write("\n".join(T) + "\n")

    if pex:
        fig_vdd_pex(pex, a13 * DIV, build)
        macro("HavePex", "1")
        def r(cell, **kw):
            x = pex1(pex, cell, **kw)
            return x["f_ring"] if x else float("nan")
        sch13, sch101 = one(rows, n=13)["f_ring"], one(rows, n=101)["f_ring"]
        T = ["\\begin{tabular}{lrrrr}", "\\toprule",
             " & \\multicolumn{2}{c}{RO13} & \\multicolumn{2}{c}{RO101} \\\\",
             "\\cmidrule(lr){2-3}\\cmidrule(lr){4-5}",
             "Ring frequency & MHz & meas./sim. & MHz & meas./sim. \\\\", "\\midrule"]
        cases = [("Schematic + pad, TT", sch13, sch101),
                 ("Layout, devices only, TT", r("top_13", pex=0), r("top_101", pex=0)),
                 ("Layout + wiring C, TT", r("top_13"), r("top_101")),
                 ("Layout + wiring C, SS", r("top_13", corner="mos_ss"), r("top_101", corner="mos_ss")),
                 ("Layout + wiring C, FF", r("top_13", corner="mos_ff"), r("top_101", corner="mos_ff")),
                 ("Layout + wiring C, TT, \\SI{1.0}{V}", r("top_13", vdd=1.0), r("top_101", vdd=1.0)),
                 ("Layout + wiring C, TT, all-zero start", float("nan"), r("top_101", start="uic"))]
        for label, f13, f101 in cases:
            c13 = f"{f13 / 1e6:.1f} & {a13 * DIV / f13:.2f}" if f13 == f13 else "-- & --"
            c101 = f"{f101 / 1e6:.1f} & {a101 * DIV / f101:.2f}" if f101 == f101 else "-- & --"
            T.append(f"{label} & {c13} & {c101} \\\\")
        T += ["\\midrule", f"Measured (alone) & {a13 * DIV / 1e6:.1f} & & {a101 * DIV / 1e6:.1f} & \\\\",
              "\\bottomrule", "\\end{tabular}"]
        open(os.path.join(build, "table_cmp.tex"), "w").write("\n".join(T) + "\n")
        pt13, pt101 = r("top_13"), r("top_101")
        macro("PexA", f"{pt13 / 1e6:.1f}")
        macro("PexB", f"{pt101 / 1e6:.1f}")
        macro("PexWireA", f"{(1 - pt13 / r('top_13', pex=0)) * 100:.0f}")
        macro("PexWireB", f"{(1 - pt101 / r('top_101', pex=0)) * 100:.0f}")
        macro("PexSsA", f"{r('top_13', corner='mos_ss') / 1e6:.1f}")
        macro("MeasOverPexA", f"{a13 * DIV / pt13:.2f}")
        macro("MeasOverPexB", f"{a101 * DIV / pt101:.2f}")
        ev = equiv_vdd(pex, a13 * DIV)
        macro("EquivVdd", f"{ev:.2f}" if ev else "below 0.9")
        # RO101 with RO13's slow-down: the fundamental it should have, and the number of edges
        f0 = pt101 * (a13 * DIV / pt13)
        macro("RoBFundEst", f"{f0 / 1e6:.1f}")
        macro("RoBEdges", f"{a101 * DIV / f0:.2f}")
        uic = r("top_101", start="uic")
        if uic == uic:
            macro("PexUicB", f"{uic / 1e6:.1f}")
            macro("PexUicEdges", f"{uic / pt101:.2f}")
        # Start-up of the all-zero starts (edges.py): edges at first, and when the extra ones are gone
        path = os.path.join(results, "pex_startup.csv")
        if os.path.exists(path):
            st = list(csv.DictReader(open(path)))
            for case, tag in (("top_101_mos_tt_v1.2_t27_uic", "Pex"), ("top_101_mos_tt_v1.2_t27_uic_nopex", "Dev")):
                w = [(float(x["t_end"]), float(x["f_edges"])) for x in st if x["case"] == case]
                if not w:
                    continue
                fin = r("top_101", start="uic", pex=1 if tag == "Pex" else 0)
                settle = max(t for t, f in w if f > 1.5 * fin)
                macro(f"Start{tag}First", f"{w[0][1] / 1e6:.0f}")
                macro(f"Start{tag}Edges", f"{w[0][1] / fin:.0f}")
                macro(f"Start{tag}Settle", f"{settle * 1e6:.2f}")
    open(os.path.join(build, "meas.tex"), "w").write("\n".join(L) + "\n")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    results = os.path.abspath(sys.argv[1])
    rows = load(os.path.join(results, "sim.csv"))
    build = os.path.join(results, "report")
    os.makedirs(build, exist_ok=True)
    for f in ("report.tex", "references.tex"):
        shutil.copy(os.path.join(HERE, f), build)
    fig_vdd(rows, build)
    fig_load(rows, build)
    write_tex(rows, build, results)
    m = parse_runs(results)
    if m:
        write_meas_tex(m, load_pex(results), rows, build, results)
        for f in ("measurement.tex",):
            shutil.copy(os.path.join(HERE, f), build)
    else:
        for f in ("meas.tex", "measurement.tex"):
            if os.path.exists(os.path.join(build, f)):
                os.remove(os.path.join(build, f))
    subprocess.run(["latexmk", "-pdf", "-interaction=nonstopmode", "-halt-on-error", "report.tex"],
                   cwd=build, check=True, stdout=subprocess.DEVNULL)
    print(os.path.join(build, "report.pdf"))


if __name__ == "__main__":
    main()
