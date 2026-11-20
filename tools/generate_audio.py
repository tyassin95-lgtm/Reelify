#!/usr/bin/env python3
"""
Generates Reelify's bundled ORIGINAL music and sound effects procedurally (no samples, no
third-party recordings). Output: app/src/main/assets/audio/{music,sfx}/*.m4a (AAC via ffmpeg).

All output of this script is dedicated to the public domain (CC0 1.0).
Deterministic: fixed random seeds, so re-running reproduces the same audio.

Usage: python3 tools/generate_audio.py
"""
import os, subprocess, sys, tempfile, wave
import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "audio")

def note_hz(n):  # MIDI note -> Hz
    return 440.0 * 2 ** ((n - 69) / 12)

def env_adsr(n, a, d, s, r, sr=SR):
    a, d, r = int(a * sr), int(d * sr), int(r * sr)
    sus = max(n - a - d - r, 0)
    e = np.concatenate([np.linspace(0, 1, max(a, 1)), np.linspace(1, s, max(d, 1)), np.full(sus, s), np.linspace(s, 0, max(r, 1))])
    return np.pad(e, (0, max(0, n - len(e))))[:n]

def lowpass_fft(x, cutoff, sr=SR, slope=2.0):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / sr)
    X *= 1 / np.sqrt(1 + (f / cutoff) ** (2 * slope))
    return np.fft.irfft(X, len(x))

def highpass_fft(x, cutoff, sr=SR, slope=2.0):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / sr) + 1e-9
    X *= 1 / np.sqrt(1 + (cutoff / f) ** (2 * slope))
    return np.fft.irfft(X, len(x))

def fft_convolve(x, h):
    n = len(x) + len(h) - 1
    N = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(h, N), N)[:len(x)]

def reverb_ir(seconds, seed, damp=6000, sr=SR):
    rng = np.random.default_rng(seed)
    n = int(seconds * sr)
    t = np.arange(n) / sr
    ir = rng.standard_normal((2, n)) * np.exp(-t * (6.9 / seconds))
    ir = np.stack([lowpass_fft(ir[0], damp), lowpass_fft(ir[1], damp)])
    ir[:, : int(0.012 * sr)] *= np.linspace(0, 1, int(0.012 * sr))
    return ir / np.sqrt(np.sum(ir ** 2, axis=1, keepdims=True))

def apply_reverb(stereo, wet, seconds=2.4, seed=1, damp=6000):
    ir = reverb_ir(seconds, seed, damp)
    out = np.stack([fft_convolve(stereo[0], ir[0]), fft_convolve(stereo[1], ir[1])])
    return stereo * (1 - wet) + out * wet

def saw(phase):
    return 2 * (phase % 1.0) - 1

def tri(phase):
    return 2 * np.abs(2 * (phase % 1.0) - 1) - 1

def pad_voice(freq, n, rng, bright=2500, detune=0.006, voices=4):
    t = np.arange(n) / SR
    sig = np.zeros(n)
    for v in range(voices):
        d = 1 + detune * (v - (voices - 1) / 2) / max(voices - 1, 1) * 2
        ph0 = rng.random()
        lfo = 1 + 0.0015 * np.sin(2 * np.pi * (0.13 + 0.05 * v) * t + rng.random() * 6)
        sig += 0.6 * saw(freq * d * lfo * t + ph0) + 0.4 * tri(freq * d * t + ph0)
    return lowpass_fft(sig / voices, bright)

def pluck(freq, n, decay=1.6, bright=1.0):
    t = np.arange(n) / SR
    s = np.zeros(n)
    for k, amp in enumerate([1.0, 0.45 * bright, 0.22 * bright, 0.12 * bright, 0.06 * bright], start=1):
        s += amp * np.sin(2 * np.pi * freq * k * t) * np.exp(-t * decay * (1 + 0.6 * (k - 1)))
    att = np.minimum(1, t / 0.004)
    return s * att

def kick(n):
    t = np.arange(n) / SR
    f = 45 + 75 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 7) + 0.15 * np.exp(-t * 90) * np.random.default_rng(3).standard_normal(n)

def hat(n, rng):
    t = np.arange(n) / SR
    return highpass_fft(rng.standard_normal(n), 7000) * np.exp(-t * 55)

def clap(n, rng):
    t = np.arange(n) / SR
    noise = lowpass_fft(highpass_fft(rng.standard_normal(n), 900), 5000)
    return noise * (np.exp(-t * 22) + 0.4 * np.exp(-((t - 0.012) ** 2) / 0.00002))

def place(buf, sig, start, gain=1.0, pan=0.0):
    s = int(start)
    if s >= buf.shape[1]:
        return
    e = min(buf.shape[1], s + len(sig))
    l = np.cos((pan + 1) * np.pi / 4); r = np.sin((pan + 1) * np.pi / 4)
    buf[0, s:e] += sig[: e - s] * gain * l * 1.4142
    buf[1, s:e] += sig[: e - s] * gain * r * 1.4142

def soft_clip(x, drive=1.2):
    return np.tanh(x * drive) / np.tanh(drive)

def master(stereo, target_rms_db=-17.0, ceiling_db=-1.2):
    stereo = stereo - stereo.mean(axis=1, keepdims=True)
    rms = np.sqrt(np.mean(stereo ** 2))
    stereo *= 10 ** (target_rms_db / 20) / max(rms, 1e-9)
    stereo = soft_clip(stereo, 1.1)
    peak = np.max(np.abs(stereo))
    ceiling = 10 ** (ceiling_db / 20)
    if peak > ceiling:
        stereo *= ceiling / peak
    return stereo

# ------------------------------------------------------------------------------------------ music

PROGRESSIONS = {
    # roots (MIDI) + chord intervals
    "maj7":   [(57, [0, 4, 7, 11]), (54, [0, 3, 7, 10]), (50, [0, 4, 7, 11]), (52, [0, 4, 7, 10])],  # A F#m D E
    "minor":  [(50, [0, 3, 7, 10]), (46, [0, 4, 7, 11]), (53, [0, 4, 7, 9]), (48, [0, 4, 7, 10])],  # Dm Bb F C
    "lift":   [(48, [0, 4, 7, 14]), (55, [0, 4, 7, 11]), (57, [0, 3, 7, 10]), (53, [0, 4, 7, 14])],  # C G Am F
    "float":  [(52, [0, 7, 11, 14]), (48, [0, 4, 7, 14]), (55, [0, 4, 7, 9]), (50, [0, 3, 7, 14])],  # E C G D
    "lux":    [(51, [0, 4, 7, 11, 14]), (56, [0, 4, 7, 11]), (49, [0, 3, 7, 10]), (54, [0, 4, 7, 10])],
}

TRACKS = [
    # id, bpm, progression, layers, brightness, reverb wet, seed
    ("elegant_silk", 84, "maj7", {"pad": 0.55, "pluck": 0.42, "bass": 0.35, "arp": "slow"}, 2600, 0.38, 11),
    ("cinematic_rise", 72, "minor", {"pad": 0.7, "pluck": 0.28, "bass": 0.45, "swell": True, "arp": "slow", "pulse": True}, 2200, 0.45, 12),
    ("modern_pulse", 104, "lift", {"pad": 0.42, "pluck": 0.35, "bass": 0.5, "drums": "modern", "arp": "eighth"}, 3400, 0.25, 13),
    ("luxury_velvet", 90, "lux", {"pad": 0.6, "pluck": 0.38, "bass": 0.4, "drums": "soft", "arp": "slow"}, 2400, 0.36, 14),
    ("soft_electronic_glow", 100, "float", {"pad": 0.5, "pluck": 0.3, "bass": 0.45, "drums": "soft", "arp": "sixteenth"}, 3000, 0.3, 15),
    ("minimal_air", 80, "float", {"pad": 0.45, "pluck": 0.32, "arp": "sparse"}, 2000, 0.42, 16),
    ("motivational_lift", 112, "lift", {"pad": 0.5, "pluck": 0.4, "bass": 0.55, "drums": "drive", "arp": "eighth", "swell": True}, 3600, 0.24, 17),
    ("calm_clinical", 76, "maj7", {"pad": 0.55, "pluck": 0.3, "bass": 0.25, "arp": "sparse"}, 2200, 0.4, 18),
    ("social_bounce", 118, "lift", {"pad": 0.35, "pluck": 0.45, "bass": 0.55, "drums": "bounce", "arp": "sixteenth"}, 3800, 0.2, 19),
    ("ambient_haze", 66, "float", {"pad": 0.8, "pluck": 0.15, "swell": True, "arp": "sparse"}, 1600, 0.55, 20),
]

def render_track(tid, bpm, prog_name, layers, bright, wet, seed, seconds=64.0):
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    buf = np.zeros((2, n))
    beat = 60.0 / bpm
    bar = beat * 4
    prog = PROGRESSIONS[prog_name]
    chord_bars = 2
    n_chords = int(np.ceil(seconds / (bar * chord_bars)))
    # Arrangement: intro (first 4 bars light), full, breakdown, full, outro
    total_bars = int(seconds / bar) + 1
    def intensity(b):
        if b < 2: return 0.55
        if b < 4: return 0.8
        if total_bars * 0.55 < b < total_bars * 0.68: return 0.7
        return 1.0
    for ci in range(n_chords):
        root, iv = prog[ci % len(prog)]
        start = ci * bar * chord_bars
        dur = bar * chord_bars
        ns = int(dur * SR) + int(0.6 * SR)
        b0 = int(start / bar)
        inten = intensity(b0)
        # pad
        if layers.get("pad"):
            e = env_adsr(ns, 0.9 if layers.get("swell") else 0.4, 0.5, 0.85, 0.9)
            for k, i in enumerate(iv):
                v = pad_voice(note_hz(root + i + 12), ns, rng, bright=bright)
                place(buf, v * e, start * SR, layers["pad"] * 0.18 * (0.8 + 0.2 * inten), pan=(k - len(iv) / 2) * 0.25)
        # bass
        if layers.get("bass"):
            bn = int(dur * SR)
            t = np.arange(bn) / SR
            f = note_hz(root - 12)
            b = soft_clip(np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t), 1.5) * env_adsr(bn, 0.02, 0.3, 0.8, 0.25)
            place(buf, b, start * SR, layers["bass"] * 0.32 * inten)
        # arpeggio / plucks
        mode = layers.get("arp", "slow")
        step = {"sparse": beat * 2, "slow": beat, "eighth": beat / 2, "sixteenth": beat / 4}[mode]
        steps = int(dur / step)
        tones = [root + 12 + i for i in iv] + [root + 24 + iv[0], root + 24 + iv[1 % len(iv)]]
        pattern = rng.permutation(len(tones)) if mode != "slow" else np.arange(len(tones))
        for s in range(steps):
            if mode == "sparse" and rng.random() < 0.35: continue
            if mode == "sixteenth" and s % 4 == 3 and rng.random() < 0.5: continue
            note = tones[pattern[s % len(pattern)]]
            pn = int(1.6 * SR)
            vel = 0.75 + 0.25 * rng.random()
            place(buf, pluck(note_hz(note), pn, decay=2.2 if mode != "sparse" else 1.3, bright=0.9), (start + s * step) * SR,
                  layers.get("pluck", 0.3) * 0.22 * vel * inten, pan=0.35 * np.sin(s * 1.3))
    # drums
    style = layers.get("drums")
    if style:
        k = kick(int(0.5 * SR)); h_rng = np.random.default_rng(seed + 100)
        hats = [hat(int(0.12 * SR), h_rng) for _ in range(4)]
        cl = clap(int(0.3 * SR), h_rng)
        nbeats = int(seconds / beat)
        for bt in range(nbeats):
            bpos = bt * beat
            barno = int(bpos / bar)
            if barno < 2:  # intro without drums
                continue
            inten = intensity(barno)
            if inten < 0.75 and style != "drive":
                continue
            beat_in_bar = bt % 4
            kg = {"soft": 0.35, "modern": 0.5, "drive": 0.6, "bounce": 0.55}[style]
            if style in ("modern", "drive", "soft") or beat_in_bar in (0, 2):
                if style != "soft" or beat_in_bar in (0, 2):
                    place(buf, k, bpos * SR, kg)
            if style == "bounce" and beat_in_bar == 2:
                place(buf, k, (bpos + beat * 0.75) * SR, kg * 0.6)
            if beat_in_bar in (1, 3):
                place(buf, cl, bpos * SR, {"soft": 0.12, "modern": 0.2, "drive": 0.25, "bounce": 0.22}[style], pan=0.05)
            for sub in range(2 if style in ("soft",) else 4 if style in ("drive", "bounce") else 2):
                place(buf, hats[sub % 4], (bpos + sub * beat / (2 if style == "soft" else 4 if style in ("drive", "bounce") else 2)) * SR,
                      0.05 + 0.03 * (sub % 2 == 1), pan=0.3)
    if layers.get("pulse"):
        t = np.arange(n) / SR
        pulse = np.sin(2 * np.pi * note_hz(prog[0][0] - 12) * t) * (0.5 + 0.5 * np.sin(2 * np.pi * t / beat)) ** 4
        place(buf, lowpass_fft(pulse, 400), 0, 0.08)
    buf = apply_reverb(buf, wet, seconds=2.8 if wet > 0.4 else 2.0, seed=seed, damp=5500)
    # global fade in/out so the track loops/trims gracefully (the app applies its own fades too)
    fade = np.ones(n)
    fi, fo = int(1.2 * SR), int(3.0 * SR)
    fade[:fi] = np.linspace(0, 1, fi) ** 2
    fade[-fo:] = np.linspace(1, 0, fo) ** 2
    buf *= fade
    return master(buf, -18.0)

# ------------------------------------------------------------------------------------------ sfx

def band_noise_sweep(n, f0, f1, width, rng, sr=SR):
    noise = rng.standard_normal(n + 2048)
    out = np.zeros(n + 2048)
    hop, win = 512, np.hanning(1024)
    for i in range(0, n, hop):
        frame = noise[i:i + 1024] * win
        if len(frame) < 1024: break
        X = np.fft.rfft(frame)
        f = np.fft.rfftfreq(1024, 1 / sr)
        c = f0 * (f1 / f0) ** (i / max(n, 1))
        X *= np.exp(-((np.log(f + 1) - np.log(c)) ** 2) / (2 * width ** 2))
        out[i:i + 1024] += np.fft.irfft(X, 1024) * win
    return out[:n]

def sfx_render(sid, rng):
    if sid == "soft_whoosh":
        n = int(0.7 * SR); t = np.arange(n) / SR
        s = band_noise_sweep(n, 400, 3200, 0.5, rng) * np.sin(np.pi * t / t[-1]) ** 1.5
        st = np.stack([s * (1 - t / t[-1] * 0.6), s * (0.4 + t / t[-1] * 0.6)])
        return apply_reverb(st, 0.2, 0.8, 5)
    if sid == "transition_sweep":
        n = int(0.9 * SR); t = np.arange(n) / SR
        s = band_noise_sweep(n, 300, 6000, 0.35, rng) * (t / t[-1]) ** 1.2 * np.exp(-np.maximum(t - 0.75, 0) * 30)
        tone = np.sin(2 * np.pi * np.cumsum(200 + 1400 * (t / t[-1]) ** 2) / SR) * 0.15 * (t / t[-1])
        return apply_reverb(np.stack([s + tone, s * 0.9 + tone]), 0.25, 1.0, 6)
    if sid == "subtle_impact":
        n = int(0.9 * SR); t = np.arange(n) / SR
        th = np.sin(2 * np.pi * np.cumsum(40 + 60 * np.exp(-t * 20)) / SR) * np.exp(-t * 6)
        click = lowpass_fft(rng.standard_normal(n), 2500) * np.exp(-t * 60) * 0.3
        s = soft_clip(th + click, 1.3)
        return apply_reverb(np.stack([s, s]), 0.25, 1.4, 7, 3000)
    if sid == "soft_click":
        n = int(0.15 * SR); t = np.arange(n) / SR
        s = lowpass_fft(highpass_fft(rng.standard_normal(n), 1500), 6000) * np.exp(-t * 250) + 0.4 * np.sin(2 * np.pi * 1800 * t) * np.exp(-t * 180)
        return np.stack([s, s])
    if sid == "rise":
        n = int(1.5 * SR); t = np.arange(n) / SR
        g = (t / t[-1]) ** 2.2
        s = band_noise_sweep(n, 200, 5000, 0.45, rng) * g
        tone = sum(np.sin(2 * np.pi * np.cumsum(f * (1 + 1.5 * (t / t[-1]) ** 2)) / SR) for f in (220, 330.0, 440)) * 0.08 * g
        out = np.stack([s + tone, s * 0.95 + tone]) * np.minimum(1, (t[-1] - t) / 0.03)
        return apply_reverb(out, 0.3, 1.2, 8)
    if sid == "gentle_shimmer":
        n = int(1.4 * SR); t = np.arange(n) / SR
        s = np.zeros(n)
        for i, (f, r) in enumerate([(1568, 1.0), (2093, 0.8), (2637, 0.6), (3136, 0.5), (4186, 0.35)]):
            d = int(i * 0.06 * SR)
            part = np.sin(2 * np.pi * f * t[: n - d]) * np.exp(-t[: n - d] * 3.2) * r
            for k, inh in enumerate((1.0, 2.76, 5.4)):
                part += 0.25 / (k + 1) * np.sin(2 * np.pi * f * inh * t[: n - d]) * np.exp(-t[: n - d] * 6)
            s[d:] += part
        return apply_reverb(np.stack([s, np.roll(s, 220)]), 0.45, 2.0, 9)
    if sid == "clean_pop":
        n = int(0.2 * SR); t = np.arange(n) / SR
        s = np.sin(2 * np.pi * np.cumsum(300 + 600 * np.exp(-t * 40)) / SR) * np.exp(-t * 30) * np.minimum(1, t / 0.002)
        return np.stack([s, s])
    if sid == "text_reveal":
        n = int(0.5 * SR); t = np.arange(n) / SR
        sw = band_noise_sweep(n, 2500, 8000, 0.4, rng) * np.sin(np.pi * np.minimum(t / 0.35, 1)) * 0.6
        tick = np.sin(2 * np.pi * 2600 * t) * np.exp(-np.maximum(t - 0.3, 0) * 120) * (t > 0.3) * 0.35
        return apply_reverb(np.stack([sw + tick, sw * 0.9 + tick]), 0.2, 0.8, 10)
    if sid == "light_accent":
        n = int(0.8 * SR); t = np.arange(n) / SR
        s = sum(pluck(note_hz(m), n, decay=4.0, bright=0.5) for m in (79, 83, 86)) / 3
        return apply_reverb(np.stack([s, s]), 0.35, 1.5, 11)
    raise ValueError(sid)

SFX = ["soft_whoosh", "transition_sweep", "subtle_impact", "soft_click", "rise", "gentle_shimmer", "clean_pop", "text_reveal", "light_accent"]

def write_m4a(stereo, path, bitrate):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(stereo.T, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav = tmp.name
    with wave.open(wav, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "aac", "-b:a", bitrate, "-movflags", "+faststart", path], check=True)
    os.remove(wav)

def main():
    only = sys.argv[1:]
    for tid, bpm, prog, layers, bright, wet, seed in TRACKS:
        if only and tid not in only: continue
        audio = render_track(tid, bpm, prog, layers, bright, wet, seed)
        write_m4a(audio, os.path.join(ROOT, "music", tid + ".m4a"), "128k")
        print("music", tid)
    rng = np.random.default_rng(2026)
    for sid in SFX:
        if only and sid not in only: continue
        s = sfx_render(sid, rng)
        s = master(s, -16.0, -1.0)
        write_m4a(s, os.path.join(ROOT, "sfx", sid + ".m4a"), "160k")
        print("sfx", sid)

if __name__ == "__main__":
    main()
