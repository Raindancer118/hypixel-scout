package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPacingTest {
	@Test
	void aRankedPlayerSendsAtTheChosenInterval() {
		assertThat(ChatPacing.ticksBetween(true, 10)).isEqualTo(10);
	}

	@Test
	void withoutARankHypixelsCooldownWinsOverAShorterInterval() {
		assertThat(ChatPacing.ticksBetween(false, 10)).isEqualTo(ChatPacing.UNRANKED_TICKS);
		assertThat(ChatPacing.ticksBetween(false, 100)).isEqualTo(100);
	}

	@Test
	void noIntervalIsShorterThanTheFloor() {
		assertThat(ChatPacing.ticksBetween(true, 1)).isEqualTo(ChatPacing.MIN_TICKS);
	}
}
