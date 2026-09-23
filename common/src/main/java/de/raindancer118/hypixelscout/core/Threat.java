package de.raindancer118.hypixelscout.core;

import java.util.Arrays;
import java.util.List;

/**
 * How dangerous a player is, in one word.
 *
 * <p>Based on the index Bedwars players already use — stars times FKDR squared — because it weighs
 * the two things that actually decide a fight: time played and how often it is won. The bands are
 * where the community draws them, rounded: LOW ends at 500, MED at 3 000 and EXTREME starts at
 * 30 000; NONE, VERY HIGH and INSANE split the ends and the wide middle further.
 *
 * <p>Each rated level carries two lower edges: one on the index itself, for the fixed bands, and one
 * on the ratio to a reference, for {@link ThreatScale}'s relative bands (1 = an even match).
 */
public enum Threat {
	UNKNOWN("§8", "?", Double.NaN, Double.NaN),
	NICKED("§d", "NICK", Double.NaN, Double.NaN),
	NONE("§7", "NONE", 0, 0),
	LOW("§a", "LOW", 100, 0.2),
	MEDIUM("§e", "MED", 500, 0.5),
	HIGH("§6", "HIGH", 3_000, 1.5),
	VERY_HIGH("§c", "V.HIGH", 10_000, 2.5),
	EXTREME("§4", "EXTREME", 30_000, 4.0),
	INSANE("§5", "INSANE", 150_000, 10.0);

	private static final List<Threat> RATED = Arrays.stream(values()).filter(Threat::isRated).toList();

	private final String colour;
	private final String label;
	private final double indexFrom;
	private final double ratioFrom;

	Threat(String colour, String label, double indexFrom, double ratioFrom) {
		this.colour = colour;
		this.label = label;
		this.indexFrom = indexFrom;
		this.ratioFrom = ratioFrom;
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

		return ofIndex(index(stats));
	}

	/** The level on the fixed bands for an index. */
	public static Threat ofIndex(double index) {
		Threat level = NONE;
		for (Threat threat : RATED) {
			if (index >= threat.indexFrom) {
				level = threat;
			}
		}
		return level;
	}

	/** The level on the relative bands for an index divided by the reference it is measured against. */
	public static Threat ofRatio(double ratio) {
		Threat level = NONE;
		for (Threat threat : RATED) {
			if (ratio >= threat.ratioFrom) {
				level = threat;
			}
		}
		return level;
	}

	/** The levels a player with known stats can have, harmless first. */
	public static List<Threat> rated() {
		return RATED;
	}

	/** Whether this is a level on the scale, rather than "unknown" or "nicked". */
	public boolean isRated() {
		return this != UNKNOWN && this != NICKED;
	}

	/** Section-sign colour code, for text built the way {@link StatFormat} builds it. */
	public String colour() {
		return colour;
	}

	public String label() {
		return label;
	}
}
