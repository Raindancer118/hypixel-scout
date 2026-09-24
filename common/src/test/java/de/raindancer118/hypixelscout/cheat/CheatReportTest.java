package de.raindancer118.hypixelscout.cheat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheatReportTest {
	private final Suspicion suspicion = new Suspicion();

	private void seen(String player, Check check, int times) {
		for (int i = 0; i < times; i++) {
			suspicion.record(new Violation(player, check, "4.2 blocks", 0), 1.0);
		}
	}

	@Test
	void onePlainLinePerFlaggedPlayerWithTeamConfidenceAndEveryCheck() {
		seen("Sundial", Check.REACH, 4);
		seen("Sundial", Check.SCAFFOLD, 8);
		seen("Brick", Check.FLY, 2);
		seen("Nobody", Check.SPEED, 1);

		int sundial = (int) Math.round(suspicion.confidence("Sundial") * 100);
		int brick = (int) Math.round(suspicion.confidence("Brick") * 100);
		assertThat(CheatReport.lines(suspicion, name -> name.equals("Sundial") ? "Yellow" : "")).containsExactly(
				"CHEATER? YELLOW Sundial " + sundial + "% sure - Reach x4, Scaffold x8",
				"CHEATER? Brick " + brick + "% sure - Fly x2");
	}

	@Test
	void theSurestComesFirst() {
		seen("Maybe", Check.SCAFFOLD, 7);
		seen("Surely", Check.NUKER, 1);
		seen("Surely", Check.FLY, 3);

		assertThat(CheatReport.lines(suspicion, name -> "")).first().asString().contains("Surely");
	}

	@Test
	void theLineStaysPlainAsciiAndShortEnoughForChat() {
		for (Check check : Check.values()) {
			seen("A_Very_Long_Name", check, 12);
		}
		String line = CheatReport.lines(suspicion, name -> "Aqua").getFirst();

		assertThat(line).hasSizeLessThanOrEqualTo(CheatReport.MAX_LENGTH).matches("\\p{ASCII}+");
		assertThat(line).startsWith("CHEATER? AQUA A_Very_Long_Name ");
	}

	@Test
	void nobodyFlaggedIsNoLine() {
		assertThat(CheatReport.lines(suspicion, name -> "")).isEmpty();
	}
}
