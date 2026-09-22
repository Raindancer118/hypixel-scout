package de.raindancer118.hypixelscout.core;

/** What a list of players is ordered by. */
public enum SortMode {
	STARS, FKDR, WLR, NAME;

	/** A hand-edited config with a typo in it falls back rather than stopping the mod. */
	public static SortMode parse(String value) {
		if (value == null) {
			return STARS;
		}

		try {
			return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return STARS;
		}
	}
}
