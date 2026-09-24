package de.raindancer118.hypixelscout.core;

import java.util.Locale;
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
	public record Target(String name, String team, PlayerStats stats, double distance) {
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
	public record Result(String text, Problem problem) {
		public boolean ok() {
			return problem == Problem.NONE;
		}
	}

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");
	private static final java.util.Set<String> KNOWN = java.util.Set.of(
			"team", "name", "stars", "threat", "fkdr", "wlr", "bblr", "ws", "distance");

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
		String line = template == null ? "" : template.replaceAll("[\\r\\n\\t]+", " ").strip();
		if (line.isEmpty()) {
			return new Result("", Problem.EMPTY);
		}
		if (needsTarget(line) && target == null) {
			return new Result("", Problem.NO_TARGET);
		}

		StringBuilder out = new StringBuilder();
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

		return switch (key) {
			case "team" -> target.team() == null || target.team().isBlank() ? null
					: target.team().toUpperCase(Locale.ROOT);
			case "name" -> target.name();
			case "distance" -> String.valueOf(Math.round(target.distance()));
			case "threat" -> stats == null ? "?" : StatLines.plainThreat(scale, stats);
			case "stars" -> known ? String.valueOf(stats.getStars()) : "?";
			case "fkdr" -> known ? StatLines.oneDecimal(stats.getFkdr()) : "?";
			case "wlr" -> known ? StatLines.oneDecimal(stats.getWlr()) : "?";
			case "bblr" -> known ? StatLines.oneDecimal(ProfileMetrics.bedRatio(stats)) : "?";
			case "ws" -> known && stats.getWinstreak() != null ? String.valueOf(stats.getWinstreak()) : "?";
			default -> throw new IllegalArgumentException(key);
		};
	}

	private static String fit(String line) {
		return line.length() <= StatLines.MAX_CHAT ? line : line.substring(0, StatLines.MAX_CHAT).strip();
	}
}
