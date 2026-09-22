package de.raindancer118.hypixelscout.core;

/**
 * The figures the profile card shows that Hypixel does not store: rates, per-game averages and
 * per-star averages.
 *
 * <p>Every one of them divides by something a fresh account has none of, so every one of them
 * answers zero rather than infinity or NaN for an empty profile.
 */
public final class ProfileMetrics {
	private ProfileMetrics() {
	}

	public static int gamesPlayed(PlayerStats stats) {
		return stats.getWins() + stats.getLosses();
	}

	/** As a percentage, because that is how the card reads it out. */
	public static double winRate(PlayerStats stats) {
		int games = gamesPlayed(stats);
		return games == 0 ? 0.0 : 100.0 * stats.getWins() / games;
	}

	public static double finalsPerGame(PlayerStats stats) {
		int games = gamesPlayed(stats);
		return games == 0 ? 0.0 : (double) stats.getFinalKills() / games;
	}

	public static double killsPerGame(PlayerStats stats) {
		int games = gamesPlayed(stats);
		return games == 0 ? 0.0 : (double) stats.getKills() / games;
	}

	/** What a star is worth for this player — the tell for a boosted or bought account. */
	public static double finalsPerStar(PlayerStats stats) {
		return stats.getStars() == 0 ? 0.0 : (double) stats.getFinalKills() / stats.getStars();
	}

	public static double killsPerStar(PlayerStats stats) {
		return stats.getStars() == 0 ? 0.0 : (double) stats.getKills() / stats.getStars();
	}

	public static double kdr(PlayerStats stats) {
		return stats.getDeaths() == 0 ? stats.getKills()
				: (double) stats.getKills() / stats.getDeaths();
	}

	public static double bedRatio(PlayerStats stats) {
		return stats.getBedsLost() == 0 ? stats.getBedsBroken()
				: (double) stats.getBedsBroken() / stats.getBedsLost();
	}
}
