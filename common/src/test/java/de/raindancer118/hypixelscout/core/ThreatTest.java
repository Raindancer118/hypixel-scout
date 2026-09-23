package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatTest {
	private static PlayerStats player(int stars, int finalKills, int finalDeaths) {
		return PlayerStats.builder("Someone", UUID.randomUUID()).stars(stars)
				.finals(finalKills, finalDeaths).build();
	}

	@Test
	void theIndexIsStarsTimesTheSquareOfTheFkdr() {
		assertThat(Threat.index(player(100, 300, 100))).isEqualTo(900.0);
	}

	@Test
	void unknownAndNickedPlayersHaveNoIndex() {
		assertThat(Threat.index(null)).isNegative();
		assertThat(Threat.index(PlayerStats.nicked("Nick", UUID.randomUUID()))).isNegative();
	}

	@Test
	void theLevelRisesWithTheIndex() {
		assertThat(Threat.of(player(20, 10, 20))).isEqualTo(Threat.NONE);
		assertThat(Threat.of(player(200, 100, 100))).isEqualTo(Threat.LOW);
		assertThat(Threat.of(player(200, 200, 100))).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.of(player(300, 400, 100))).isEqualTo(Threat.HIGH);
		assertThat(Threat.of(player(400, 1000, 200))).isEqualTo(Threat.VERY_HIGH);
		assertThat(Threat.of(player(800, 5000, 500))).isEqualTo(Threat.EXTREME);
		assertThat(Threat.of(player(1500, 40_000, 2_900))).isEqualTo(Threat.INSANE);
	}

	@Test
	void theOldBandEdgesStillHold() {
		// LOW ends at 500, MED at 3 000 and EXTREME starts at 30 000, as before there were seven levels.
		assertThat(Threat.ofIndex(499)).isEqualTo(Threat.LOW);
		assertThat(Threat.ofIndex(500)).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.ofIndex(2_999)).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.ofIndex(29_999)).isEqualTo(Threat.VERY_HIGH);
		assertThat(Threat.ofIndex(30_000)).isEqualTo(Threat.EXTREME);
	}

	@Test
	void theRatedLevelsAreInOrderAndLeaveOutTheUnknownOnes() {
		assertThat(Threat.rated()).containsExactly(Threat.NONE, Threat.LOW, Threat.MEDIUM, Threat.HIGH,
				Threat.VERY_HIGH, Threat.EXTREME, Threat.INSANE);
		assertThat(Threat.UNKNOWN.isRated()).isFalse();
		assertThat(Threat.NICKED.isRated()).isFalse();
	}

	@Test
	void everyLabelIsShortAsciiForTheTableAndChat() {
		for (Threat threat : Threat.values()) {
			assertThat(threat.label()).hasSizeLessThanOrEqualTo(7);
			assertThat(threat.label().chars()).allMatch(c -> c >= 32 && c < 127);
		}
	}

	@Test
	void aNickIsItsOwnKindOfUnknown() {
		assertThat(Threat.of(PlayerStats.nicked("Nick", UUID.randomUUID()))).isEqualTo(Threat.NICKED);
		assertThat(Threat.of(null)).isEqualTo(Threat.UNKNOWN);
	}
}
