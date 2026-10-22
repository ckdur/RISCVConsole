#!/usr/bin/env python3
"""Build the ADC characterization report from a results directory made by adc_sweep.sh.

  make_report.py <results_dir> [build_dir]

Analyzes every run listed in <results_dir>/runs.csv, writes the figures (vector PDF),
results.tex (LaTeX macros and tables) and results.json into build_dir (default:
<results_dir>/report), then compiles report.tex there with latexmk.
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
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    results = os.path.abspath(sys.argv[1])
    build = os.path.abspath(sys.argv[2]) if len(sys.argv) > 2 else os.path.join(results, "report")
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

    summary = {
        "ramp": {"vmin": ramp["vmin"], "vmax": ramp["vmax"], "lsb_v": ramp["lsb"], "t": ramp["t"].tolist(),
                 "dnl": ramp["dnl"].tolist(), "inl": ramp["inl"].tolist(), "hits": ramp["hits"].tolist()},
        "sines": [{k: s[k] for k in ("name", "freq", "amp", "offset", "sinad", "sinad_static", "enob_static", "snr", "enob", "thd", "sfdr",
                                     "clip", "scope_amp_v", "scope_offset_v")} |
                  {"harm": {str(k): v for k, v in s["harm"].items()}} for s in sines],
    }
    with open(os.path.join(build, "results.json"), "w") as fo:
        json.dump(summary, fo, indent=1, default=float)

    for f in ("report.tex", "theory.tex"):
        shutil.copy(os.path.join(REPORT_DIR, f), build)
    setup = os.path.join(results, "setup.tex")
    shutil.copy(setup if os.path.exists(setup) else os.path.join(REPORT_DIR, "setup.tex"), build)
    subprocess.run(["latexmk", "-pdf", "-interaction=nonstopmode", "-halt-on-error", "report.tex"],
                   cwd=build, check=True, stdout=subprocess.DEVNULL)
    print(os.path.join(build, "report.pdf"))


if __name__ == "__main__":
    main()
