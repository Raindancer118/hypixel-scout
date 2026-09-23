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

	@Test
	void aPlayerGoesOutAsWhoTheyAreThenTheirNumbers() {
		PlayerStats stats = PlayerStats.builder("Sundial", UUID.randomUUID()).stars(1502)
				.finals(40_100, 2_900).games(7_900, 1_500).beds(15_800, 1_500).kills(80_200, 8_700)
				.winstreak(104).rank("MVP_PLUS").build();

		assertThat(StatLines.chatLines("Sundial", stats, ThreatScale.ABSOLUTE)).containsExactly(
				"Sundial [MVP+] 1502* is EXTREME",
				"13.8 FKDR, 5.3 WLR, 104 winstreak, 1.7 beds and 8.5 kills a game");
	}

	@Test
	void aHiddenWinstreakAndNoRankAreLeftOutRatherThanGuessed() {
		PlayerStats stats = PlayerStats.builder("quietfox", UUID.randomUUID()).stars(212)
				.finals(2_120, 1_700).games(610, 540).build();

		assertThat(StatLines.chatLines("quietfox", stats, ThreatScale.ABSOLUTE)).containsExactly(
				"quietfox 212* is LOW", "1.2 FKDR, 1.1 WLR, 0.0 beds and 0.0 kills a game");
	}

	@Test
	void aNickIsOneLine() {
		assertThat(StatLines.chatLines("Glimmer", PlayerStats.nicked("Glimmer", UUID.randomUUID()), ThreatScale.ABSOLUTE))
				.containsExactly("Glimmer is nicked (no Hypixel profile under that name)");
	}

	@Test
	void everyLineIsPlainAsciiAndShortEnough() {
		PlayerStats stats = PlayerStats.builder("ABCDEFGHIJKLMNOP", UUID.randomUUID()).stars(10_000)
				.finals(999_999, 1).games(999_999, 1).beds(999_999, 1).kills(999_999, 1)
				.winstreak(99_999).rank("SUPERSTAR").build();

		assertThat(StatLines.chatLines("ABCDEFGHIJKLMNOP", stats, ThreatScale.ABSOLUTE)).allSatisfy(line -> {
			assertThat(line.length()).isLessThanOrEqualTo(100);
			assertThat(line.chars()).allMatch(c -> c >= 32 && c < 127);
		});
	}

	@Test
	void theChatLineSaysTheThreatAgainstWhomeverItIsMeasuredAgainst() {
		PlayerStats enemy = PlayerStats.builder("Sundial", UUID.randomUUID()).stars(1502)
				.finals(40_100, 2_900).games(7_900, 1_500).build();
		// Somebody far stronger than the Sundial of the test: to them it is an even match.
		PlayerStats veteran = PlayerStats.builder("Me", UUID.randomUUID()).stars(3000)
				.finals(100_000, 10_000).build();
		ThreatScale scale = ThreatScale.of(ThreatScale.Basis.ME, veteran, java.util.List.of());

		assertThat(StatLines.chatLines("Sundial", enemy, scale).getFirst()).isEqualTo("Sundial 1502* is MED");
		assertThat(StatLines.detail("Sundial", enemy, false, null, scale))
				.extracting(StatLines::plain).anyMatch(line -> line.contains("Threat MED"));
	}
}
