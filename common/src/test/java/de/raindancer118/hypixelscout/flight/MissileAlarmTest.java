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
	void theToneStartsCalmAndRisesAsItClosesIn() {
		assertThat(MissileAlarm.pitch(60)).isEqualTo(MissileAlarm.CALM_PITCH);
		assertThat(MissileAlarm.pitch(MissileAlarm.CLOSING_TICKS)).isEqualTo(MissileAlarm.CALM_PITCH);
		assertThat(MissileAlarm.pitch(10)).isGreaterThan(MissileAlarm.pitch(20));
		assertThat(MissileAlarm.pitch(0)).isEqualTo(MissileAlarm.IMPACT_PITCH);
	}

	@Test
	void thePitchStaysWithinWhatTheSoundEngineTakes() {
		for (double ticks = -5; ticks <= 100; ticks += 0.5) {
			assertThat(MissileAlarm.pitch(ticks)).isBetween(0.5f, 2.0f);
		}
	}
}
