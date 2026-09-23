"""Synthesises the missile-inbound tone (sounds/missile_inbound.ogg), so the asset has no third-party source.

Modelled on the F/A-18's AN/ALR-67 radar warning receiver "missile launch" audio: a continuous tone
that changes between 455 Hz and 555 Hz every 0.1 s (openflightschool.de, AN/ALR-67 audio warnings;
the F-16's ALR-56M uses a 515 Hz tone at the same 0.1 s rhythm). Pure sine, phase-continuous at every
switch so it never clicks; 2 s long — ten pairs, a whole number of cycles — so it loops seamlessly.

Run: python3 tools/missile_tone.py && ffmpeg -y -i /tmp/missile_inbound.wav -c:a libvorbis -q:a 6 \\
     versions/26.2/src/main/resources/assets/hypixelscout/sounds/missile_inbound.ogg
"""
import wave

import numpy as np

RATE = 44_100
STEP = 0.1                # each tone lasts this long
TONES = (455.0, 555.0)
STEPS = 20                # 2 s; 45.5 + 55.5 cycles a pair keeps the loop phase-exact

frequency = np.concatenate([np.full(int(RATE * STEP), TONES[i % 2]) for i in range(STEPS)])
# Integrating the frequency gives a phase with no jumps at the switches.
phase = 2 * np.pi * np.cumsum(frequency) / RATE
signal = 0.5 * np.sin(phase)

with wave.open("/tmp/missile_inbound.wav", "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(RATE)
    out.writeframes((signal * 32_767).astype("<i2").tobytes())
