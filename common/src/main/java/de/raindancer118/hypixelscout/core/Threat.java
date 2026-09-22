package de.raindancer118.hypixelscout.core;

/**
 * How dangerous a player is, in one word.
 *
 * <p>Based on the index Bedwars players already use — stars times FKDR squared — because it weighs
 * the two things that actually decide a fight: time played and how often it is won. The bands are
 * where the community draws them, rounded.
 */
public enum Threat {
	UNKNOWN("§8", "?"),
	NICKED("§d", "NICK"),
	LOW("§a", "LOW"),
	MEDIUM("§e", "MED"),
	HIGH("§c", "HIGH"),
	EXTREME("§4", "EXTREME");

	private final String colour;
	private final String label;

	Threat(String colour, String label) {
		this.colour = colour;
		this.label = label;
	}

	/** Stars × FKDR², or {@code -1} for somebody there are no numbers for. */
	public static double index(PlayerStats stats) {
		if (stats == null || stats.isNicked()) {
			return -1.0;
		}

		double fkdr = stats.getFkdr();
		return stats.getStars() * fkdr * fkdr;
	}

	public static Threat of(PlayerStats stats) {
		if (stats == null) {
			return UNKNOWN;
		}
		if (stats.isNicked()) {
			return NICKED;
		}

		double index = index(stats);
		if (index < 500) {
			return LOW;
		}
		if (index < 3_000) {
			return MEDIUM;
		}
		if (index < 30_000) {
			return HIGH;
		}

		return EXTREME;
	}

	/** Section-sign colour code, for text built the way {@link StatFormat} builds it. */
	public String colour() {
		return colour;
	}

	public String label() {
		return label;
	}
}
