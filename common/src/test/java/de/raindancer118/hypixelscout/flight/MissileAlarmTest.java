package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissileAlarmTest {
	private static IncomingWatch.Warning warning(ProjectileKind kind, double ticks) {
		return new IncomingWatch.Warning(1, kind, ticks, Vec.ZERO, true);
	}

	private static final LockWatch.Lock LOCK = new LockWatch.Lock(2, "Enemy", new Vec(0, 1.62, 20), Vec.ZERO, 18, false);

	@Test
	void onlyAFireballSoundsTheMissileAlarm() {
		assertThat(MissileAlarm.tone(warning(ProjectileKind.FIREBALL, 20), null)).isEqualTo(MissileAlarm.Tone.LAUNCH);
		assertThat(MissileAlarm.tone(warning(ProjectileKind.ARROW, 20), null)).isEqualTo(MissileAlarm.Tone.NONE);
		assertThat(MissileAlarm.tone(null, null)).isEqualTo(MissileAlarm.Tone.NONE);
	}

	@Test
	void aFireChargeAimedAtMeSoundsTheLockTone() {
		assertThat(MissileAlarm.tone(null, LOCK)).isEqualTo(MissileAlarm.Tone.LOCK);
		// An arrow on its way does not drown the lock: it only pings.
		assertThat(MissileAlarm.tone(warning(ProjectileKind.ARROW, 20), LOCK)).isEqualTo(MissileAlarm.Tone.LOCK);
	}

	@Test
	void aFireballInTheAirOutranksALock() {
		// Once it is thrown, what matters is the one coming, not the next one being aimed.
		assertThat(MissileAlarm.tone(warning(ProjectileKind.FIREBALL, 20), LOCK)).isEqualTo(MissileAlarm.Tone.LAUNCH);
	}

	@Test
	void theToneKeepsItsPitchLikeTheRealOne() {
		// A real launch warning does not speed up as the missile closes in; neither does this one.
		for (double ticks = -5; ticks <= 100; ticks += 0.5) {
			assertThat(MissileAlarm.pitch(ticks)).isEqualTo(1.0f);
		}
	}
}
