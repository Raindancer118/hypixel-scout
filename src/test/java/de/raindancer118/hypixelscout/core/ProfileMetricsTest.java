package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The derived numbers on the profile card: rates and per-game figures, not raw counts. */
class ProfileMetricsTest {
	private static PlayerStats.Builder base() {
		return PlayerStats.builder("Someone", UUID.randomUUID());
	}

	@Test
	void countsGamesAsWinsPlusLosses() {
		PlayerStats stats = base().games(300, 200).build();

		assertEquals(500, ProfileMetrics.gamesPlayed(stats));
		assertEquals(60.0, ProfileMetrics.winRate(stats), 1e-9);
	}

	@Test
	void givesRatesPerGame() {
		PlayerStats stats = base().games(80, 20).finals(500, 100).kills(1000, 900).build();

		assertEquals(5.0, ProfileMetrics.finalsPerGame(stats), 1e-9);
		assertEquals(10.0, ProfileMetrics.killsPerGame(stats), 1e-9);
	}

	@Test
	void givesFinalsPerStarSoAStarCountCanBeJudged() {
		// Two players with 400 stars and wildly different finals are not the same player.
		PlayerStats stats = base().stars(100).finals(2000, 0).build();

		assertEquals(20.0, ProfileMetrics.finalsPerStar(stats), 1e-9);
	}

	@Test
	void returnsZeroRatherThanInfinityForAnEmptyProfile() {
		PlayerStats stats = base().build();

		assertEquals(0.0, ProfileMetrics.winRate(stats), 1e-9);
		assertEquals(0.0, ProfileMetrics.finalsPerGame(stats), 1e-9);
		assertEquals(0.0, ProfileMetrics.finalsPerStar(stats), 1e-9);
		assertEquals(0, ProfileMetrics.gamesPlayed(stats));
	}

	@Test
	void countsBedsBrokenAgainstBedsLost() {
		PlayerStats stats = base().beds(300, 150).build();

		assertEquals(2.0, ProfileMetrics.bedRatio(stats), 1e-9);
	}
}
