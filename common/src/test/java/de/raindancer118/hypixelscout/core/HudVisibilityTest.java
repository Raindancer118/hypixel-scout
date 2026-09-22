package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When the table is on screen.
 *
 * <p>It is opened and closed like a window, because a permanent block of text over the left half
 * of the screen is worse than no mod at all.
 */
class HudVisibilityTest {
	@Test
	void staysClosedUntilItIsOpened() {
		assertFalse(HudVisibility.visible(HudMode.TOGGLE, true, false, false, 0L));
		assertTrue(HudVisibility.visible(HudMode.TOGGLE, true, true, false, 0L));
	}

	@Test
	void neverShowsOutsideAGameHoweverItWasOpened() {
		for (HudMode mode : HudMode.values()) {
			assertFalse(HudVisibility.visible(mode, false, true, true, 0L),
					mode + " must stay hidden outside a game");
		}
	}

	@Test
	void showsNothingWhenSwitchedOff() {
		assertFalse(HudVisibility.visible(HudMode.OFF, true, true, true, 0L),
				"off means off, even if the toggle was left on");
	}

	@Test
	void staysUpForeverWhenAsked() {
		assertTrue(HudVisibility.visible(HudMode.ALWAYS, true, false, false, 999_000L));
	}

	@Test
	void showsOnlyWhileTheKeyIsHeldInHoldMode() {
		assertTrue(HudVisibility.visible(HudMode.HOLD, true, false, true, 0L));
		assertFalse(HudVisibility.visible(HudMode.HOLD, true, true, false, 0L),
				"holding is the whole point of hold mode; a stale toggle must not keep it up");
	}

	@Test
	void opensItselfForTheStartOfTheGameAndThenCloses() {
		assertTrue(HudVisibility.visible(HudMode.GAME_START, true, false, false, 0L));
		assertFalse(HudVisibility.visible(HudMode.GAME_START, true, false, false,
				HudVisibility.OPENING_MILLIS + 1), "once the rush starts the screen is the game's");
	}

	@Test
	void canBeOpenedAgainByHandAfterItClosedItself() {
		assertTrue(HudVisibility.visible(HudMode.GAME_START, true, true, false, 999_000L));
	}
}
