package de.raindancer118.hypixelscout.core;

import java.util.Comparator;

/**
 * How players are ranked against each other.
 *
 * <p>Shared by the table and the lobby screen so the same lobby reads the same way in both, and
 * kept here so the tie-breaking — what to do with a player nobody has an answer for yet — is
 * decided once and tested.
 */
public final class StatsSorting {
	/** Case-insensitive, because a lobby is not sorted by who happened to capitalise their name. */
	public static final Comparator<String> BY_NAME = new Comparator<String>() {
		@Override
		public int compare(String left, String right) {
			return left.compareToIgnoreCase(right);
		}
	};

	private StatsSorting() {
	}

	/**
	 * The number a player is ranked by, highest first. Unknown and nicked players answer -1: they
	 * sit at the bottom and stay there rather than jumping up the list mid-read.
	 */
	public static double value(PlayerStats stats, SortMode mode) {
		if (stats == null || stats.isNicked()) {
			return -1.0;
		}

		if (mode == SortMode.FKDR) {
			return stats.getFkdr();
		}
		if (mode == SortMode.WLR) {
			return stats.getWlr();
		}

		return stats.getStars();
	}
}
