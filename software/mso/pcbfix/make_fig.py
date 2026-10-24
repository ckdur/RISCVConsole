#!/usr/bin/env python3
"""Figures and numbers of pcb_fix.tex: the front end of the adapter board with the present op-amps
and with the proposed ones, from the same linear model as the measurement report.

Usage: make_fig.py <results_dir>   (the results.json of its report build is used for the
measured points)
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "report"))
import make_report as mr  # noqa: E402  (plot style, board constants)

plt = mr.plt


def model(f, gbw, cd=mr.FE_CD, fda=False):
    """Differential and common-mode gains at the ADC pins. fda: both outputs from one fully
    differential stage (identical paths, no common-mode ripple)."""
    s = 2j * np.pi * np.asarray(f, float)
    if fda:
        d = 1 / (1 + s * 3 / (2 * np.pi * gbw))  # noise gain 1 + Rf/Rg = 3
        c = 0 * d
    else:
        hb = 1 / (1 + s / (2 * np.pi * gbw))
        hi = -1 / (1 + 2 * s / (2 * np.pi * gbw))
        d, c = (hb - hb * hi) / 2, (hb + hb * hi) / 2
    d = d / (1 + s * mr.FE_R * (2 * cd + mr.FE_CG))
    c = c / (1 + s * mr.FE_R * mr.FE_CG)
    return d, c


CASES = [
    ("LMV358 (present, fitted GBW)", dict(gbw=1.39e6)),
    ("OPA2365, C19 = 1.2 nF", dict(gbw=50e6)),
    ("OPA2365, C19 = 470 pF", dict(gbw=50e6, cd=470e-12)),
    ("THS4521 (FDA), C19 = 470 pF", dict(gbw=95e6, cd=470e-12, fda=True)),
]


def main():
    results = os.path.abspath(sys.argv[1])
    with open(os.path.join(results, "report", "results.json")) as f:
        meas = json.load(f)["sines"]
    build = os.path.join(HERE, "build")
    os.makedirs(os.path.join(build, "fig"), exist_ok=True)
    fm = np.array([s["freq"] for s in meas])
    am = np.array([s["amp"] for s in meas]) / meas[0]["amp"]

    ff = np.logspace(3, np.log10(2e6), 300)
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(mr.FIG_W, 3.9), sharex=True)
    a1.semilogx(fm / 1e3, 20 * np.log10(am), color=mr.INK, marker="o", ms=3.5, ls="none",
                label="Measured (present board)")
    for i, (lbl, kw) in enumerate(CASES):
        d, c = model(ff, **kw)
        a1.semilogx(ff / 1e3, 20 * np.log10(np.abs(d)), color=mr.SERIES[i], label=lbl)
        if not kw.get("fda"):
            a2.semilogx(ff / 1e3, 20 * np.log10(np.maximum(np.abs(c) / np.abs(d), 1e-5)), color=mr.SERIES[i])
    a1.axvline(mr.aa.FS / 2e3, color=mr.INK2, lw=0.6, ls=":")
    a1.set_ylabel("Diff. gain (dB)")
    a1.set_ylim(-9, 1)
    a1.legend(loc="lower left", fontsize=7)
    a2.axvline(mr.aa.FS / 2e3, color=mr.INK2, lw=0.6, ls=":")
    a2.set_ylabel("CM / diff. (dB)")
    a2.set_ylim(-80, 0)
    a2.set_xlabel("Input frequency (kHz)")
    mr.khz_axis(a2)
    mr.save(fig, build, "fe_options")

    L = []
    m = lambda k, v: L.append("\\newcommand{\\%s}{%s}" % (k, v))
    rows = []
    for (lbl, kw), key in zip(CASES, "ABCD"):
        d, c = model([300e3, 650e3], **kw)
        rows.append((lbl, abs(d[0]), abs(d[1]), abs(c[1]) / abs(d[1]) * 100))
        m("Gain" + key, "%.2f" % abs(d[1]))
        m("Cm" + key, "%.1f" % (abs(c[1]) / abs(d[1]) * 100))
    with open(os.path.join(build, "table_options.tex"), "w") as fo:
        fo.write("\\begin{tabular}{l S[table-format=1.2] S[table-format=1.2] S[table-format=2.2]}\n\\toprule\n"
                 "Front end & {Gain, \\SI{300}{kHz}} & {Gain, \\SI{650}{kHz}} & {CM/diff., \\SI{650}{kHz} (\\si{\\percent})} \\\\\n"
                 "\\midrule\n")
        for r in rows:
            fo.write("%s & %.2f & %.2f & %.1f \\\\\n" % r)
        fo.write("\\bottomrule\n\\end{tabular}\n")
    m("MeasGainHigh", "%.2f" % am[-1])
    with open(os.path.join(build, "numbers.tex"), "w") as fo:
        fo.write("\n".join(L) + "\n")


if __name__ == "__main__":
    main()
