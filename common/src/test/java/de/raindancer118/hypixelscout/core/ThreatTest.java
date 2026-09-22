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
		assertThat(Threat.of(player(20, 10, 20))).isEqualTo(Threat.LOW);
		assertThat(Threat.of(player(200, 200, 100))).isEqualTo(Threat.MEDIUM);
		assertThat(Threat.of(player(400, 1000, 200))).isEqualTo(Threat.HIGH);
		assertThat(Threat.of(player(800, 5000, 500))).isEqualTo(Threat.EXTREME);
	}

	@Test
	void aNickIsItsOwnKindOfUnknown() {
		assertThat(Threat.of(PlayerStats.nicked("Nick", UUID.randomUUID()))).isEqualTo(Threat.NICKED);
		assertThat(Threat.of(null)).isEqualTo(Threat.UNKNOWN);
	}
}
