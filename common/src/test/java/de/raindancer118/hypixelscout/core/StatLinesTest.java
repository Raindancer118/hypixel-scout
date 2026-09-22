package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatLinesTest {
	private static String plain(String formatted) {
		return formatted.replaceAll("§.", "");
	}

	@Test
	void aPlayerStillBeingLookedUpSaysSo() {
		assertThat(StatLines.detail("Alpha", null, true, null)).extracting(StatLines::plain)
				.containsExactly("Alpha", "Looking them up…");
	}

	@Test
	void aFailedLookupGivesTheReason() {
		assertThat(StatLines.detail("Alpha", null, false, "Invalid API key"))
				.extracting(StatLines::plain).containsExactly("Alpha", "Invalid API key");
	}

	@Test
	void aNickIsSaidOutLoud() {
		assertThat(StatLines.detail("Alpha", PlayerStats.nicked("Alpha", UUID.randomUUID()), false, null))
				.extracting(StatLines::plain).startsWith("Alpha", "Nicked");
	}

	@Test
	void aProfileHasTheHeadlineNumbersAndTheLinks() {
		PlayerStats stats = PlayerStats.builder("Alpha", UUID.randomUUID()).stars(412)
				.finals(3000, 1000).games(900, 300).beds(1200, 400).kills(5000, 2500)
				.winstreak(12).rank("MVP_PLUS").socials(Map.of("DISCORD", "alpha#1")).build();

		var lines = StatLines.detail("Alpha", stats, false, null);

		assertThat(plain(lines.get(0))).isEqualTo("[412✫] [MVP+] Alpha");
		assertThat(lines).extracting(StatLines::plain).anyMatch(line -> line.contains("FKDR 3.00"));
		assertThat(lines).extracting(StatLines::plain).anyMatch(line -> line.contains("WLR 3.00"));
		assertThat(lines).extracting(StatLines::plain).anyMatch(line -> line.contains("Streak 12"));
		assertThat(lines).extracting(StatLines::plain).anyMatch(line -> line.contains("discord"));
		// 1200 beds and 5000 kills over 1200 games.
		assertThat(lines).extracting(StatLines::plain)
				.anyMatch(line -> line.contains("Beds/game 1.00") && line.contains("Kills/game 4.17"));
	}

	@Test
	void theNameLineCarriesStarRankAndName() {
		PlayerStats stats = PlayerStats.builder("Alpha", UUID.randomUUID()).stars(1203).rank("SUPERSTAR").build();

		assertThat(plain(StatLines.name("Alpha", stats))).isEqualTo("[1203✪] [MVP++] Alpha");
		assertThat(plain(StatLines.name("Alpha", null))).isEqualTo("Alpha");
	}
}
