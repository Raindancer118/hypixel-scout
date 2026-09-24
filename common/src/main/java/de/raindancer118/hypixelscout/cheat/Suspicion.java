package de.raindancer118.hypixelscout.cheat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Adds sightings up into flags.
 *
 * <p>Every sighting adds its check's weight to that player's score for the check; the score fades by
 * one point every {@link #TICKS_PER_POINT} ticks. At {@link #FLAG_AT} — divided by the sensitivity —
 * the player is flagged for the check, and stays flagged for the rest of the round: fading only
 * keeps rare lag-born sightings from ever adding up, it does not take a flag back.
 *
 * <p>Confidence: each sighting is taken as independent evidence with its check's
 * {@link Check#sureness()}, so {@code n} of them make {@code 1 − (1 − p)ⁿ}. {@code n} is the most
 * sightings the score ever held at once (after fading), not all of them: twenty lag spikes spread
 * over a long game stay one or two, twenty in half a minute count as twenty. A player's confidence
 * combines their checks the same way.
 */
public final class Suspicion {
	public static final double FLAG_AT = 10.0;
	/** Twenty seconds for a point to fade. */
	public static final long TICKS_PER_POINT = 400;
	/** The share of a sighting one legit observation takes back. */
	public static final double RELIEF = 0.4;

	/**
	 * A flagged check: how often it was seen, the latest evidence, and how sure this check alone
	 * makes the mod (0 to 1).
	 */
	public static final class Flag {
		private final String player;
		private final Check check;
		private final int count;
		private final String detail;
		private final double confidence;

		public Flag(String player, Check check, int count, String detail, double confidence) {
			this.player = player;
			this.check = check;
			this.count = count;
			this.detail = detail;
			this.confidence = confidence;
		}

		public String player() {
			return player;
		}

		public Check check() {
			return check;
		}

		public int count() {
			return count;
		}

		public String detail() {
			return detail;
		}

		public double confidence() {
			return confidence;
		}

		public int percent() {
			return (int) Math.round(confidence * 100);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Flag)) return false;
			Flag other = (Flag) obj;
			return count == other.count && check == other.check
					&& Double.doubleToLongBits(confidence) == Double.doubleToLongBits(other.confidence)
					&& Objects.equals(player, other.player) && Objects.equals(detail, other.detail);
		}

		@Override
		public int hashCode() {
			return Objects.hash(player, check, count, detail, confidence);
		}

		@Override
		public String toString() {
			return "Flag[player=" + player + ", check=" + check + ", count=" + count + ", detail=" + detail
					+ ", confidence=" + confidence + "]";
		}
	}

	private static final class Score {
		double points;
		long tick;
		int count;
		String detail = "";
		boolean flagged;
		/** The most sightings the faded score ever held at once. */
		double peak;
		/** When the check last saw something — reliefs do not count. */
		long lastSeen;

		double confidence(Check check) {
			return 1 - Math.pow(1 - check.sureness(), peak);
		}
	}

	/** One check on one player, for the screens: how often, how sure, flagged or not, the latest evidence. */
	public static final class Seen {
		private final Check check;
		private final int count;
		private final double confidence;
		private final boolean flagged;
		private final String detail;
		private final long tick;

		public Seen(Check check, int count, double confidence, boolean flagged, String detail, long tick) {
			this.check = check;
			this.count = count;
			this.confidence = confidence;
			this.flagged = flagged;
			this.detail = detail;
			this.tick = tick;
		}

		public Check check() {
			return check;
		}

		public int count() {
			return count;
		}

		public double confidence() {
			return confidence;
		}

		public boolean flagged() {
			return flagged;
		}

		public String detail() {
			return detail;
		}

		public long tick() {
			return tick;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Seen)) return false;
			Seen other = (Seen) obj;
			return count == other.count && flagged == other.flagged && tick == other.tick && check == other.check
					&& Double.doubleToLongBits(confidence) == Double.doubleToLongBits(other.confidence)
					&& Objects.equals(detail, other.detail);
		}

		@Override
		public int hashCode() {
			return Objects.hash(check, count, confidence, flagged, detail, tick);
		}

		@Override
		public String toString() {
			return "Seen[check=" + check + ", count=" + count + ", confidence=" + confidence + ", flagged="
					+ flagged + ", detail=" + detail + ", tick=" + tick + "]";
		}
	}

	/**
	 * Everything seen on one player this round.
	 *
	 * @param lastTick when any of their checks last saw something
	 * @param checks   the checks that did, the surest first
	 */
	public static final class Suspect {
		private final String player;
		private final double confidence;
		private final boolean flagged;
		private final long lastTick;
		private final List<Seen> checks;

		public Suspect(String player, double confidence, boolean flagged, long lastTick, List<Seen> checks) {
			this.player = player;
			this.confidence = confidence;
			this.flagged = flagged;
			this.lastTick = lastTick;
			this.checks = checks;
		}

		public String player() {
			return player;
		}

		public double confidence() {
			return confidence;
		}

		public boolean flagged() {
			return flagged;
		}

		public long lastTick() {
			return lastTick;
		}

		public List<Seen> checks() {
			return checks;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Suspect)) return false;
			Suspect other = (Suspect) obj;
			return flagged == other.flagged && lastTick == other.lastTick
					&& Double.doubleToLongBits(confidence) == Double.doubleToLongBits(other.confidence)
					&& Objects.equals(player, other.player) && Objects.equals(checks, other.checks);
		}

		@Override
		public int hashCode() {
			return Objects.hash(player, confidence, flagged, lastTick, checks);
		}

		@Override
		public String toString() {
			return "Suspect[player=" + player + ", confidence=" + confidence + ", flagged=" + flagged
					+ ", lastTick=" + lastTick + ", checks=" + checks + "]";
		}
	}

	private final Map<String, Map<Check, Score>> scores = new LinkedHashMap<>();

	/**
	 * Counts one sighting.
	 *
	 * @param sensitivity {@code 1} as designed, higher flags sooner
	 * @return the flag, the one time it is newly raised
	 */
	public synchronized Optional<Flag> record(Violation violation, double sensitivity) {
		if (violation.relief()) {
			relieve(violation);
			return Optional.empty();
		}
		Score score = scores.computeIfAbsent(violation.player(), name -> new LinkedHashMap<>())
				.computeIfAbsent(violation.check(), check -> new Score());

		long elapsed = score.count == 0 ? 0 : Math.max(0, violation.tick() - score.tick);
		score.points = Math.max(0, score.points - (double) elapsed / TICKS_PER_POINT) + violation.check().weight();
		score.tick = violation.tick();
		score.count++;
		score.detail = violation.detail();
		score.lastSeen = violation.tick();
		score.peak = Math.max(score.peak, score.points / violation.check().weight());

		if (!score.flagged && score.points * sensitivity >= FLAG_AT - 1e-9) {
			score.flagged = true;
			return Optional.of(flag(violation.player(), violation.check(), score));
		}
		return Optional.empty();
	}

	/**
	 * The legit thing seen where the check looks: takes {@link #RELIEF} of a sighting back, from the
	 * score and from the confidence. Never below nothing; a flag already raised stays.
	 */
	private void relieve(Violation relief) {
		Score score = scores.getOrDefault(relief.player(), Collections.emptyMap()).get(relief.check());
		if (score == null) {
			return;
		}
		double weight = relief.check().weight();
		long elapsed = Math.max(0, relief.tick() - score.tick);
		score.points = Math.max(0, score.points - (double) elapsed / TICKS_PER_POINT - RELIEF * weight);
		score.tick = Math.max(score.tick, relief.tick());
		score.peak = Math.max(score.points / weight, score.peak - RELIEF);
	}

	/** This player's flags, the surest first. */
	public synchronized List<Flag> flags(String player) {
		List<Flag> flags = new ArrayList<>();
		scores.getOrDefault(player, Collections.emptyMap()).forEach((check, score) -> {
			if (score.flagged) {
				flags.add(flag(player, check, score));
			}
		});
		flags.sort(Comparator.comparingDouble(Flag::confidence).reversed().thenComparing(Comparator.comparingInt(Flag::count).reversed()));
		return flags;
	}

	/** Every flag of the round. */
	public synchronized List<Flag> flagged() {
		List<Flag> all = new ArrayList<>();
		for (String player : scores.keySet()) {
			all.addAll(flags(player));
		}
		return all;
	}

	/**
	 * How sure the mod is that this player cheats, from every sighting of every check, flagged or
	 * not: 0 for nothing seen, never quite 1.
	 */
	public synchronized double confidence(String player) {
		double innocent = 1;
		for (Map.Entry<Check, Score> entry : scores.getOrDefault(player, Collections.emptyMap()).entrySet()) {
			innocent *= 1 - entry.getValue().confidence(entry.getKey());
		}
		return 1 - innocent;
	}

	/** Everybody seen doing anything suspicious this round, flagged or not, the surest first. */
	public synchronized List<Suspect> suspects() {
		List<Suspect> suspects = new ArrayList<>();
		scores.forEach((player, byCheck) -> {
			List<Seen> seen = new ArrayList<>();
			long last = 0;
			boolean flagged = false;
			for (Map.Entry<Check, Score> entry : byCheck.entrySet()) {
				Score score = entry.getValue();
				if (score.count == 0) {
					continue;
				}
				seen.add(new Seen(entry.getKey(), score.count, score.confidence(entry.getKey()), score.flagged,
						score.detail, score.lastSeen));
				last = Math.max(last, score.lastSeen);
				flagged |= score.flagged;
			}
			if (!seen.isEmpty()) {
				seen.sort(Comparator.comparingDouble(Seen::confidence).reversed().thenComparing(Seen::check));
				suspects.add(new Suspect(player, confidence(player), flagged, last, Collections.unmodifiableList(new ArrayList<>(seen))));
			}
		});
		suspects.sort(Comparator.comparingDouble(Suspect::confidence).reversed().thenComparing(Suspect::player));
		return suspects;
	}

	/** How often a check was seen on a player, flagged or not. */
	public synchronized int count(String player, Check check) {
		Score score = scores.getOrDefault(player, Collections.emptyMap()).get(check);
		return score == null ? 0 : score.count;
	}

	/** A flag the player says was wrong: this check of this player, or all of theirs for {@code null}. */
	public synchronized void forget(String player, Check check) {
		if (check == null) {
			scores.remove(player);
		} else {
			scores.getOrDefault(player, new LinkedHashMap<>()).remove(check);
		}
	}

	/** A check switched off: its sightings and flags go, for everybody. */
	public synchronized void forget(Check check) {
		for (Map<Check, Score> byCheck : scores.values()) {
			byCheck.remove(check);
		}
	}

	public synchronized void clear() {
		scores.clear();
	}

	private static Flag flag(String player, Check check, Score score) {
		return new Flag(player, check, score.count, score.detail, score.confidence(check));
	}
}
