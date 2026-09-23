package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatCalloutTest {
	private static PlayerStats player(String name, int stars, int fk, int fd, Integer streak) {
		return PlayerStats.builder(name, UUID.randomUUID()).stars(stars).finals(fk, fd).winstreak(streak).build();
	}

	private static Map<String, List<PlayerStats>> game() {
		Map<String, List<PlayerStats>> teams = new LinkedHashMap<>();
		teams.put("Blue", Arrays.asList(player("Brickmason", 488, 6_900, 1_150, 12),
				player("Nimbus_07", 64, 180, 310, 0), PlayerStats.nicked("Glimmer", UUID.randomUUID())));
		teams.put("Yellow", Arrays.asList(player("Sundial", 1502, 40_100, 2_900, 104),
				player("PaperCrane", 44, 90, 160, 0)));
		teams.put("Green", Arrays.asList(player("Lanternfish", 731, 15_200, 1_380, 61)));
		return teams;
	}

	@Test
	void oneLinePerDangerousPlayerMostDangerousFirstWithTheirTeam() {
		assertThat(ThreatCallout.lines(game(), 50, ThreatScale.ABSOLUTE, 8)).containsExactly(
				"YELLOW Sundial 1502* - EXTREME - 13.8 FKDR - 104 WS",
				"GREEN Lanternfish 731* - EXTREME - 11.0 FKDR - 61 WS",
				"BLUE Brickmason 488* - HIGH - 6.0 FKDR",
				"BLUE Glimmer is nicked");
	}

	@Test
	void aShortWinstreakIsNotWorthMentioning() {
		assertThat(ThreatCallout.lines(game(), 50, ThreatScale.ABSOLUTE, 8))
				.noneMatch(line -> line.contains("12 WS"));
	}

	@Test
	void aLongReportIsCutAndSaysHowManyMore() {
		assertThat(ThreatCallout.lines(game(), 50, ThreatScale.ABSOLUTE, 2)).containsExactly(
				"YELLOW Sundial 1502* - EXTREME - 13.8 FKDR - 104 WS",
				"GREEN Lanternfish 731* - EXTREME - 11.0 FKDR - 61 WS",
				"+2 more, see the Scout screen");
	}

	@Test
	void nobodyDangerousIsOneLineSayingSo() {
		Map<String, List<PlayerStats>> teams = new LinkedHashMap<>();
		teams.put("Blue", Arrays.asList(player("Nimbus_07", 64, 180, 310, 0), null));
		teams.put("Red", Arrays.asList(player("PaperCrane", 44, 90, 160, 0)));

		assertThat(ThreatCallout.lines(teams, 50, ThreatScale.ABSOLUTE, 8))
				.containsExactly("No dangerous enemies (2 LOW, 1 not looked up yet)");
	}

	@Test
	void aLowPlayerOnALongRunIsStillCalledOut() {
		Map<String, List<PlayerStats>> teams = new LinkedHashMap<>();
		teams.put("Red", Arrays.asList(player("Streaky", 40, 100, 100, 80)));

		assertThat(ThreatCallout.lines(teams, 50, ThreatScale.ABSOLUTE, 8))
				.containsExactly("RED Streaky 40* - LOW - 1.0 FKDR - 80 WS");
	}
}
