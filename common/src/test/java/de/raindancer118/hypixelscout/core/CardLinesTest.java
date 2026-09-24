package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CardLinesTest {
	private static final PlayerStats ALPHA = PlayerStats.builder("Alpha", UUID.randomUUID()).stars(412)
			.finals(3000, 1000).games(900, 300).beds(1200, 400).kills(5000, 2500)
			.winstreak(12).rank("MVP_PLUS").socials(Map.of("DISCORD", "alpha#1")).build();

	private static List<String> plain(List<String> lines) {
		return lines.stream().map(StatLines::plain).toList();
	}

	private static CardLines.Layout layout(int perLine, CardField... fields) {
		return new CardLines.Layout(List.of(fields), perLine, true, true);
	}

	@Test
	void theNameThenTheChosenFieldsInTheirOrderSoManyToALine() {
		List<String> lines = plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE,
				layout(2, CardField.WINSTREAK, CardField.FKDR, CardField.WLR), Map.of()));

		assertThat(lines).containsExactly("[412✫] [MVP+] Alpha", "WS 12  FKDR 3.00", "WLR 3.00");
	}

	@Test
	void starAndRankCanBeLeftOffTheName() {
		CardLines.Layout bare = new CardLines.Layout(List.of(CardField.FKDR), 3, false, false);
		assertThat(plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE, bare, Map.of())).getFirst())
				.isEqualTo("Alpha");
		CardLines.Layout rankOnly = new CardLines.Layout(List.of(CardField.FKDR), 3, false, true);
		assertThat(plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE, rankOnly, Map.of())).getFirst())
				.isEqualTo("[MVP+] Alpha");
	}

	@Test
	void aFieldWithNothingToSayIsLeftOutRatherThanShownEmpty() {
		PlayerStats hidden = PlayerStats.builder("Beta", UUID.randomUUID()).stars(10).finals(10, 10).build();
		List<String> lines = plain(CardLines.lines("Beta", hidden, false, null, ThreatScale.ABSOLUTE,
				layout(3, CardField.WINSTREAK, CardField.SOCIALS, CardField.FKDR), Map.of()));

		assertThat(lines).containsExactly("[10✫] Beta", "FKDR 1.00");
	}

	@Test
	void everyFieldSaysSomethingForAFullProfile() {
		for (CardField field : CardField.values()) {
			if (field == CardField.CHEATS || field == CardField.LAST_LOGIN || field == CardField.ACCOUNT_AGE) {
				continue;
			}
			List<String> lines = CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE, layout(3, field), Map.of());
			assertThat(lines).as(field.name()).hasSize(2);
		}
	}

	@Test
	void theCheatFieldIsWhateverTheCallerKnows() {
		List<String> lines = plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE,
				layout(3, CardField.CHEATS, CardField.FKDR), Map.of(CardField.CHEATS, "§c⚠ 91%")));
		assertThat(lines.get(1)).isEqualTo("⚠ 91%  FKDR 3.00");

		List<String> clean = plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE,
				layout(3, CardField.CHEATS, CardField.FKDR), Map.of()));
		assertThat(clean.get(1)).isEqualTo("FKDR 3.00");
	}

	@Test
	void nobodyKnownYetOrNickedSaysSoWhateverTheFields() {
		assertThat(plain(CardLines.lines("Alpha", null, true, null, ThreatScale.ABSOLUTE, layout(3, CardField.FKDR), Map.of())))
				.containsExactly("Alpha", "Looking them up…");
		assertThat(plain(CardLines.lines("Alpha", null, false, "Invalid API key", ThreatScale.ABSOLUTE, layout(3, CardField.FKDR), Map.of())))
				.containsExactly("Alpha", "Invalid API key");
		assertThat(plain(CardLines.lines("Alpha", PlayerStats.nicked("Alpha", UUID.randomUUID()), false, null,
				ThreatScale.ABSOLUTE, layout(3, CardField.FKDR), Map.of()))).containsExactly("Alpha", "NICK");
	}

	@Test
	void noFieldsIsJustTheName() {
		assertThat(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE, layout(3), Map.of())).hasSize(1);
	}

	@Test
	void theDefaultsSayWhatTheCardsAlwaysSaid() {
		List<String> tooltip = plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE,
				CardLines.Layout.TOOLTIP, Map.of()));
		assertThat(String.join(" ", tooltip)).contains("FKDR 3.00", "WLR 3.00", "WS 12", "B/G 1.00", "K/G 4.17", "discord");

		List<String> popup = plain(CardLines.lines("Alpha", ALPHA, false, null, ThreatScale.ABSOLUTE,
				CardLines.Layout.POPUP, Map.of()));
		assertThat(popup).hasSize(2);
		assertThat(popup.get(1)).contains("FKDR 3.00", "WS 12");
	}
}
