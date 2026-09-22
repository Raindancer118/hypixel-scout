package de.raindancer118.hypixelscout.core;

/**
 * Whether the table belongs on screen right now.
 *
 * <p>Its own class, and tested, because the answer is the difference between something you open
 * when you want it and a wall of text you fight for the rest of the game.
 */
public final class HudVisibility {
	/** How long {@link HudMode#GAME_START} keeps the table up for at the start of a game. */
	public static final long OPENING_MILLIS = 20_000L;

	private HudVisibility() {
	}

	public static boolean visible(HudMode mode, boolean inGame, boolean opened, boolean keyHeld,
			long millisSinceStart) {
		if (!inGame || mode == HudMode.OFF) {
			return false;
		}

		if (mode == HudMode.ALWAYS) {
			return true;
		}

		// Holding the key means exactly that, and a toggle left over from the last game must not
		// keep the table up in this one.
		if (mode == HudMode.HOLD) {
			return keyHeld;
		}

		if (opened || keyHeld) {
			return true;
		}

		return mode == HudMode.GAME_START && millisSinceStart <= OPENING_MILLIS;
	}
}
