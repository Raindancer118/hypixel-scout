package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissileAlarmTest {
	private static IncomingWatch.Warning warning(ProjectileKind kind, double ticks) {
		return new IncomingWatch.Warning(1, kind, ticks, Vec.ZERO, true);
	}

	@Test
	void onlyAFireballSoundsTheMissileAlarm() {
		assertThat(MissileAlarm.sounds(warning(ProjectileKind.FIREBALL, 20))).isTrue();
		assertThat(MissileAlarm.sounds(warning(ProjectileKind.ARROW, 20))).isFalse();
		assertThat(MissileAlarm.sounds(null)).isFalse();
	}

	@Test
	void theToneKeepsItsPitchLikeTheRealOne() {
		// A real launch warning does not speed up as the missile closes in; neither does this one.
		for (double ticks = -5; ticks <= 100; ticks += 0.5) {
			assertThat(MissileAlarm.pitch(ticks)).isEqualTo(1.0f);
		}
	}
}
