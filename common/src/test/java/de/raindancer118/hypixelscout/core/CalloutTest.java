package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CalloutTest {
	private static final PlayerStats SUNDIAL = TypicalPlayers.like("Sundial", 1502, 40_100, 2_900, 104);

	private static Callout.Target sundial() {
		return new Callout.Target("Sundial", "Red", SUNDIAL, 17.6);
	}

	@Test
	void theTeamIsReadOffTheTargetInCapitals() {
		assertThat(Callout.render("{team} inc", sundial(), ThreatScale.ABSOLUTE).text()).isEqualTo("RED inc");
	}

	@Test
	void everyPlaceholderIsFilledIn() {
		Callout.Result result = Callout.render(
				"{team} {name} {stars}* {threat} {fkdr} FKDR {wlr} WLR {bblr} BBLR {ws} WS {distance}m",
				sundial(), ThreatScale.ABSOLUTE);

		assertThat(result.problem()).isEqualTo(Callout.Problem.NONE);
		assertThat(result.text()).isEqualTo("RED Sundial 1502* INSANE 13.8 FKDR "
				+ StatLines.oneDecimal(SUNDIAL.getWlr()) + " WLR "
				+ StatLines.oneDecimal(ProfileMetrics.bedRatio(SUNDIAL)) + " BBLR 104 WS 18m");
	}

	@Test
	void placeholdersAreReadWhateverTheirCase() {
		assertThat(Callout.render("{TEAM} {Name} inc", sundial(), ThreatScale.ABSOLUTE).text())
				.isEqualTo("RED Sundial inc");
	}

	@Test
	void aMessageWithoutPlaceholdersNeedsNobodyToAimAt() {
		Callout.Result result = Callout.render("Going mid, cover me", null, ThreatScale.ABSOLUTE);

		assertThat(result.problem()).isEqualTo(Callout.Problem.NONE);
		assertThat(result.text()).isEqualTo("Going mid, cover me");
	}

	@Test
	void aPlaceholderWithNobodyAimedAtIsRefused() {
		assertThat(Callout.render("{team} inc", null, ThreatScale.ABSOLUTE).problem())
				.isEqualTo(Callout.Problem.NO_TARGET);
	}

	@Test
	void theTeamOfSomebodyWithoutOneIsRefusedRatherThanSentBlank() {
		Callout.Target teamless = new Callout.Target("Sundial", "", SUNDIAL, 5);

		assertThat(Callout.render("{team} inc", teamless, ThreatScale.ABSOLUTE).problem())
				.isEqualTo(Callout.Problem.NO_TEAM);
		// A message that does not ask for the team does not care.
		assertThat(Callout.render("{name} inc", teamless, ThreatScale.ABSOLUTE).text()).isEqualTo("Sundial inc");
	}

	@Test
	void statsNotKnownYetAreAQuestionMarkAndANickSaysSo() {
		Callout.Target unknown = new Callout.Target("Sundial", "Red", null, 5);
		Callout.Target nick = new Callout.Target("Glimmer", "Blue", PlayerStats.nicked("Glimmer", UUID.randomUUID()), 5);

		assertThat(Callout.render("{team} {name} {stars}* {threat} {fkdr}", unknown, ThreatScale.ABSOLUTE).text())
				.isEqualTo("RED Sundial ?* ? ?");
		assertThat(Callout.render("{team} {name} {stars}* {threat}", nick, ThreatScale.ABSOLUTE).text())
				.isEqualTo("BLUE Glimmer ?* NICK");
	}

	@Test
	void aHiddenWinstreakIsAQuestionMarkNotZero() {
		PlayerStats hidden = TypicalPlayers.like("Quiet", 200, 2000, 1000, null);

		assertThat(Callout.render("{ws}", new Callout.Target("Quiet", "Red", hidden, 3), ThreatScale.ABSOLUTE).text())
				.isEqualTo("?");
	}

	@Test
	void unknownPlaceholdersStayAsTyped() {
		assertThat(Callout.render("{team} {weather}", sundial(), ThreatScale.ABSOLUTE).text()).isEqualTo("RED {weather}");
	}

	@Test
	void anEmptyMessageIsNotSent() {
		assertThat(Callout.render("   ", sundial(), ThreatScale.ABSOLUTE).problem()).isEqualTo(Callout.Problem.EMPTY);
		assertThat(Callout.render(null, sundial(), ThreatScale.ABSOLUTE).problem()).isEqualTo(Callout.Problem.EMPTY);
	}

	@Test
	void theLineIsCutToWhatChatTakesAndKeptToOneLine() {
		String result = Callout.render("{team} " + "x".repeat(200) + "\nsecond", sundial(), ThreatScale.ABSOLUTE).text();

		assertThat(result).hasSizeLessThanOrEqualTo(StatLines.MAX_CHAT).doesNotContain("\n").startsWith("RED ");
	}

	@Test
	void whetherAMessageNeedsATarget() {
		assertThat(Callout.needsTarget("{team} inc")).isTrue();
		assertThat(Callout.needsTarget("{DISTANCE}")).isTrue();
		assertThat(Callout.needsTarget("Going mid")).isFalse();
		assertThat(Callout.needsTarget("{weather}")).isFalse();
	}
}
