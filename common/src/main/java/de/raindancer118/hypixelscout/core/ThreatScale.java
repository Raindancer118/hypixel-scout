package de.raindancer118.hypixelscout.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * What a threat level is measured against, and which kind of danger it is about.
 *
 * <p>A 3 000-index player is a nightmare for somebody who just started and an ordinary opponent for
 * a veteran. So the level can be relative: the enemy's index against the player's own, or against
 * the player and their team together — a strong teammate raises the bar, a weak one lowers it. Fights
 * and beds each have their own reference: a rusher is measured against how the player does at beds,
 * not at fights.
 *
 * <p>Relative bands: under a fifth of the reference is NONE, under half LOW, up to one and a half
 * times MEDIUM (an even match), up to two and a half HIGH, up to four VERY HIGH, up to ten times
 * EXTREME, beyond that INSANE — see {@link Threat}.
 *
 * <p>The sensitivity multiplies whatever is compared with the bands: at 2 an enemy is rated as if
 * they were twice as strong, at 0.5 as if half. It is the player's to set, for those who want to be
 * warned early or only about the worst.
 *
 * @param combatReference the combat index enemies are measured against, or {@code -1} for the fixed bands
 * @param bedReference    the bed index enemies are measured against, or {@code -1} for the fixed bands
 * @param sensitivity     how much more dangerous than their numbers every enemy is taken to be
 * @param focus           which danger the level is about
 */
public final class ThreatScale {
	/** What the comparison is made with. */
	public enum Basis {
		/** The fixed community bands, the same for everybody. */
		ABSOLUTE,
		/** The player's own stats. */
		ME,
		/** The player together with their teammates. */
		TEAM
	}

	/**
	 * One enemy rated both ways.
	 *
	 * @param combat  the level in a fight
	 * @param beds    the level at the beds
	 * @param overall the level the focus reports
	 */
	public static final class Rating {
		private final Threat combat;
		private final Threat beds;
		private final Threat overall;

		public Rating(Threat combat, Threat beds, Threat overall) {
			this.combat = combat;
			this.beds = beds;
			this.overall = overall;
		}

		public Threat combat() {
			return combat;
		}

		public Threat beds() {
			return beds;
		}

		public Threat overall() {
			return overall;
		}

		/** Whether the beds are what makes this player dangerous, rather than their fights. */
		public boolean worseAtBeds() {
			return beds.isRated() && beds.compareTo(combat) > 0;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Rating)) return false;
			Rating other = (Rating) obj;
			return combat == other.combat && beds == other.beds && overall == other.overall;
		}

		@Override
		public int hashCode() {
			return Objects.hash(combat, beds, overall);
		}

		@Override
		public String toString() {
			return "Rating[combat=" + combat + ", beds=" + beds + ", overall=" + overall + "]";
		}
	}

	/** The fixed bands, and what every relative scale falls back to when nothing is known. */
	public static final ThreatScale ABSOLUTE = new ThreatScale(-1, -1, 1.0, ThreatFocus.BOTH);

	/**
	 * The smallest reference used: a fresh account has an index near zero, and dividing by that
	 * would make every opponent EXTREME. This is roughly a player with a few games behind them.
	 */
	public static final double MIN_REFERENCE = 100.0;

	private final double combatReference;
	private final double bedReference;
	private final double sensitivity;
	private final ThreatFocus focus;

	public ThreatScale(double combatReference, double bedReference, double sensitivity, ThreatFocus focus) {
		this.combatReference = combatReference;
		this.bedReference = bedReference;
		this.sensitivity = sensitivity;
		this.focus = focus == null ? ThreatFocus.BOTH : focus;
	}

	public double combatReference() {
		return combatReference;
	}

	public double bedReference() {
		return bedReference;
	}

	public double sensitivity() {
		return sensitivity;
	}

	public ThreatFocus focus() {
		return focus;
	}

	/**
	 * @param self      the player's own stats, or {@code null} while they are not known
	 * @param teammates the other members of the player's team; unknown and nicked ones are skipped
	 */
	public static ThreatScale of(Basis basis, PlayerStats self, Collection<PlayerStats> teammates) {
		if (basis == Basis.ABSOLUTE || basis == null) {
			return ABSOLUTE;
		}

		Collection<PlayerStats> mates = basis == Basis.TEAM ? teammates : Collections.<PlayerStats>emptyList();
		double combat = reference(self, mates, Threat::combatIndex);
		double beds = reference(self, mates, Threat::bedIndex);
		return combat < 0 ? ABSOLUTE : new ThreatScale(combat, beds, 1.0, ThreatFocus.BOTH);
	}

	/** The player's own index, the geometric mean with their team's average, or -1 with neither. */
	private static double reference(PlayerStats self, Collection<PlayerStats> teammates,
			ToDoubleFunction<PlayerStats> index) {
		double own = known(self) ? index.applyAsDouble(self) : -1;

		double sum = 0;
		int count = 0;
		for (PlayerStats mate : teammates) {
			if (known(mate)) {
				sum += index.applyAsDouble(mate);
				count++;
			}
		}

		if (count == 0) {
			return own < 0 ? -1 : Math.max(MIN_REFERENCE, own);
		}

		double team = sum / count;
		// The geometric mean: both count, and neither a smurf nor a carry drowns the other out.
		double reference = own < 0 ? team : Math.sqrt(Math.max(own, MIN_REFERENCE) * Math.max(team, MIN_REFERENCE));
		return Math.max(MIN_REFERENCE, reference);
	}

	/** The same scale at another sensitivity; anything that is not a positive number means 1. */
	public ThreatScale withSensitivity(double sensitivity) {
		return new ThreatScale(combatReference, bedReference,
				Double.isFinite(sensitivity) && sensitivity > 0 ? sensitivity : 1.0, focus);
	}

	/** The same scale about another danger; {@code null} means both. */
	public ThreatScale withFocus(ThreatFocus focus) {
		return new ThreatScale(combatReference, bedReference, sensitivity, focus);
	}

	private static boolean known(PlayerStats stats) {
		return stats != null && !stats.isNicked();
	}

	public boolean isRelative() {
		return combatReference > 0;
	}

	/** The level the focus reports: for both, the worse of the two. */
	public Threat threatOf(PlayerStats stats) {
		return rate(stats).overall();
	}

	/** Fights and beds, each on its own; unknown and nicked players are that in all three. */
	public Rating rate(PlayerStats stats) {
		if (!known(stats)) {
			Threat unrated = Threat.of(stats);
			return new Rating(unrated, unrated, unrated);
		}

		Threat combat = level(combatRatio(stats));
		Threat beds = level(bedRatio(stats));
		Threat overall;
		switch (focus) {
			case COMBAT:
				overall = combat;
				break;
			case BEDS:
				overall = beds;
				break;
			case BOTH:
				overall = combat.compareTo(beds) >= 0 ? combat : beds;
				break;
			default:
				throw new IllegalStateException("Unexpected focus: " + focus);
		}
		return new Rating(combat, beds, overall);
	}

	/**
	 * A number to sort enemies by that agrees with {@link #threatOf}: the rated index against its
	 * reference (or the bare index on the fixed bands), {@code -1} for somebody without numbers.
	 */
	public double score(PlayerStats stats) {
		if (!known(stats)) {
			return -1.0;
		}

		switch (focus) {
			case COMBAT:
				return combatRatio(stats);
			case BEDS:
				return bedRatio(stats);
			case BOTH:
				return Math.max(combatRatio(stats), bedRatio(stats));
			default:
				throw new IllegalStateException("Unexpected focus: " + focus);
		}
	}

	private double combatRatio(PlayerStats stats) {
		double index = Threat.combatIndex(stats) * sensitivity;
		return isRelative() ? index / combatReference : index;
	}

	private double bedRatio(PlayerStats stats) {
		double index = Threat.bedIndex(stats) * sensitivity;
		return isRelative() ? index / Math.max(MIN_REFERENCE, bedReference) : index;
	}

	private Threat level(double value) {
		return isRelative() ? Threat.ofRatio(value) : Threat.ofIndex(value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ThreatScale)) return false;
		ThreatScale other = (ThreatScale) obj;
		return Double.doubleToLongBits(combatReference) == Double.doubleToLongBits(other.combatReference)
				&& Double.doubleToLongBits(bedReference) == Double.doubleToLongBits(other.bedReference)
				&& Double.doubleToLongBits(sensitivity) == Double.doubleToLongBits(other.sensitivity)
				&& focus == other.focus;
	}

	@Override
	public int hashCode() {
		return Objects.hash(combatReference, bedReference, sensitivity, focus);
	}

	@Override
	public String toString() {
		return "ThreatScale[combatReference=" + combatReference + ", bedReference=" + bedReference
				+ ", sensitivity=" + sensitivity + ", focus=" + focus + "]";
	}
}
