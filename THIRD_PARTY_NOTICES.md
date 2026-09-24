# Third-party notices

Hypixel Scout's own code is not otherwise under any of the licences below; this file lists what was
copied or derived from elsewhere, and under what licence, as required.

## Iustitia (MIT)

`common/src/main/java/de/raindancer118/hypixelscout/cheat/CheatWatch.java` ports the algorithms and
thresholds of several checks from [Iustitia](https://github.com/ThoriaDevelopment/Iustitia)
(`dev.iustitia`), commit `9cf356fe`, `src/main/kotlin/dev/iustitia/checks/{combat,movement}/*.kt`.
Iustitia is MIT-licensed; its licence permits copying with attribution, reproduced in full below. The
checks (or parts of checks) derived from it, each carrying its own `// Derived from Iustitia (MIT),
checks/.../X.kt` comment at the point of use:

| Hypixel Scout check (`Check` enum) | Iustitia source |
| --- | --- |
| `Check.AUTOCLICKER` | `checks/combat/ClickStatisticsCheck.kt` (CPS, robot, stDev, kurtosis signals) |
| `Check.AIMSNAP` | `checks/movement/AimWrapCheck.kt` |
| `Check.AIMTRACK` | `checks/movement/RotationTrackingCheck.kt` (match-rate signal; the pitch-GCD sub-signal was not ported, see `CheatWatch`'s javadoc on `judgeAimTrack`) |
| `Check.TRIGGERBOT` | `checks/combat/TriggerbotCheck.kt` |
| `Check.HITFLICK` | `checks/combat/HitFlickCheck.kt` |
| `Check.MULTIAURA` (2-tick union window) | `checks/combat/MultiTargetCheck.kt` |
| `Check.NOFALL` | `checks/movement/NoFallDamageCheck.kt` (adapted: touchdown proxy uses `Frame#supported()`, a terrain query, in place of Iustitia's own touchdown detection — see `CheatWatch#judgeNoFall`'s javadoc) |
| `Check.STEP` | `checks/movement/StepHeightCheck.kt` (adapted: no remote Jump Boost amplifier visibility, see `CheatWatch#judgeStep`'s javadoc) |
| `Check.BLINK` | `checks/movement/PacketGapCheck.kt` |
| `Check.CRITICALS` | `checks/combat/CriticalsCheck.kt` |
| Reach split (standing/moving), the invulnerability gap for knockback, and the server-lag judging pause (pre-existing, 0.12.0) | Iustitia's general approach, studied and re-implemented |

Iustitia's MIT licence, in full:

```
MIT License

Copyright (c) 2026 ThoriaDevelopment / Iustitia

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## MegaWallsEnhancements' HackerDetector — studied, not copied

Alexdoru's `HackerDetector` (part of MegaWallsEnhancements) was studied for ideas only (the
swing-then-push attribution, hits through walls, KeepSprint, backwards-bridging Scaffold and reliefs
concepts it inspired here were written fresh, not copied). It is under its own custom, **non-commercial**
licence — **not MIT** — so no source from it is or may be copied into this project. An earlier version
of `CheatWatch`'s javadoc incorrectly stated both detectors were MIT; that has been corrected.

## GrimAC / NoCheatPlus — studied, not copied

Both are GPL-3.0. Their general detection approaches were surveyed for background only; nothing from
either was copied, and nothing from either may be copied into this (differently licensed) project.
