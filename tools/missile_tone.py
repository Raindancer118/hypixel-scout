"""Synthesises the two warning tones, so the assets have no third-party source.

missile_inbound.ogg — a fireball is on its way. Modelled on the F/A-18's AN/ALR-67 radar warning
receiver "missile launch" audio: a continuous tone that changes between 455 Hz and 555 Hz every 0.1 s
(openflightschool.de, AN/ALR-67 audio warnings; the F-16's ALR-56M uses a 515 Hz tone at the same
0.1 s rhythm). Pure sine, phase-continuous at every switch so it never clicks; 2 s long — ten pairs,
a whole number of cycles — so it loops seamlessly.

missile_lock.ogg — somebody aims a fire charge at you, nothing thrown yet. Not a recording of any
real receiver: a steady 1 kHz beep, 0.12 s on and 0.12 s off, above the launch warble and chopped
instead of continuous, so the two are told apart at once. Each beep has 5 ms ramps so it does not
click; 0.96 s long, four beeps, so it loops seamlessly too.

Run: python3 tools/missile_tone.py && for t in missile_inbound missile_lock; do ffmpeg -y -i /tmp/$t.wav \\
     -c:a libvorbis -q:a 6 versions/26.2/src/main/resources/assets/hypixelscout/sounds/$t.ogg; done
"""
import wave

import numpy as np

RATE = 44_100


def write(name, signal):
    with wave.open(f"/tmp/{name}.wav", "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes((signal * 32_767).astype("<i2").tobytes())


def launch():
    step = 0.1                # each tone lasts this long
    tones = (455.0, 555.0)
    steps = 20                # 2 s; 45.5 + 55.5 cycles a pair keeps the loop phase-exact
    frequency = np.concatenate([np.full(int(RATE * step), tones[i % 2]) for i in range(steps)])
    # Integrating the frequency gives a phase with no jumps at the switches.
    phase = 2 * np.pi * np.cumsum(frequency) / RATE
    return 0.5 * np.sin(phase)


def lock():
    beep = int(RATE * 0.12)
    ramp = int(RATE * 0.005)
    t = np.arange(beep) / RATE
    envelope = np.ones(beep)
    envelope[:ramp] = np.linspace(0, 1, ramp)
    envelope[-ramp:] = np.linspace(1, 0, ramp)
    on = 0.45 * np.sin(2 * np.pi * 1000.0 * t) * envelope
    return np.tile(np.concatenate([on, np.zeros(beep)]), 4)


write("missile_inbound", launch())
write("missile_lock", lock())
