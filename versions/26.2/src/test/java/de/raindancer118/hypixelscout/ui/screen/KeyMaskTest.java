package de.raindancer118.hypixelscout.ui.screen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyMaskTest {
	@Test
	void onlyTheFirstBlockOfTheKeyStaysReadable() {
		assertThat(SettingsScreen.mask("0f1e2d3c-4b5a-6978", 0)).isEqualTo("0f1e2d3c-••••-••••");
	}

	@Test
	void theOffsetIsWhereTheVisiblePartStartsInTheWholeKey() {
		// A scrolled field hands over only what is visible, starting further in.
		assertThat(SettingsScreen.mask("2d3c-4b5a", 4)).isEqualTo("2d3c-••••");
	}
}
