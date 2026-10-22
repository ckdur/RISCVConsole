#!/usr/bin/env python3
"""Analyze an adc_buf dump from adc_run.sh.

  adc_analyze.py ramp <prefix>             histogram test: transition levels, DNL, INL
                                            (the ramp must overdrive both ends of the ADC range)
  adc_analyze.py sine <prefix> [freq_hz]   sine fit: SINAD, ENOB, harmonics, clipping

Voltages come from the scope capture in <prefix>.scope.csv (CH1 = DDS output).
Also used as a module by report/make_report.py (analyze_ramp, analyze_sine).
"""
import sys
import numpy as np

BITS = 5
NCODES = 1 << BITS
FS = 10e6 / 7  # ADC sample rate (10 MHz / 7 clocks per conversion)
NHARM = 7      # Harmonics reported (2..NHARM)
HBINS = 4      # Half width in bins of a tone in the windowed spectrum


def load(prefix):
    codes = np.frombuffer(open(prefix + ".bin", "rb").read(), dtype=np.uint8).astype(int)
    scope = np.genfromtxt(prefix + ".scope.csv", delimiter=",", names=True)
    return codes, scope


def sine_fit(x, fs, freq):
    """4 parameter sine fit. Returns (freq, amplitude, offset, phase, fitted)."""
    n = len(x)
    tt = np.arange(n) / fs

    def fit3(f):
        a = np.column_stack([np.cos(2 * np.pi * f * tt), np.sin(2 * np.pi * f * tt), np.ones(n)])
        p, *_ = np.linalg.lstsq(a, x, rcond=None)
        return p, a @ p

    # Refine the frequency with a parabolic step on the residual
    f = freq
    for _ in range(8):
        df = fs / n * 1e-2
        e0 = np.sum((x - fit3(f)[1]) ** 2)
        ep = np.sum((x - fit3(f + df)[1]) ** 2)
        em = np.sum((x - fit3(f - df)[1]) ** 2)
        den = ep - 2 * e0 + em
        if den <= 0:
            break
        f -= df * (ep - em) / (2 * den)
    p, y = fit3(f)
    return f, np.hypot(p[0], p[1]), p[2], np.arctan2(-p[1], p[0]), y


def alias(f, fs):
    """Frequency of f after sampling at fs, folded to [0, fs/2]."""
    f = f % fs
    return fs - f if f > fs / 2 else f


def analyze_ramp(prefix):
    codes, scope = load(prefix)
    v = scope["ch1"]
    # Ends of the triangle: its voltages are uniformly distributed, so the sorted scope samples
    # are a line vs rank. Fit the middle part and extrapolate (averages the scope quantization).
    vs = np.sort(v)
    r = (np.arange(len(vs)) + 0.5) / len(vs)
    m = (r > 0.1) & (r < 0.9)
    k, c = np.polyfit(r[m], vs[m], 1)
    vmin, vmax = c, k + c
    h = np.bincount(codes, minlength=NCODES)
    n = len(codes)

    # Transition level T[k] between code k-1 and k (linear ramp -> time fraction = voltage fraction)
    t = vmin + (vmax - vmin) * np.cumsum(h)[:-1] / n  # T[1..NCODES-1]
    lsb = (t[-1] - t[0]) / (NCODES - 2)
    dnl = np.diff(t) / lsb - 1                         # codes 1..NCODES-2
    inl = (t - (t[0] + lsb * np.arange(NCODES - 1))) / lsb  # T[1..NCODES-1], end point fit

    return {
        "codes": codes, "scope": scope, "vmin": vmin, "vmax": vmax, "hits": h, "n": n,
        "overdriven": bool(h[0] and h[-1]), "t": t, "lsb": lsb, "dnl": dnl, "inl": inl,
        "missing": [k for k in range(1, NCODES - 1) if h[k] == 0],
    }


def analyze_sine(prefix, freq):
    codes, scope = load(prefix)
    x = codes.astype(float)
    n = len(x)

    f, amp, off, ph, y = sine_fit(x, FS, freq)
    err = np.sqrt(np.mean((x - y) ** 2))
    sinad = 20 * np.log10(amp / np.sqrt(2) / err)
    enob = (sinad - 1.76) / 6.02
    clip = np.mean((codes == 0) | (codes == NCODES - 1))

    # Windowed spectrum (Blackman-Harris), robust to a small clock mismatch between ADC and DDS
    ph_w = 2 * np.pi * np.arange(n) / n
    w = 0.35875 - 0.48829 * np.cos(ph_w) + 0.14128 * np.cos(2 * ph_w) - 0.01168 * np.cos(3 * ph_w)
    spec = np.abs(np.fft.rfft((x - x.mean()) * w)) ** 2
    spec /= np.sum(w) ** 2 / 2  # Power of a sine of amplitude A -> A^2/2 summed over its bins
    freqs = np.fft.rfftfreq(n, 1 / FS)

    def tone(fr):
        b = int(round(fr / FS * n))
        lo, hi = max(b - HBINS, 1), min(b + HBINS + 1, len(spec))
        return np.arange(lo, hi)

    used = np.zeros(len(spec), bool)
    used[:HBINS + 1] = True  # DC
    fb = tone(f)
    p_fund = spec[fb].sum()
    used[fb] = True
    harm = {}
    for hk in range(2, NHARM + 1):
        fh = alias(hk * f, FS)
        hb = tone(fh)
        harm[hk] = {"freq": fh, "dbc": 10 * np.log10(max(spec[hb].sum(), 1e-30) / p_fund)}
        used[hb] = True
    p_harm = sum(10 ** (h["dbc"] / 10) for h in harm.values()) * p_fund
    p_noise = spec[~used].sum() * len(spec) / max((~used).sum(), 1)  # Extrapolate to the full band
    spur = spec.copy()
    spur[fb] = 0
    spur[:HBINS + 1] = 0

    # Input amplitude seen by the scope (to separate a DDS roll-off from the ADC)
    t_s = scope["t"]
    fs_scope = 1 / (t_s[1] - t_s[0])
    sf = sine_fit(scope["ch1"], fs_scope, alias(freq, fs_scope))

    return {
        "codes": codes, "scope": scope, "freq_nominal": freq, "freq": f, "amp": amp, "offset": off,
        "phase": ph, "fitted": y, "err_rms": err, "sinad": sinad, "enob": enob, "clip": clip,
        "thd": 10 * np.log10(p_harm / p_fund), "snr": 10 * np.log10(p_fund / p_noise),
        "sfdr": 10 * np.log10(p_fund / spur.max()), "harm": harm,
        "spec_dbfs": 10 * np.log10(np.maximum(spec, 1e-30) / ((NCODES / 2) ** 2 / 2)), "spec_freqs": freqs,
        "scope_fs": fs_scope, "scope_amp_v": sf[1], "scope_offset_v": sf[2],
    }


def print_ramp(r):
    print("Scope ramp: %.4f V .. %.4f V" % (r["vmin"], r["vmax"]))
    print("Samples: %d, code 0: %d, code %d: %d" % (r["n"], r["hits"][0], NCODES - 1, r["hits"][-1]))
    if not r["overdriven"]:
        print("WARNING: the ramp does not overdrive both ends, transition levels are not reliable")
    t, lsb, dnl, inl = r["t"], r["lsb"], r["dnl"], r["inl"]
    print("First transition (0->1): %.4f V, last (%d->%d): %.4f V" % (t[0], NCODES - 2, NCODES - 1, t[-1]))
    print("LSB: %.2f mV, span of codes 0..%d (one LSB past the end transitions): %.4f V .. %.4f V" %
          (lsb * 1e3, NCODES - 1, t[0] - lsb, t[-1] + lsb))
    print("DNL: min %.2f max %.2f LSB, INL: min %.2f max %.2f LSB (end point)" %
          (dnl.min(), dnl.max(), inl.min(), inl.max()))
    print("code  hits   T[k](V)   DNL     INL")
    for k in range(NCODES):
        tk = "%.4f" % t[k - 1] if k >= 1 else "   -  "
        d = "%6.2f" % dnl[k - 1] if 1 <= k <= NCODES - 2 else "   -  "
        i = "%6.2f" % inl[k - 1] if k >= 1 else "   -  "
        print("%4d %6d   %s  %s  %s" % (k, r["hits"][k], tk, d, i))
    if r["missing"]:
        print("Missing codes:", r["missing"])


def print_sine(s):
    print("Scope: amplitude %.4f V, offset %.4f V" % (s["scope_amp_v"], s["scope_offset_v"]))
    print("Codes: min %d max %d, clipped samples: %.2f %%" % (s["codes"].min(), s["codes"].max(), s["clip"] * 100))
    print("Fit: freq %.3f Hz, amplitude %.3f LSB, offset %.3f LSB" % (s["freq"], s["amp"], s["offset"]))
    print("RMS error %.3f LSB, SINAD %.2f dB, ENOB %.2f bits" % (s["err_rms"], s["sinad"], s["enob"]))
    print("SNR %.2f dB, THD %.2f dB, SFDR %.2f dB" % (s["snr"], s["thd"], s["sfdr"]))
    print("Harmonics: " + "  ".join("H%d %.1f dBc @ %.0f Hz" % (k, h["dbc"], h["freq"]) for k, h in s["harm"].items()))
    if s["clip"] > 0:
        print("WARNING: the sine is clipped, SINAD/ENOB are pessimistic")


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("ramp", "sine"):
        print(__doc__)
        sys.exit(1)
    if sys.argv[1] == "ramp":
        print_ramp(analyze_ramp(sys.argv[2]))
    else:
        print_sine(analyze_sine(sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 1000.0))
