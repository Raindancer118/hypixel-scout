package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BedwarsModesTest {
	@Test
	void theModeIdIsReadTheWayPlayersSayIt() {
		assertThat(BedwarsModes.shortName("BEDWARS_EIGHT_ONE")).isEqualTo("Solo");
		assertThat(BedwarsModes.shortName("BEDWARS_EIGHT_TWO")).isEqualTo("Doubles");
		assertThat(BedwarsModes.shortName("BEDWARS_FOUR_THREE")).isEqualTo("3v3v3v3");
		assertThat(BedwarsModes.shortName("BEDWARS_FOUR_FOUR")).isEqualTo("4v4v4v4");
		assertThat(BedwarsModes.shortName("BEDWARS_TWO_FOUR")).isEqualTo("4v4");
		assertThat(BedwarsModes.shortName("bedwars_four_four")).isEqualTo("4v4v4v4");
	}

	@Test
	void aModeNobodyListedStillReadsAsWords() {
		assertThat(BedwarsModes.shortName("BEDWARS_EIGHT_TWO_ULTIMATE")).isEqualTo("Doubles Ultimate");
		assertThat(BedwarsModes.shortName("BEDWARS_CASTLE")).isEqualTo("Castle");
		assertThat(BedwarsModes.shortName(null)).isEmpty();
	}

	@Test
	void theQueueChoicesStartWithAnEmptySlotAndAreAllPlayable() {
		assertThat(BedwarsModes.QUEUEABLE.getFirst()).isEmpty();
		assertThat(BedwarsModes.QUEUEABLE).contains("bedwars_eight_one", "bedwars_four_four",
				"bedwars_two_four");
		assertThat(BedwarsModes.QUEUEABLE.stream().skip(1))
				.allMatch(mode -> mode.startsWith("bedwars_"));
	}

	@Test
	void anEmptySlotIsNamedAsSuch() {
		assertThat(BedwarsModes.shortName("")).isEmpty();
	}

	@Test
	void soloHasNoTeamChatToReportInto() {
		assertThat(BedwarsModes.hasTeammates("BEDWARS_EIGHT_ONE")).isFalse();
		assertThat(BedwarsModes.hasTeammates("BEDWARS_EIGHT_ONE_RUSH")).isFalse();
		assertThat(BedwarsModes.hasTeammates("BEDWARS_EIGHT_TWO")).isTrue();
		assertThat(BedwarsModes.hasTeammates("BEDWARS_FOUR_FOUR")).isTrue();
		assertThat(BedwarsModes.hasTeammates(null)).isFalse();
	}
}
