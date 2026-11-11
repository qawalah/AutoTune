#!/usr/bin/env python3
"""
Checks the phone-sweep recordings in an unzipped AutoTune diagnostics folder (log.jsonl + recordings/*.wav).

Usage:  python3 -I tools/diagnostics/analyse_recordings.py <unzipped diagnostics folder>
Needs numpy. Written for the 11 Nov 2026 F-150 investigation (DIAGNOSTICS-2026-11-11.md); see that file for what
each number means. Per recording it prints:
  - the run (from log.jsonl), its score and where the start beeps were found
  - chunk lags: where each 0.5 s piece of the sweep arrived (ms) against the beeps; a jump that holds to the end is a
    Bluetooth pause (same rule as SweepCheck.stall: every later chunk >= 6 ms away from the median before)
  - mic click: the level of the first 0.1 s (the USB mic switching on)
  - bass: tone level and overtones (THD) at the start of the sweep, and how much the bass tone wobbles
"""
import json, os, sys, wave
import numpy as np

FS = 48000
T = 10.0                     # the phone sweep: 10 s, 20 Hz to 20 kHz, log
OFFSET = 1.6                 # Marker.SWEEP_OFFSET_SEC: first beep to sweep
L = np.log(1000.0)
t = np.arange(int(FS * T)) / FS
SWEEP = 0.5 * np.sin(2 * np.pi * 20 * T / L * (np.exp(t / T * L) - 1))


def load(path):
    w = wave.open(path)
    x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
    return x.reshape(-1, w.getnchannels())[:, 0]


def beeps(x):
    """First beep (s), from one-beep correlation; tolerates a late beep like Marker.find since 2.19.1."""
    B = int(0.12 * FS); tt = np.arange(B) / FS
    f = 1500 + 1500 * (tt / 0.12) / 2
    g = np.full(B, 0.5); fd = int(0.005 * FS)
    g[:fd] *= 0.5 * (1 - np.cos(np.pi * np.arange(fd) / fd)); g[-fd:] *= 0.5 * (1 - np.cos(np.pi * np.arange(fd)[::-1] / fd))
    beep = g * np.sin(2 * np.pi * f * tt)
    seg = x[int(0.5 * FS):int(6 * FS)]
    c = np.abs(np.correlate(seg, beep, 'valid'))
    peaks = []
    cc = c.copy()
    for _ in range(3):
        i = int(np.argmax(cc)); peaks.append(i); cc[max(0, i - int(0.05 * FS)):i + int(0.05 * FS)] = 0
    peaks.sort()
    first, last = peaks[0], peaks[-1]
    late = (last - first) / FS - 0.48
    # after a pause the sweep follows the last beep
    return 0.5 + (last / FS - 0.48), (peaks[1] - first) / FS - 0.24, late


def chunk_lags(x, s0):
    out = []
    a = 2.0
    while a + 0.5 <= T + 1e-9:
        i0, i1 = int(a * FS), int((a + 0.5) * FS); ml = int(0.08 * FS)
        seg = x[s0 + i0 - ml:s0 + i1 + ml]
        if s0 + i0 - ml < 0 or len(seg) < i1 - i0 + 2 * ml: break
        ref = SWEEP[i0:i1]
        N = 1 << int(np.ceil(np.log2(len(seg) + len(ref))))
        C = np.fft.fft(seg, N) * np.conj(np.fft.fft(ref, N)); fr = np.fft.fftfreq(N)
        C[fr < 0] = 0
        env = np.abs(np.fft.ifft(C))[:2 * ml + 1]
        out.append((int(np.argmax(env)) - ml) / FS * 1000); a += 0.5
    return out


def stall(lags):
    first, size = None, 0.0
    for s in range(2, len(lags) - 2):
        before = float(np.median(lags[:s])); after = lags[s:]
        if min(after) - before >= 6 or before - max(after) >= 6:
            if first is None: first = 2.0 + 0.5 * s
            step = float(np.median(after)) - before
            if abs(step) > abs(size): size = step
    return None if first is None else (first, size)


def bass(x, s0):
    N = 8192; win = np.hanning(N); fr = np.fft.rfftfreq(N, 1 / FS)
    def band(S, f): m = (fr > f / 1.12) & (fr < f * 1.12); return S[m].sum()
    rows = []
    for tt_ in (0.2, 0.8, 1.4, 2.0, 2.6):
        f = 20 * 1000 ** (tt_ / 10); c = int((s0 / FS + tt_) * FS)
        S = np.abs(np.fft.rfft(x[c - N // 2:c + N // 2] * win)) ** 2
        F = band(S, f); h = sum(band(S, k * f) for k in (2, 3, 4, 5))
        rows.append(f"{f:3.0f}Hz {10 * np.log10(F + 1e-20):4.0f}dB thd{100 * np.sqrt(h / F):4.0f}%")
    # wobble of the bass tone 0.8-3.2 s into the sweep (35-90 Hz), slow trend removed
    a, b = int(0.8 * FS), int(3.2 * FS); tt2 = np.arange(a, b) / FS
    bb = x[s0 + a:s0 + b] * np.exp(-1j * 2 * np.pi * 20 * T / L * (np.exp(tt2 / T * L) - 1))
    S = np.fft.fft(bb); f = np.fft.fftfreq(len(bb), 1 / FS); S[np.abs(f) > 60] = 0
    env = np.abs(np.fft.ifft(S))[::48]; env /= env.mean()
    k = 120; tr = np.convolve(env, np.ones(k) / k, 'same'); r = (env / tr)[k:-k]
    return ' | '.join(rows), 100 * np.std(r)


def main(d):
    runs = {}
    for line in open(os.path.join(d, 'log.jsonl')):
        e = json.loads(line)
        if e.get('type') == 'measurement' and e.get('recording'): runs[e['recording']] = e
    saved = {}
    for line in open(os.path.join(d, 'log.jsonl')):
        e = json.loads(line)
        if e.get('type') in ('run_saved', 'stock_saved'): saved[e['t'][:19]] = e
    for name in sorted(os.listdir(os.path.join(d, 'recordings'))):
        if not name.endswith('.wav'): continue
        x = load(os.path.join(d, 'recordings', name)); e = runs.get(name, {})
        score = next((s.get('rmsDb') for k, s in saved.items() if k == e.get('t', '')[:19]), None)
        click = 20 * np.log10(np.sqrt(np.mean(x[:int(0.1 * FS)] ** 2)) + 1e-12)
        x[:int(0.5 * FS)] = 0  # as SweepCheck.quietStart
        m, gap2, late = beeps(x)
        s0 = int(round((m + OFFSET) * FS))
        lg = chunk_lags(x, s0); st = stall(lg)
        b, wob = bass(x, s0)
        print(f"\n{name}  {e.get('route', '?')} test={e.get('test', '?')!r} score={score} fatal={'yes' if e.get('fatal') else 'no'}")
        print(f"  beeps: sweep at {m + OFFSET:.3f} s; beep 2 {gap2 * 1000:+.0f} ms, beep 3 {late * 1000:+.0f} ms off time"
              f"   mic click {click:.0f} dB rms")
        print(f"  chunk lags (ms, from 2 s): {' '.join(f'{v:.0f}' for v in lg)}")
        print(f"  stall: {'none' if st is None else f'{st[1]:+.0f} ms at {st[0]:.1f} s into the sweep'}")
        print(f"  bass: {b}   wobble {wob:.1f}%")
        if 'peakOffsetMs' in e or 'beepLateMs' in e:  # logged by the app from 2.19.1
            print(f"  app logged: peakOffsetMs={e.get('peakOffsetMs')} beepLateMs={e.get('beepLateMs')} stallMs={e.get('stallMs')} attempt={e.get('attempt')}")


if __name__ == '__main__':
    if len(sys.argv) != 2: sys.exit(__doc__)
    main(sys.argv[1])
