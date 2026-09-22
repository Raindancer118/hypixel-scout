package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The combined view of an enemy team: the numbers you would want called out in party chat before
 * the rush.
 */
class TeamReportTest {
	private static PlayerStats player(String name, int stars, int fk, int fd, int w, int l,
			Integer streak) {
		return PlayerStats.builder(name, UUID.randomUUID())
				.stars(stars).finals(fk, fd).games(w, l).winstreak(streak).build();
	}

	@Test
	void addsTheStarsUp() {
		TeamReport report = TeamReport.of("RED", Arrays.asList(
				player("A", 100, 0, 0, 0, 0, null),
				player("B", 250, 0, 0, 0, 0, null)));

		assertEquals(350, report.getCombinedStars());
		assertEquals(2, report.getSize());
	}

	@Test
	void poolsTheKillsAndDeathsRatherThanAveragingTheRatios() {
		// A 10.0 player and a 0.5 player are not a team of 5.25: the finals go in one pile and the
		// deaths in another, which is what actually happens in the game.
		TeamReport report = TeamReport.of("BLUE", Arrays.asList(
				player("A", 0, 1000, 100, 0, 0, null),
				player("B", 0, 50, 100, 0, 0, null)));

		assertEquals(1050.0 / 200.0, report.getCombinedFkdr(), 1e-9);
	}

	@Test
	void poolsTheWinsAndLossesTheSameWay() {
		TeamReport report = TeamReport.of("BLUE", Arrays.asList(
				player("A", 0, 0, 0, 300, 100, null),
				player("B", 0, 0, 0, 100, 300, null)));

		assertEquals(1.0, report.getCombinedWlr(), 1e-9);
	}

	@Test
	void namesOnlyTheDangerousWinstreaks() {
		TeamReport report = TeamReport.of("RED", Arrays.asList(
				player("Quiet", 0, 0, 0, 0, 0, Integer.valueOf(12)),
				player("Hot", 0, 0, 0, 0, 0, Integer.valueOf(63)),
				player("Hidden", 0, 0, 0, 0, 0, null)));

		List<String> called = new ArrayList<String>();
		for (TeamReport.Streak streak : report.streaksAbove(50)) {
			called.add(streak.getName() + ":" + streak.getWinstreak());
		}

		assertEquals(Arrays.asList("Hot:63"), called);
	}

	@Test
	void leavesNickedPlayersOutOfTheTotalsButStillCountsThem() {
		// Nobody knows a nick's stats, so folding a zero into the ratios would understate the team.
		TeamReport report = TeamReport.of("RED", Arrays.asList(
				player("Known", 200, 400, 100, 0, 0, null),
				PlayerStats.nicked("Nicked", UUID.randomUUID())));

		assertEquals(200, report.getCombinedStars());
		assertEquals(4.0, report.getCombinedFkdr(), 1e-9);
		assertEquals(2, report.getSize());
		assertEquals(1, report.getUnknown(), "the message has to admit what it could not see");
	}

	@Test
	void fitsInOneChatMessage() {
		List<PlayerStats> team = new ArrayList<PlayerStats>();
		for (int i = 0; i < 4; i++) {
			team.add(player("LongestPossibleN" + i, 999, 9999, 999, 9999, 999,
					Integer.valueOf(120)));
		}

		String line = TeamReport.of("RED", team).toChatMessage(50);

		assertTrue(line.length() <= 256, "Minecraft refuses to send anything longer: " + line);
		assertTrue(line.startsWith("RED"), line);
		assertFalse(line.contains("§"), "party chat strips colour codes anyway");
	}

	@Test
	void saysSoWhenNothingIsKnownAtAll() {
		TeamReport report = TeamReport.of("RED",
				Arrays.asList(PlayerStats.nicked("Nicked", UUID.randomUUID())));

		assertEquals(0.0, report.getCombinedFkdr(), 1e-9);
		assertTrue(report.toChatMessage(50).contains("1 unknown"));
	}
}
