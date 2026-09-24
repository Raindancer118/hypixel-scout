package de.raindancer118.hypixelscout.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * How dangerous a player is, in one word.
 *
 * <p>Two indices, one per kind of danger ({@link ThreatFocus}), both built on the one Bedwars players
 * already use — stars times FKDR squared — and scaled so that for an ordinary player they come out
 * as exactly that number. Everything else only moves a player off it:
 *
 * <ul>
 *   <li><b>Combat</b>: stars × FKDR<sup>1.4</sup> × (1.5 KDR)<sup>0.35</sup> × (2 WLR)<sup>0.25</sup>,
 *       times how many finals a game they get and their current winstreak. The FKDR still weighs
 *       most; KDR and WLR tell the player who only farms finals from the one who wins fights.</li>
 *   <li><b>Beds</b>: stars × (1.25 BBLR)<sup>1.4</sup> × (2 WLR)<sup>0.6</sup>, times how many beds a
 *       game they break and their winstreak — the rusher, not the camper.</li>
 * </ul>
 *
 * <p>The factors (1.5, 2, 1.25) are what an ordinary player's KDR, WLR and BBLR are short of their
 * FKDR, so a typical profile lands on stars × FKDR² in both. The per-game figures are taken to the
 * fourth root and held between half and double: they say something about the style, and a broken
 * number must not make or unmake a player. A winstreak adds one percent per win, up to double.
 *
 * <p>The bands are where the community draws them, rounded: LOW ends at 500, MED at 3 000 and
 * EXTREME starts at 30 000; NONE, VERY HIGH and INSANE split the ends and the wide middle further.
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

	private static final List<Threat> RATED = Collections.unmodifiableList(
			Arrays.stream(values()).filter(Threat::isRated).collect(Collectors.toList()));

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

	/** How far an ordinary player's KDR is short of their FKDR. */
	public static final double KDR_TO_FKDR = 1.5;
	/** How far an ordinary player's WLR is short of their FKDR. */
	public static final double WLR_TO_FKDR = 2.0;
	/** How far an ordinary player's beds broken against lost are short of their FKDR. */
	public static final double BBLR_TO_FKDR = 1.25;
	/** Finals in an average game, across the modes. */
	public static final double TYPICAL_FINALS_PER_GAME = 1.2;
	/** Beds broken in an average game, across the modes. */
	public static final double TYPICAL_BEDS_PER_GAME = 0.8;
	/** The most a per-game figure can multiply an index by, and one over the least. */
	public static final double MAX_PER_GAME_FACTOR = 2.0;
	/** A winstreak of this length or more doubles an index. */
	public static final int FULL_STREAK = 100;

	/** The combat index, or {@code -1} for somebody there are no numbers for. */
	public static double combatIndex(PlayerStats stats) {
		if (stats == null || stats.isNicked()) {
			return -1.0;
		}

		double ratios = Math.pow(stats.getFkdr(), 1.4)
				* Math.pow(KDR_TO_FKDR * ProfileMetrics.kdr(stats), 0.35)
				* Math.pow(WLR_TO_FKDR * stats.getWlr(), 0.25);
		return stats.getStars() * ratios * perGame(ProfileMetrics.finalsPerGame(stats), TYPICAL_FINALS_PER_GAME, stats)
				* form(stats);
	}

	/** The bed index, or {@code -1} for somebody there are no numbers for. */
	public static double bedIndex(PlayerStats stats) {
		if (stats == null || stats.isNicked()) {
			return -1.0;
		}

		double ratios = Math.pow(BBLR_TO_FKDR * ProfileMetrics.bedRatio(stats), 1.4)
				* Math.pow(WLR_TO_FKDR * stats.getWlr(), 0.6);
		return stats.getStars() * ratios * perGame(ProfileMetrics.bedsPerGame(stats), TYPICAL_BEDS_PER_GAME, stats)
				* form(stats);
	}

	/** The index the focus rates by; for both, the greater of the two. */
	public static double index(PlayerStats stats, ThreatFocus focus) {
		ThreatFocus resolved = focus == null ? ThreatFocus.BOTH : focus;
		switch (resolved) {
			case COMBAT:
				return combatIndex(stats);
			case BEDS:
				return bedIndex(stats);
			case BOTH:
				return Math.max(combatIndex(stats), bedIndex(stats));
			default:
				throw new IllegalStateException("Unexpected focus: " + resolved);
		}
	}

	/** A per-game figure against the typical one, softened and held within bounds; 1 without games. */
	private static double perGame(double value, double typical, PlayerStats stats) {
		if (ProfileMetrics.gamesPlayed(stats) == 0) {
			return 1.0;
		}

		double factor = Math.pow(value / typical, 0.25);
		return Math.max(1.0 / MAX_PER_GAME_FACTOR, Math.min(MAX_PER_GAME_FACTOR, factor));
	}

	/** One percent more per win in a row, up to double; a hidden streak counts as none. */
	private static double form(PlayerStats stats) {
		Integer streak = stats.getWinstreak();
		return streak == null || streak <= 0 ? 1.0 : 1.0 + Math.min(streak, FULL_STREAK) / (double) FULL_STREAK;
	}

	/** The level on the fixed bands, in a fight. */
	public static Threat of(PlayerStats stats) {
		return of(stats, ThreatFocus.COMBAT);
	}

	/** The level on the fixed bands for the focus. */
	public static Threat of(PlayerStats stats, ThreatFocus focus) {
		if (stats == null) {
			return UNKNOWN;
		}
		if (stats.isNicked()) {
			return NICKED;
		}

		return ofIndex(index(stats, focus));
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
