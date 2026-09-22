package de.raindancer118.hypixelscout.core;

import java.util.Collection;

/**
 * What a threat level is measured against.
 *
 * <p>A 3 000-index player is a nightmare for somebody who just started and an ordinary opponent for
 * a veteran. So the level can be relative: the enemy's index against the player's own, or against
 * the player and their team together — a strong teammate raises the bar, a weak one lowers it.
 *
 * <p>Relative bands: under half the reference is LOW, up to one and a half times is MEDIUM (an
 * even match), up to four times HIGH, beyond that EXTREME.
 */
public record ThreatScale(double reference) {
	/** What the comparison is made with. */
	public enum Basis {
		/** The fixed community bands, the same for everybody. */
		ABSOLUTE,
		/** The player's own stars and FKDR. */
		ME,
		/** The player together with their teammates. */
		TEAM
	}

	/** The fixed bands, and what every relative scale falls back to when nothing is known. */
	public static final ThreatScale ABSOLUTE = new ThreatScale(-1);

	/**
	 * The smallest reference used: a fresh account has an index near zero, and dividing by that
	 * would make every opponent EXTREME. This is roughly a player with a few games behind them.
	 */
	public static final double MIN_REFERENCE = 100.0;

	/**
	 * @param self      the player's own stats, or {@code null} while they are not known
	 * @param teammates the other members of the player's team; unknown and nicked ones are skipped
	 */
	public static ThreatScale of(Basis basis, PlayerStats self, Collection<PlayerStats> teammates) {
		double own = known(self) ? Threat.index(self) : -1;

		if (basis == Basis.ME) {
			return own < 0 ? ABSOLUTE : new ThreatScale(Math.max(MIN_REFERENCE, own));
		}

		if (basis == Basis.TEAM) {
			double sum = 0;
			int count = 0;
			for (PlayerStats mate : teammates) {
				if (known(mate)) {
					sum += Threat.index(mate);
					count++;
				}
			}

			if (count == 0) {
				return own < 0 ? ABSOLUTE : new ThreatScale(Math.max(MIN_REFERENCE, own));
			}

			double team = sum / count;
			// The geometric mean: both count, and neither a smurf nor a carry drowns the other out.
			double reference = own < 0 ? team : Math.sqrt(Math.max(own, MIN_REFERENCE) * Math.max(team, MIN_REFERENCE));
			return new ThreatScale(Math.max(MIN_REFERENCE, reference));
		}

		return ABSOLUTE;
	}

	private static boolean known(PlayerStats stats) {
		return stats != null && !stats.isNicked();
	}

	public boolean isRelative() {
		return reference > 0;
	}

	public Threat threatOf(PlayerStats stats) {
		if (!isRelative() || stats == null || stats.isNicked()) {
			return Threat.of(stats);
		}

		double ratio = Threat.index(stats) / reference;
		if (ratio < 0.5) {
			return Threat.LOW;
		}
		if (ratio < 1.5) {
			return Threat.MEDIUM;
		}
		if (ratio < 4.0) {
			return Threat.HIGH;
		}

		return Threat.EXTREME;
	}
}
