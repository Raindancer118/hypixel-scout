package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatNamesTest {
	private static final Set<String> ROSTER = Set.of("Technoblade", "Dream", "xX_Pro_Xx");

	@Test
	void findsEveryRosterNameWithItsPosition() {
		List<ChatNames.Span> spans = ChatNames.find("Dream was killed by Technoblade. FINAL KILL!",
				ROSTER::contains);

		assertThat(spans).containsExactly(
				new ChatNames.Span(0, 5, "Dream"),
				new ChatNames.Span(20, 31, "Technoblade"));
	}

	@Test
	void doesNotMatchANameInsideALongerWord() {
		assertThat(ChatNames.find("Dreamer and SuperDream", ROSTER::contains)).isEmpty();
	}

	@Test
	void underscoresArePartOfTheName() {
		assertThat(ChatNames.find("[MVP+] xX_Pro_Xx: gg", ROSTER::contains))
				.containsExactly(new ChatNames.Span(7, 16, "xX_Pro_Xx"));
	}

	@Test
	void nothingToFindInAnEmptyLine() {
		assertThat(ChatNames.find("", ROSTER::contains)).isEmpty();
		assertThat(ChatNames.find(null, ROSTER::contains)).isEmpty();
	}
}
