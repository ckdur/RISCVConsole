#!/usr/bin/env python3
"""Build the ADC characterization report from a results directory made by adc_sweep.sh.

  make_report.py <results_dir> [build_dir] [--sim sim_summary.json]

Analyzes every run listed in <results_dir>/runs.csv, writes the figures (vector PDF),
results.tex (LaTeX macros and tables) and results.json into build_dir (default:
<results_dir>/report), then compiles report.tex there with latexmk.

With a transistor-level simulation of the ADC (sim_summary.json written by
saradc_auto/sim/report/make_sim_report.py, given with --sim or found in <results_dir>), the report
gets a section comparing it with the measurement in LSB-normalized terms.
"""
import csv
import json
import os
import shutil
import subprocess
import sys

import matplotlib
matplotlib.use("pdf")
import matplotlib.pyplot as plt
import matplotlib.ticker
import numpy as np

REPORT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(REPORT_DIR))
import adc_analyze as aa  # noqa: E402

# Reference palette (light mode, categorical slots in fixed order) and text inks
SERIES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"]
INK = "#0b0b0b"
INK2 = "#52514e"
GRID = "#e4e3df"

plt.rcParams.update({
    "font.family": "serif", "font.size": 8, "axes.titlesize": 9, "axes.labelsize": 8,
    "axes.edgecolor": INK2, "axes.labelcolor": INK, "axes.linewidth": 0.6,
    "xtick.color": INK2, "ytick.color": INK2, "xtick.labelsize": 7, "ytick.labelsize": 7,
    "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.5,
    "axes.spines.top": False, "axes.spines.right": False,
    "legend.frameon": False, "legend.fontsize": 7, "lines.linewidth": 1.2,
    "pdf.fonttype": 42, "figure.dpi": 150,
})
FIG_W = 5.6  # inches, \textwidth of the report


def save(fig, build, name):
    fig.tight_layout()
    fig.savefig(os.path.join(build, "fig", name + ".pdf"))
    plt.close(fig)


def fig_transfer(r, build):
    t, lsb = r["t"], r["lsb"]
    fig, ax = plt.subplots(figsize=(FIG_W, 2.6))
    v = np.concatenate([[t[0] - 2 * lsb], np.repeat(t, 2), [t[-1] + 2 * lsb]])
    c = np.concatenate([[0, 0], np.repeat(np.arange(1, aa.NCODES), 2)])[:len(v)]
    ax.plot(v, c, color=SERIES[0], label="Measured")
    ideal = t[0] + lsb * (np.arange(-1, aa.NCODES + 1) - 0.5)
    ax.plot(ideal, np.arange(-1, aa.NCODES + 1), color=INK2, lw=0.8, ls="--", label="Ideal (end point)")
    ax.set_xlabel("Input voltage (V)")
    ax.set_ylabel("Output code")
    ax.set_ylim(-0.5, aa.NCODES - 0.5)
    ax.legend(loc="upper left")
    save(fig, build, "transfer")


def fig_dnl_inl(r, build):
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(FIG_W, 3.4), sharex=True)
    k = np.arange(1, aa.NCODES - 1)
    a1.bar(k, r["dnl"], width=0.7, color=SERIES[0])
    a1.axhline(0, color=INK2, lw=0.6)
    a1.set_ylabel("DNL (LSB)")
    a1.set_title("Differential nonlinearity", loc="left", color=INK)
    k2 = np.arange(1, aa.NCODES)
    a2.plot(k2, r["inl"], color=SERIES[0], marker="o", ms=3)
    a2.axhline(0, color=INK2, lw=0.6)
    a2.set_ylabel("INL (LSB)")
    a2.set_xlabel("Code (transition k-1 to k for INL)")
    a2.set_title("Integral nonlinearity (end point)", loc="left", color=INK)
    a2.set_xticks(range(0, aa.NCODES, 4))
    save(fig, build, "dnl_inl")


def fig_histogram(r, build):
    fig, ax = plt.subplots(figsize=(FIG_W, 2.2))
    h = r["hits"]
    ax.bar(np.arange(1, aa.NCODES - 1), h[1:-1], width=0.7, color=SERIES[0])
    ax.axhline(h[1:-1].mean(), color=INK2, lw=0.8, ls="--")
    ax.annotate("mean %.0f" % h[1:-1].mean(), (aa.NCODES - 1.5, h[1:-1].mean()), color=INK2, fontsize=7,
                ha="right", va="bottom")
    ax.set_xlabel("Code (end codes 0 and %d excluded)" % (aa.NCODES - 1))
    ax.set_ylabel("Hits")
    ax.set_xticks(range(0, aa.NCODES, 4))
    save(fig, build, "histogram")


def fig_sine_time(s, build, name):
    n = int(round(3 * aa.FS / s["freq"]))  # three periods
    n = max(min(n, len(s["codes"])), 50)
    t = np.arange(n) / aa.FS * 1e3
    fig, ax = plt.subplots(figsize=(FIG_W, 2.2))
    ax.plot(t, s["fitted"][:n], color=INK2, lw=0.8, ls="--", label="Sine fit")
    ax.plot(t, s["codes"][:n], color=SERIES[0], drawstyle="steps-post", label="ADC output")
    ax.set_xlabel("Time (ms)")
    ax.set_ylabel("Code")
    ax.set_ylim(-0.5, aa.NCODES - 0.5)
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2)
    save(fig, build, name)


def fig_spectrum(s, build, name):
    fig, ax = plt.subplots(figsize=(FIG_W, 2.4))
    ax.plot(s["spec_freqs"] / 1e3, s["spec_dbfs"], color=SERIES[0], lw=0.6)
    placed = [s["freq"]]
    for k, h in s["harm"].items():
        if h["dbc"] > -60 and all(abs(h["freq"] - p) > 0.03 * aa.FS / 2 for p in placed):
            placed.append(h["freq"])
            y = s["spec_dbfs"][int(round(h["freq"] / aa.FS * len(s["codes"])))]
            ax.annotate("H%d" % k, (h["freq"] / 1e3, y), xytext=(0, 3), textcoords="offset points",
                        ha="center", fontsize=6, color=INK2)
    ax.set_xlabel("Frequency (kHz)")
    ax.set_ylabel("Power (dBFS)")
    ax.set_xlim(0, aa.FS / 2e3)
    ax.set_ylim(-110, 5)
    save(fig, build, name)


def khz_axis(ax):
    ax.xaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: "%g" % v))


def fig_sweep(sines, build):
    f = np.array([s["freq"] for s in sines]) / 1e3
    # ENOB vs frequency
    fig, ax = plt.subplots(figsize=(FIG_W, 2.4))
    ax.semilogx(f, [s["enob"] for s in sines], color=SERIES[0], marker="o", ms=4, label="Measured")
    ax.semilogx(f, [s["enob_static"] for s in sines], color=SERIES[1], marker="o", ms=3.5,
                label="Predicted from the static transfer (ramp)")
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2)
    ax.set_xlabel("Input frequency (kHz)")
    khz_axis(ax)
    ax.set_ylabel("ENOB (bits)")
    ax.axhline(aa.BITS, color=INK2, lw=0.6, ls="--")
    ax.annotate("ideal %d bits" % aa.BITS, (f[0], aa.BITS), xytext=(2, -9), textcoords="offset points",
                fontsize=7, color=INK2)
    ax.set_ylim(0, aa.BITS + 0.5)
    save(fig, build, "sweep_enob")

    # Harmonics vs frequency (one axis, dBc)
    fig, ax = plt.subplots(figsize=(FIG_W, 2.6))
    series = [("THD", [s["thd"] for s in sines])] + \
             [("H%d" % k, [s["harm"][k]["dbc"] for s in sines]) for k in (2, 3, 5)]
    for i, (lbl, y) in enumerate(series):
        ax.semilogx(f, y, color=SERIES[i], marker="o", ms=3.5, label=lbl)
        ax.annotate(lbl, (f[-1], y[-1]), xytext=(4, 0), textcoords="offset points", va="center",
                    fontsize=7, color=INK)
    ax.set_xlabel("Input frequency (kHz)")
    khz_axis(ax)
    ax.set_ylabel("Level (dBc)")
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=4)
    save(fig, build, "sweep_harmonics")

    # Amplitude response: ADC fitted amplitude and scope amplitude, both relative to the lowest frequency
    fig, ax = plt.subplots(figsize=(FIG_W, 2.4))
    adc = np.array([s["amp"] for s in sines])
    scope = np.array([s["scope_amp_v"] for s in sines])
    ax.semilogx(f, 20 * np.log10(adc / adc[0]), color=SERIES[0], marker="o", ms=3.5, label="ADC (fitted)")
    ax.semilogx(f, 20 * np.log10(scope / scope[0]), color=SERIES[1], marker="o", ms=3.5,
                label="Input (scope CH1)")
    ax.set_xlabel("Input frequency (kHz)")
    khz_axis(ax)
    ax.set_ylabel("Amplitude rel. to %.0f kHz (dB)" % f[0])
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2)
    save(fig, build, "sweep_amplitude")


# Adapter board front end (IHP130_ASICONE_ADP_QFN48_0p5Q_Adapter): U5A buffer -> VIP, U5B inverter
# (two 1k, noise gain 2) -> VIN, both LMV358, then 47.5 ohm per side, 1.2 nF across, 120 pF to ground
FE_R, FE_CD, FE_CG = 47.5, 1.2e-9, 120e-12
# References and common mode set on the board (measured with a multimeter)
FE_VREFP, FE_VREFN, FE_VMID = 0.8, 0.4, 0.6


def frontend_model(f, gbw):
    """Differential and common-mode gains (relative to the low-frequency differential gain) of the
    linear model of the board front end with single-pole op-amps of gain-bandwidth gbw."""
    s = 2j * np.pi * np.asarray(f, float)
    hb = 1 / (1 + s / (2 * np.pi * gbw))
    hi = -1 / (1 + 2 * s / (2 * np.pi * gbw))
    d = (hb - hb * hi) / 2 / (1 + s * FE_R * (2 * FE_CD + FE_CG))
    c = (hb + hb * hi) / 2 / (1 + s * FE_R * FE_CG)
    return d, c


def fig_frontend(sines, build):
    """Measured amplitude response against the front-end model; common-mode ripple and H2."""
    f = np.array([s["freq"] for s in sines])
    rel = np.array([s["amp"] for s in sines]) / sines[0]["amp"]
    fit = f <= 520e3  # above, the op-amps slew
    gbws = np.linspace(0.5e6, 3e6, 501)
    err = [np.sum((np.abs(frontend_model(f[fit], g)[0]) - rel[fit]) ** 2) for g in gbws]
    gbw = gbws[int(np.argmin(err))]
    ff = np.logspace(np.log10(f[0]), np.log10(1e6), 200)
    fig, (a1, a2, a3) = plt.subplots(3, 1, figsize=(FIG_W, 5.4), sharex=True)
    a1.semilogx(f / 1e3, 20 * np.log10(rel), color=SERIES[0], marker="o", ms=3.5, ls="none", label="Measured (ADC)")
    a1.semilogx(ff / 1e3, 20 * np.log10(np.abs(frontend_model(ff, gbw)[0])), color=SERIES[1],
                label="Model, GBW %.2f MHz (fit)" % (gbw / 1e6))
    a1.semilogx(ff / 1e3, 20 * np.log10(np.abs(frontend_model(ff, 1e6)[0])), color=SERIES[2], ls="--",
                label="Model, GBW 1 MHz")
    a1.set_ylabel("Diff. gain (dB)")
    a1.legend(loc="lower left", fontsize=7)
    cm = np.abs(frontend_model(ff, gbw)[1]) / np.abs(frontend_model(ff, gbw)[0])
    a2.semilogx(ff / 1e3, 20 * np.log10(np.maximum(cm, 1e-4)), color=SERIES[1])
    a2.set_ylabel("CM / diff. (dB)")
    a2.set_ylim(-50, 0)
    a3.semilogx(f / 1e3, [s["harm"][2]["dbc"] for s in sines], color=SERIES[0], marker="o", ms=3.5)
    a3.set_ylabel("H2 (dBc)")
    a3.set_xlabel("Input frequency (kHz)")
    khz_axis(a3)
    save(fig, build, "frontend")
    # Slew rate the op-amps need for the scope amplitude
    rows = []
    for s in sines:
        d, c = frontend_model(s["freq"], gbw)
        sinad_q = 20 * np.log10(s["amp"] * np.sqrt(6))  # ideal quantizer, amplitude A in LSB
        rows.append((s["freq"], s["amp"], s["amp"] / sines[0]["amp"], abs(d), abs(c) / abs(d),
                     2 * np.pi * s["freq"] * s["scope_amp_v"] / 1e6, s["sinad"], sinad_q))
    return gbw, rows


def write_frontend_tex(build, ramp, gbw, rows):
    L = []
    m = lambda k, v: L.append("\\newcommand{\\%s}{%s}" % (k, v))
    # Ideal transfer through the front end: v_d = 2 (v_in - FE_VMID), LSB_d = 2 (VREFP - VREFN) / 2^N
    lsb = (FE_VREFP - FE_VREFN) / aa.NCODES
    t1, tl = FE_VMID - (aa.NCODES / 2 - 1) * lsb, FE_VMID + (aa.NCODES / 2 - 1) * lsb
    mid = (ramp["t"][0] + ramp["t"][-1]) / 2
    m("BoardVrefp", "%.1f" % FE_VREFP)
    m("BoardVrefn", "%.1f" % FE_VREFN)
    m("BoardVmid", "%.1f" % FE_VMID)
    m("FeLsbIdeal", "%.2f" % (lsb * 1e3))
    m("FeTFirstIdeal", "%.4f" % t1)
    m("FeTLastIdeal", "%.4f" % tl)
    m("FeGainErr", "%.1f" % ((ramp["lsb"] / lsb - 1) * 100))
    m("FeMid", "%.4f" % mid)
    m("FeOffsetMv", "%.0f" % ((mid - FE_VMID) * 1e3))
    m("FeOffsetLsb", "%.1f" % ((mid - FE_VMID) / ramp["lsb"]))
    # Binary bit weights that best explain the transition levels (in end-point LSB)
    k = np.arange(1, aa.NCODES)
    X = np.column_stack([[(kk >> b) & 1 for kk in k] for b in range(aa.BITS)] + [np.ones(len(k))])
    w = np.linalg.lstsq(X, k + ramp["inl"], rcond=None)[0]
    for b in range(aa.BITS):
        m("BitW" + "ABCDEFGH"[b], "%.2f" % w[b])
        m("BitE" + "ABCDEFGH"[b], "%+.1f" % ((w[b] / 2 ** b - 1) * 100))
    m("InlRms", "%.2f" % np.std(ramp["inl"]))
    m("InlResidRms", "%.2f" % np.std(ramp["inl"] - (X @ w - k)))
    m("FeGbw", "%.2f" % (gbw / 1e6))
    hi = rows[-1]
    m("FeHighFreq", "%.0f" % (hi[0] / 1e3))
    m("FeHighRel", "%.2f" % hi[2])
    m("FeHighModel", "%.2f" % hi[3])
    m("FeHighCm", "%.0f" % (hi[4] * 100))
    m("FeHighSlew", "%.2f" % hi[5])
    with open(os.path.join(build, "results_fe.tex"), "w") as fo:
        fo.write("\n".join(L) + "\n")
    with open(os.path.join(build, "table_fe.tex"), "w") as fo:
        fo.write("\\begin{tabular}{S[table-format=3.1] S[table-format=2.2] S[table-format=1.3] S[table-format=1.3] "
                 "S[table-format=2.0] S[table-format=1.2] S[table-format=2.2] S[table-format=2.2]}\n\\toprule\n"
                 "{$f_{in}$} & {$A$} & \\multicolumn{2}{c}{Diff. gain} & {CM/diff.} & {Slew} & {SINAD} & {SINAD$_q$} \\\\\n"
                 "{(\\si{kHz})} & {(LSB)} & {meas.} & {model} & {(\\si{\\percent})} & {(\\si{V/\\micro s})} "
                 "& {(\\si{dB})} & {(\\si{dB})} \\\\\n\\midrule\n")
        for r in rows:
            fo.write("%.1f & %.2f & %.3f & %.3f & %.0f & %.2f & %.2f & %.2f \\\\\n" % (
                r[0] / 1e3, r[1], r[2], r[3], r[4] * 100, r[5], r[6], r[7]))
        fo.write("\\bottomrule\n\\end{tabular}\n")


SIM_DBC_FLOOR = -100  # make_sim_report.py floors harmonics that cancel exactly (numerical zeros) here


def sim_dbc(v):
    return "{$<%d$}" % SIM_DBC_FLOOR if v <= SIM_DBC_FLOOR else "%.1f" % v


def fig_compare(ramp, sines, sim, build):
    """Measurement vs simulation, both normalized to their own end-point LSB (the gain of the
    front end and the reference of the chip are not known well enough to compare volts)."""
    st = sim["static"]
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(FIG_W, 3.6), sharex=True)
    k = np.arange(1, aa.NCODES - 1)
    runs = [("Measured", ramp["dnl"], ramp["inl"]), ("Simulated (schematic)", st["dnl"], st["inl"])]
    tp = (sim.get("pex") or {}).get("tapeout")
    if tp:
        runs.append(("Simulated (post-layout)", tp["dnl"], tp["inl"]))
    wd = 0.8 / len(runs)
    for i, (lbl, dnl, _) in enumerate(runs):
        a1.bar(k + (i - (len(runs) - 1) / 2) * wd, dnl, width=wd, color=SERIES[i], label=lbl)
    a1.axhline(0, color=INK2, lw=0.6)
    a1.set_ylabel("DNL (LSB)")
    a1.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=3)
    k2 = np.arange(1, aa.NCODES)
    for i, (lbl, _, inl) in enumerate(runs):
        a2.plot(k2, inl, color=SERIES[i], marker="o", ms=3, label=lbl)
    a2.axhline(0, color=INK2, lw=0.6)
    a2.set_ylabel("INL (LSB)")
    a2.set_xlabel("Code (transition k-1 to k for INL)")
    a2.set_xticks(range(0, aa.NCODES, 4))
    save(fig, build, "cmp_dnl_inl")
    if not sim["sines"]:  # Static simulation only
        return

    f = np.array([s["freq"] for s in sines]) / 1e3
    fsim = np.array([s["freq"] for s in sim["sines"]]) / 1e3
    fig, ax = plt.subplots(figsize=(FIG_W, 2.4))
    ax.semilogx(f, [s["enob"] for s in sines], color=SERIES[0], marker="o", ms=4, label="Measured")
    ax.semilogx(fsim, [s["enob"] for s in sim["sines"]], color=SERIES[1], marker="o", ms=4,
                label="Simulated, ideal input")
    fe = sim.get("sines_frontend", [])
    ffe = np.array([s["freq"] for s in fe]) / 1e3
    if fe:
        ax.semilogx(ffe, [s["enob"] for s in fe], color=SERIES[2], marker="o", ms=4,
                    label="Simulated, board front-end model")
    ax.legend(loc="lower left", bbox_to_anchor=(0, 1.0), ncol=2)
    ax.set_xlabel("Input frequency (kHz)")
    khz_axis(ax)
    ax.set_ylabel("ENOB (bits)")
    ax.set_ylim(0, aa.BITS + 0.5)
    ax.axhline(aa.BITS, color=INK2, lw=0.6, ls="--")
    save(fig, build, "cmp_enob")

    fig, axs = plt.subplots(1, 2, figsize=(FIG_W, 2.4), sharey=True)
    for ax, hk in zip(axs, (2, 3)):
        ax.semilogx(f, [s["harm"][hk]["dbc"] for s in sines], color=SERIES[0], marker="o", ms=3.5, label="Measured")
        ys = np.array([s["harm"][str(hk)]["dbc"] for s in sim["sines"]])
        ax.semilogx(fsim, np.where(ys <= SIM_DBC_FLOOR, np.nan, ys), color=SERIES[1], marker="o", ms=3.5,
                    label="Sim., ideal input")
        if fe:
            ys = np.array([s["harm"][str(hk)]["dbc"] for s in fe])
            ax.semilogx(ffe, np.where(ys <= SIM_DBC_FLOOR, np.nan, ys), color=SERIES[2], marker="o", ms=3.5,
                        label="Sim., board model")
        ax.set_title("H%d" % hk, loc="left", color=INK)
        ax.set_xlabel("Input frequency (kHz)")
        khz_axis(ax)
    axs[0].set_ylabel("Level (dBc)")
    axs[0].legend(loc="lower left", fontsize=7)
    save(fig, build, "cmp_harmonics")


def write_compare_tex(build, ramp, sines, sim):
    st = sim["static"]
    L = []
    m = lambda k, v: L.append("\\newcommand{\\%s}{%s}" % (k, v))
    m("SimSource", tex_escape(sim["source"]))
    pv = sim.get("pdk") or {}
    m("SimPdk", "%s, git %s (%s, %s; \\texttt{git describe} gives %s)" % (
        pv.get("name", "ihp-sg13g2"), pv.get("commit", "?"), pv.get("branch", "?"), pv.get("date", "?"),
        pv.get("describe", "?")))
    m("SimLsb", "%.2f" % (st["lsb_v"] * 1e3))
    m("SimLsbIdeal", "%.2f" % (st["lsb_ideal_v"] * 1e3))
    m("SimGainErr", "%.1f" % (st["gain_err"] * 100))
    m("SimDnlMin", "%.2f" % min(st["dnl"]))
    m("SimDnlMax", "%.2f" % max(st["dnl"]))
    m("SimInlMin", "%.2f" % min(st["inl"]))
    m("SimInlMax", "%.2f" % max(st["inl"]))
    d_m, d_s = np.array(ramp["dnl"]), np.array(st["dnl"])
    i_m, i_s = np.array(ramp["inl"]), np.array(st["inl"])
    m("CmpDnlCorr", "%.2f" % np.corrcoef(d_m, d_s)[0, 1])
    m("CmpInlCorr", "%.2f" % np.corrcoef(i_m, i_s)[0, 1])
    m("CmpDnlRms", "%.2f" % np.sqrt(np.mean((d_m - d_s) ** 2)))
    m("CmpInlRms", "%.2f" % np.sqrt(np.mean((i_m - i_s) ** 2)))
    m("SimInlRms", "%.3f" % np.sqrt(np.mean(i_s ** 2)))
    w = st.get("weights") or [float("nan")] * aa.BITS
    for b in range(aa.BITS):
        m("SimBitW" + "ABCDEFGH"[b], "%.3f" % w[b])
        m("SimBitE" + "ABCDEFGH"[b], "%+.1f" % (round((w[b] / 2 ** b - 1) * 100, 1) or 0.0))
    # The lowest-frequency measured sine quantized with the simulated transition levels, scaled to the
    # measured end points: the ENOB the schematic linearity allows for that same input
    t_s = np.array(st["t"])
    t_m = ramp["t"][0] + (t_s - t_s[0]) / (t_s[-1] - t_s[0]) * (ramp["t"][-1] - ramp["t"][0])
    sinad, enob = aa.static_model({"t": t_m, "lsb": ramp["lsb"]}, sines[0])
    m("SimStSinad", "%.2f" % sinad)
    m("SimStEnob", "%.2f" % enob)
    m("SimStFreq", "%.1f" % (sines[0]["freq"] / 1e3))
    if sim["sines"]:
        ss = sim["sines"]
        m("SimEnobLow", "%.2f" % ss[0]["enob"])
        m("SimEnobHigh", "%.2f" % ss[-1]["enob"])
        m("SimFreqHigh", "%.1f" % (ss[-1]["freq"] / 1e3))
        m("MeasEnobHigh", "%.2f" % sines[-1]["enob"])
        m("MeasFreqHigh", "%.0f" % (sines[-1]["freq"] / 1e3))
        m("MeasHtwoHigh", "%.1f" % sines[-1]["harm"][2]["dbc"])
        fe = sim.get("sines_frontend", [])
        if fe:
            m("SimFeEnobHigh", "%.2f" % fe[-1]["enob"])
            m("SimFeAmpHigh", "%.2f" % fe[-1]["amp"])
            m("SimFeHtwoMax", "%.0f" % max(f["harm"]["2"]["dbc"] for f in fe))
    # Post-layout simulations (static only): tapeout (Pex...) and hierarchical re-layout (Hier...)
    for key, tag in (("tapeout", "Pex"), ("hier", "Hier")):
        p = (sim.get("pex") or {}).get(key)
        if not p:
            continue
        m(tag + "Source", tex_escape(p["source"]))
        for b in range(aa.BITS):
            m(tag + "BitW" + "ABCDEFGH"[b], "%.3f" % p["weights"][b])
            m(tag + "BitE" + "ABCDEFGH"[b], "%+.1f" % (round((p["weights"][b] / 2 ** b - 1) * 100, 1) or 0.0))
        m(tag + "Lsb", "%.2f" % (p["lsb_v"] * 1e3))
        m(tag + "GainErr", "%.1f" % (p["gain_err"] * 100))
        i_p, d_p = np.array(p["inl"]), np.array(p["dnl"])
        m(tag + "DnlMin", "%.2f" % d_p.min())
        m(tag + "DnlMax", "%.2f" % d_p.max())
        m(tag + "InlRms", "%.2f" % np.sqrt(np.mean(i_p ** 2)))
        m(tag + "InlCorr", "%.2f" % np.corrcoef(i_m, i_p)[0, 1])
        m(tag + "DnlCorr", "%.2f" % np.corrcoef(d_m, d_p)[0, 1])
        m(tag + "CmpInlRms", "%.2f" % np.sqrt(np.mean((i_m - i_p) ** 2)))
        m(tag + "CmpDnlRms", "%.2f" % np.sqrt(np.mean((d_m - d_p) ** 2)))
        t_p = np.array(p["t"])
        t_m = ramp["t"][0] + (t_p - t_p[0]) / (t_p[-1] - t_p[0]) * (ramp["t"][-1] - ramp["t"][0])
        sinad, enob = aa.static_model({"t": t_m, "lsb": ramp["lsb"]}, sines[0])
        m(tag + "StSinad", "%.2f" % sinad)
        m(tag + "StEnob", "%.2f" % enob)
        par = p.get("plate_par")
        if par and key == "tapeout":
            cols = ["VOUTH", "VOUTL", "supply", "vref/vin", "controls", "own"]
            with open(os.path.join(build, "table_par.tex"), "w") as fo:
                fo.write("\\begin{tabular}{ll S[table-format=3.1] *{6}{S[table-format=2.1]}}\n\\toprule\n"
                         "Half & Plate & {Total} & {VOUTH} & {VOUTL} & {Supplies} & {VREF/VIN/VIP} & {Controls} & "
                         "{VSH--FL} \\\\\n\\midrule\n")
                for half in ("H", "L"):
                    for b in range(aa.BITS):
                        for kind in ("VSH", "FL"):
                            e = par["%s%d_%s" % (half, b, kind)]
                            n = e["units"]
                            fo.write("%s & bit %d %s & %.1f & %s \\\\\n" % (
                                half, b, kind, e["total_f"] / n * 1e15,
                                " & ".join("%.1f" % (e[c] / n * 1e15) for c in cols)))
                    if half == "H":
                        fo.write("\\midrule\n")
                fo.write("\\bottomrule\n\\end{tabular}\n")
    with open(os.path.join(build, "results_sim.tex"), "w") as fo:
        fo.write("\n".join(L) + "\n")
    if not sim["sines"]:
        return

    with open(os.path.join(build, "table_cmp.tex"), "w") as fo:
        fo.write("\\begin{tabular}{l S[table-format=3.1] S[table-format=1.2] S[table-format=-2.1] S[table-format=-2.1] "
                 "S[table-format=-2.1]}\n\\toprule\n"
                 " & {$f_{in}$ (\\si{kHz})} & {ENOB (bits)} & {H2 (\\si{dBc})} & {H3 (\\si{dBc})} & {THD (\\si{dB})} \\\\\n"
                 "\\midrule\n")
        for s in sines:
            fo.write("Measured & %.1f & %.2f & %.1f & %.1f & %.1f \\\\\n" % (
                s["freq"] / 1e3, s["enob"], s["harm"][2]["dbc"], s["harm"][3]["dbc"], s["thd"]))
        fo.write("\\midrule\n")
        for s in sim["sines"]:
            fo.write("Simulated & %.1f & %.2f & %s & %s & %.1f \\\\\n" % (
                s["freq"] / 1e3, s["enob"], sim_dbc(s["harm"]["2"]["dbc"]), sim_dbc(s["harm"]["3"]["dbc"]), s["thd"]))
        if sim.get("sines_frontend"):
            fo.write("\\midrule\n")
            for s in sim["sines_frontend"]:
                fo.write("Sim., board model & %.1f & %.2f & %s & %s & %.1f \\\\\n" % (
                    s["freq"] / 1e3, s["enob"], sim_dbc(s["harm"]["2"]["dbc"]), sim_dbc(s["harm"]["3"]["dbc"]),
                    s["thd"]))
        fo.write("\\bottomrule\n\\end{tabular}\n")


def tex_escape(s):
    return s.replace("_", r"\_")


def write_tex(build, runs, ramp, sines):
    L = []
    m = lambda k, v: L.append("\\newcommand{\\%s}{%s}" % (k, v))
    m("ResultsDir", tex_escape(os.path.basename(os.path.normpath(runs["dir"]))))
    m("AdcBits", aa.BITS)
    m("AdcLastCode", aa.NCODES - 1)
    m("AdcFs", "%.4f" % (aa.FS / 1e6))
    m("AdcSamples", len(sines[0]["codes"]) if sines else ramp["n"])
    if ramp:
        t = ramp["t"]
        m("RampFreq", "%.3f" % runs["ramp"]["freq_hz"])
        m("RampVmin", "%.4f" % ramp["vmin"])
        m("RampVmax", "%.4f" % ramp["vmax"])
        m("TFirst", "%.4f" % t[0])
        m("TLast", "%.4f" % t[-1])
        m("Lsb", "%.2f" % (ramp["lsb"] * 1e3))
        m("DnlMin", "%.2f" % ramp["dnl"].min())
        m("DnlMax", "%.2f" % ramp["dnl"].max())
        m("InlMin", "%.2f" % ramp["inl"].min())
        m("InlMax", "%.2f" % ramp["inl"].max())
        m("MissingCodes", ", ".join(map(str, ramp["missing"])) if ramp["missing"] else "none")
        m("RampOverdriven", "yes" if ramp["overdriven"] else "\\textbf{no (transition levels unreliable)}")
    if sines:
        s0 = sines[0]
        m("SineRefFreq", "%.3f" % (s0["freq"] / 1e3))
        m("SineRefEnob", "%.2f" % s0["enob"])
        m("SineRefSinad", "%.2f" % s0["sinad"])
        m("SineHighFreq", "%.3f" % (sines[-1]["freq"] / 1e3))
        m("SineAmplMv", runs["sine_ampl"])
        m("SineBiasMv", runs["sine_bias"])
    with open(os.path.join(build, "results.tex"), "w") as fo:
        fo.write("\n".join(L) + "\n")

    # Code table
    with open(os.path.join(build, "table_codes.tex"), "w") as fo:
        fo.write("\\begin{tabular}{S[table-format=2.0] S[table-format=5.0] S[table-format=1.4] "
                 "S[table-format=-1.2] S[table-format=-1.2]}\n\\toprule\n"
                 "{Code} & {Hits} & {$T_k$ (\\si{V})} & {DNL (LSB)} & {INL (LSB)} \\\\\n\\midrule\n")
        for k in range(aa.NCODES):
            tk = "%.4f" % ramp["t"][k - 1] if k >= 1 else "{--}"
            d = "%.2f" % ramp["dnl"][k - 1] if 1 <= k <= aa.NCODES - 2 else "{--}"
            i = "%.2f" % ramp["inl"][k - 1] if k >= 1 else "{--}"
            fo.write("%d & %d & %s & %s & %s \\\\\n" % (k, ramp["hits"][k], tk, d, i))
        fo.write("\\bottomrule\n\\end{tabular}\n")

    # Sweep table
    with open(os.path.join(build, "table_sweep.tex"), "w") as fo:
        fo.write("\\begin{tabular}{S[table-format=3.3] S[table-format=1.3] S[table-format=2.2] "
                 "S[table-format=2.2] S[table-format=2.2] S[table-format=2.2] S[table-format=1.2] S[table-format=-2.1] "
                 "S[table-format=2.1] S[table-format=-2.1] S[table-format=-2.1] S[table-format=-2.1]}\n\\toprule\n"
                 "{$f_{in}$ (\\si{kHz})} & {$A_{in}$ (\\si{V})} & {$A$ (LSB)} & {SINAD} & {SINAD$_{st}$} & {SNR} & {ENOB} & "
                 "{THD} & {SFDR} & {H2} & {H3} & {H5} \\\\\n"
                 " & & & {(\\si{dB})} & {(\\si{dB})} & {(\\si{dB})} & {(bits)} & {(\\si{dB})} & {(\\si{dB})} & {(\\si{dBc})} "
                 "& {(\\si{dBc})} & {(\\si{dBc})} \\\\\n\\midrule\n")
        for s in sines:
            fo.write("%.3f & %.3f & %.2f & %.2f & %.2f & %.2f & %.2f & %.1f & %.1f & %.1f & %.1f & %.1f \\\\\n" % (
                s["freq"] / 1e3, s["scope_amp_v"], s["amp"], s["sinad"], s["sinad_static"], s["snr"], s["enob"], s["thd"], s["sfdr"],
                s["harm"][2]["dbc"], s["harm"][3]["dbc"], s["harm"][5]["dbc"]))
        fo.write("\\bottomrule\n\\end{tabular}\n")

    # Clipping warnings
    clipped = [s for s in sines if s["clip"] > 0]
    with open(os.path.join(build, "notes.tex"), "w") as fo:
        for s in clipped:
            fo.write("\\item The \\SI{%.3f}{kHz} sine is clipped (%.2f\\,\\%% of the samples at code 0 or %d); "
                     "its SINAD and ENOB are pessimistic.\n" % (s["freq"] / 1e3, s["clip"] * 100, aa.NCODES - 1))
        if not clipped:
            fo.write("\\item No sine of the sweep is clipped.\n")


def main():
    args = sys.argv[1:]
    sim_path = None
    if "--sim" in args:
        i = args.index("--sim")
        sim_path = os.path.abspath(args[i + 1])
        del args[i:i + 2]
    if not args:
        print(__doc__)
        sys.exit(1)
    results = os.path.abspath(args[0])
    build = os.path.abspath(args[1]) if len(args) > 1 else os.path.join(results, "report")
    if sim_path is None and os.path.exists(os.path.join(results, "sim_summary.json")):
        sim_path = os.path.join(results, "sim_summary.json")
    os.makedirs(os.path.join(build, "fig"), exist_ok=True)

    with open(os.path.join(results, "runs.csv")) as fi:
        rows = list(csv.DictReader(fi))
    runs = {"dir": results}
    ramp, sines = None, []
    for row in rows:
        prefix = os.path.join(results, row["name"])
        freq = float(row["freq_hz"])
        if row["wave"] == "ramp":
            ramp = aa.analyze_ramp(prefix)
            runs["ramp"] = {"freq_hz": freq}
        elif row["wave"] == "sine":
            s = aa.analyze_sine(prefix, freq)
            s["name"] = row["name"]
            sines.append(s)
            runs["sine_ampl"], runs["sine_bias"] = row["ampl_mv"], row["bias_mv"]
    sines.sort(key=lambda s: s["freq"])
    if ramp is None or not sines:
        print("runs.csv needs one ramp and at least one sine")
        sys.exit(1)
    for s in sines:
        s["sinad_static"], s["enob_static"] = aa.static_model(ramp, s)

    fig_transfer(ramp, build)
    fig_dnl_inl(ramp, build)
    fig_histogram(ramp, build)
    fig_sine_time(sines[0], build, "sine_time")
    fig_spectrum(sines[0], build, "spectrum_low")
    fig_spectrum(sines[-1], build, "spectrum_high")
    fig_sweep(sines, build)
    write_tex(build, runs, ramp, sines)
    write_frontend_tex(build, ramp, *fig_frontend(sines, build))
    for f in ("comparison.tex", "results_sim.tex", "table_cmp.tex", "table_par.tex"):
        if os.path.exists(os.path.join(build, f)):
            os.remove(os.path.join(build, f))
    if sim_path:
        with open(sim_path) as fi:
            sim = json.load(fi)
        fig_compare(ramp, sines, sim, build)
        write_compare_tex(build, ramp, sines, sim)
        shutil.copy(os.path.join(REPORT_DIR, "comparison.tex"), build)

    summary = {
        "ramp": {"vmin": ramp["vmin"], "vmax": ramp["vmax"], "lsb_v": ramp["lsb"], "t": ramp["t"].tolist(),
                 "dnl": ramp["dnl"].tolist(), "inl": ramp["inl"].tolist(), "hits": ramp["hits"].tolist()},
        "sines": [{k: s[k] for k in ("name", "freq", "amp", "offset", "sinad", "sinad_static", "enob_static", "snr", "enob", "thd", "sfdr",
                                     "clip", "scope_amp_v", "scope_offset_v")} |
                  {"harm": {str(k): v for k, v in s["harm"].items()}} for s in sines],
    }
    with open(os.path.join(build, "results.json"), "w") as fo:
        json.dump(summary, fo, indent=1, default=float)

    for f in ("report.tex", "theory.tex", "frontend.tex", "references.tex"):
        shutil.copy(os.path.join(REPORT_DIR, f), build)
    setup = os.path.join(results, "setup.tex")
    shutil.copy(setup if os.path.exists(setup) else os.path.join(REPORT_DIR, "setup.tex"), build)
    subprocess.run(["latexmk", "-pdf", "-interaction=nonstopmode", "-halt-on-error", "report.tex"],
                   cwd=build, check=True, stdout=subprocess.DEVNULL)
    print(os.path.join(build, "report.pdf"))


if __name__ == "__main__":
    main()
