package de.raindancer118.hypixelscout.core;

/** How the in-game table behaves. */
public enum HudMode {
	/** Never drawn. The lobby screen is still there when the numbers are wanted. */
	OFF,
	/** Closed until the key opens it, open until the key closes it again. */
	TOGGLE,
	/** Visible only while the key is held, the way the tab list works. */
	HOLD,
	/** Opens itself for the first seconds of a game, then closes. The key still works. */
	GAME_START,
	/** The whole game long. */
	ALWAYS;

	public static HudMode parse(String value) {
		if (value == null) {
			return TOGGLE;
		}

		try {
			return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return TOGGLE;
		}
	}
}
