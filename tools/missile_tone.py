"""Synthesises the missile-inbound tone (sounds/missile_inbound.ogg), so the asset has no third-party source.

A fighter-style launch warning: short, hard-edged pulses alternating between two high tones, 8 a
second, 2 s long so it loops seamlessly (a whole number of pulses, and every pulse fades in and out).
Run: python3 tools/missile_tone.py && ffmpeg -y -i /tmp/missile_inbound.wav -c:a libvorbis -q:a 5 \
     versions/26.2/src/main/resources/assets/hypixelscout/sounds/missile_inbound.ogg
"""
import wave

import numpy as np

RATE = 44_100
PULSE = 0.125          # one pulse and its gap
ON = 0.085             # how long the tone sounds in each pulse
TONES = (1_250.0, 1_650.0)
PULSES = 16            # 2 s
EDGE = 0.004           # fade at each end of a pulse, against clicks

samples = []
for i in range(PULSES):
    t = np.arange(int(RATE * PULSE)) / RATE
    f = TONES[i % 2]
    # A square-ish wave (odd harmonics) reads as an alarm, not a flute.
    tone = sum(np.sin(2 * np.pi * f * k * t) / k for k in (1, 3, 5))
    envelope = np.clip(np.minimum(t / EDGE, (ON - t) / EDGE), 0, 1)
    samples.append(tone * envelope)

signal = np.concatenate(samples)
signal = 0.6 * signal / np.max(np.abs(signal))

with wave.open("/tmp/missile_inbound.wav", "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(RATE)
    out.writeframes((signal * 32_767).astype("<i2").tobytes())
