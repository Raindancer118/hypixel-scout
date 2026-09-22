package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GameStartTest {
	private static Map<String, String> teams(String... pairs) {
		Map<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@Test
	void theWaitingLobbyHasNoTeamsYet() {
		assertThat(GameStart.hasStarted("BEDWARS_FOUR_FOUR",
				teams("Me", "", "Alpha", "", "Bravo", ""), "Me")).isFalse();
	}

	@Test
	void everybodyInOneTeamIsTheLobbyNotTheGame() {
		// Some lobbies colour every name alike; more players in a team than the mode allows is no team.
		assertThat(GameStart.hasStarted("BEDWARS_FOUR_FOUR",
				teams("Me", "Gray", "A", "Gray", "B", "Gray", "C", "Gray", "D", "Gray"), "Me")).isFalse();
	}

	@Test
	void realTeamsWithTheOwnPlayerInOneMeanTheGameIsOn() {
		assertThat(GameStart.hasStarted("BEDWARS_FOUR_FOUR",
				teams("Me", "Red", "A", "Red", "B", "Blue", "C", "Blue"), "Me")).isTrue();
	}

	@Test
	void theOwnPlayerHasToBeInATeam() {
		assertThat(GameStart.hasStarted("BEDWARS_EIGHT_TWO",
				teams("Me", "", "A", "Red", "B", "Blue"), "Me")).isFalse();
	}

	@Test
	void soloPlayersAreEachTheirOwnTeam() {
		assertThat(GameStart.hasStarted("BEDWARS_EIGHT_ONE",
				teams("Me", "Red", "A", "Blue", "B", "Green"), "Me")).isTrue();
		assertThat(GameStart.hasStarted("BEDWARS_EIGHT_ONE",
				teams("Me", "Red", "A", "Red"), "Me")).isFalse();
	}

	@Test
	void theStartLineInChatCountsAsWell() {
		assertThat(GameStart.isStartLine("Protect your bed and destroy the enemy beds.")).isTrue();
		assertThat(GameStart.isStartLine("  Protect your bed and destroy the enemy beds.  ")).isTrue();
		assertThat(GameStart.isStartLine("Alpha: protect your bed and destroy the enemy beds.")).isFalse();
		assertThat(GameStart.isStartLine(null)).isFalse();
	}

	@Test
	void theTeamSizeFollowsTheMode() {
		assertThat(BedwarsModes.teamSize("BEDWARS_EIGHT_ONE")).isEqualTo(1);
		assertThat(BedwarsModes.teamSize("BEDWARS_EIGHT_TWO")).isEqualTo(2);
		assertThat(BedwarsModes.teamSize("BEDWARS_FOUR_THREE")).isEqualTo(3);
		assertThat(BedwarsModes.teamSize("BEDWARS_FOUR_FOUR_RUSH")).isEqualTo(4);
		assertThat(BedwarsModes.teamSize("BEDWARS_TWO_FOUR")).isEqualTo(4);
		assertThat(BedwarsModes.teamSize("BEDWARS_CASTLE")).isEqualTo(Integer.MAX_VALUE);
	}
}
