package de.raindancer118.hypixelscout.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A message on a hotkey, filled in with whoever the player is aiming at: {@code {team} inc} becomes
 * {@code RED inc}.
 *
 * <p>Placeholders, case does not matter: {@code {team}} (capitals, as the reports write it),
 * {@code {name}}, {@code {stars}}, {@code {threat}}, {@code {fkdr}}, {@code {wlr}}, {@code {bblr}},
 * {@code {ws}} and {@code {distance}} (blocks). Stats not known yet come out as {@code ?} rather than
 * as a made-up number; anything else in braces is left as typed.
 *
 * <p>A message that asks for somebody but has nobody to ask about is refused, not sent half empty —
 * "&nbsp;inc" in team chat helps nobody.
 */
public final class Callout {
	/**
	 * Whoever the message is about.
	 *
	 * @param team     the Bedwars team name ({@code Red}), empty when the scoreboard shows none
	 * @param stats    their stats, or {@code null} while not looked up
	 * @param distance blocks from the player
	 */
	public static final class Target {
		private final String name;
		private final String team;
		private final PlayerStats stats;
		private final double distance;

		public Target(String name, String team, PlayerStats stats, double distance) {
			this.name = name;
			this.team = team;
			this.stats = stats;
			this.distance = distance;
		}

		public String name() {
			return name;
		}

		public String team() {
			return team;
		}

		public PlayerStats stats() {
			return stats;
		}

		public double distance() {
			return distance;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Target)) return false;
			Target other = (Target) obj;
			return Double.doubleToLongBits(distance) == Double.doubleToLongBits(other.distance)
					&& Objects.equals(name, other.name) && Objects.equals(team, other.team)
					&& Objects.equals(stats, other.stats);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, team, stats, distance);
		}

		@Override
		public String toString() {
			return "Target[name=" + name + ", team=" + team + ", stats=" + stats + ", distance=" + distance + "]";
		}
	}

	public enum Problem {
		NONE,
		/** The message has placeholders and nobody is under the crosshair. */
		NO_TARGET,
		/** It asks for the team, and the scoreboard has none for this player. */
		NO_TEAM,
		/** Nothing to send. */
		EMPTY
	}

	/** The line to send, or why there is none. */
	public static final class Result {
		private final String text;
		private final Problem problem;

		public Result(String text, Problem problem) {
			this.text = text;
			this.problem = problem;
		}

		public String text() {
			return text;
		}

		public Problem problem() {
			return problem;
		}

		public boolean ok() {
			return problem == Problem.NONE;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Result)) return false;
			Result other = (Result) obj;
			return problem == other.problem && Objects.equals(text, other.text);
		}

		@Override
		public int hashCode() {
			return Objects.hash(text, problem);
		}

		@Override
		public String toString() {
			return "Result[text=" + text + ", problem=" + problem + "]";
		}
	}

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");
	private static final Set<String> KNOWN = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"team", "name", "stars", "threat", "fkdr", "wlr", "bblr", "ws", "distance")));

	private Callout() {
	}

	/** Whether the message says anything about a player, and so needs one aimed at. */
	public static boolean needsTarget(String template) {
		if (template == null) {
			return false;
		}
		Matcher matcher = PLACEHOLDER.matcher(template);
		while (matcher.find()) {
			if (KNOWN.contains(matcher.group(1).toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	public static Result render(String template, Target target, ThreatScale scale) {
		String line = template == null ? "" : template.replaceAll("[\\r\\n\\t]+", " ").trim();
		if (line.isEmpty()) {
			return new Result("", Problem.EMPTY);
		}
		if (needsTarget(line) && target == null) {
			return new Result("", Problem.NO_TARGET);
		}

		StringBuffer out = new StringBuffer();
		Matcher matcher = PLACEHOLDER.matcher(line);
		while (matcher.find()) {
			String key = matcher.group(1).toLowerCase(Locale.ROOT);
			String value = KNOWN.contains(key) ? value(key, target, scale) : matcher.group();
			if (value == null) {
				return new Result("", Problem.NO_TEAM);
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(out);

		return new Result(fit(out.toString()), Problem.NONE);
	}

	/** {@code null} only for a team there is none of. */
	private static String value(String key, Target target, ThreatScale scale) {
		PlayerStats stats = target.stats();
		boolean known = stats != null && !stats.isNicked();

		switch (key) {
			case "team":
				return target.team() == null || target.team().trim().isEmpty() ? null
						: target.team().toUpperCase(Locale.ROOT);
			case "name":
				return target.name();
			case "distance":
				return String.valueOf(Math.round(target.distance()));
			case "threat":
				return stats == null ? "?" : StatLines.plainThreat(scale, stats);
			case "stars":
				return known ? String.valueOf(stats.getStars()) : "?";
			case "fkdr":
				return known ? StatLines.oneDecimal(stats.getFkdr()) : "?";
			case "wlr":
				return known ? StatLines.oneDecimal(stats.getWlr()) : "?";
			case "bblr":
				return known ? StatLines.oneDecimal(ProfileMetrics.bedRatio(stats)) : "?";
			case "ws":
				return known && stats.getWinstreak() != null ? String.valueOf(stats.getWinstreak()) : "?";
			default:
				throw new IllegalArgumentException(key);
		}
	}

	private static String fit(String line) {
		return line.length() <= StatLines.MAX_CHAT ? line : line.substring(0, StatLines.MAX_CHAT).trim();
	}
}
