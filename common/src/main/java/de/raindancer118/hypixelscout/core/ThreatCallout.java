package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The enemies worth a warning, one short chat line each, most dangerous first:
 * {@code YELLOW Sundial 1502* - EXTREME - 13.8 FKDR - 104 WS}.
 *
 * <p>Players below the chosen level are left out — a report that names everybody is read by
 * nobody — unless they are on a winstreak worth a warning. Nicks are named. The report is capped, because every
 * line is a chat message and players without a rank may send one every few seconds only.
 */
public final class ThreatCallout {
	private ThreatCallout() {
	}

	private record Entry(String team, PlayerStats stats, double danger) {
	}

	/**
	 * @param enemies  each enemy team's players by team name; {@code null} for somebody not looked up yet
	 * @param from     the lowest level worth naming
	 * @param maxLines the most player lines to send; a last line then says how many more there were
	 */
	public static List<String> lines(Map<String, List<PlayerStats>> enemies, int streakThreshold,
			ThreatScale scale, Threat from, int maxLines) {
		List<Entry> called = new ArrayList<>();
		int below = 0;
		int unknown = 0;

		for (Map.Entry<String, List<PlayerStats>> team : enemies.entrySet()) {
			for (PlayerStats player : team.getValue()) {
				if (player == null) {
					unknown++;
				} else if (player.isNicked()) {
					called.add(new Entry(team.getKey(), player, -1));
				} else if (scale.threatOf(player).compareTo(from) >= 0 || onARun(player, streakThreshold)) {
					called.add(new Entry(team.getKey(), player, Threat.index(player)));
				} else {
					below++;
				}
			}
		}

		if (called.isEmpty()) {
			return List.of("No dangerous enemies (" + below + " below " + from.label()
					+ (unknown > 0 ? ", " + unknown + " not looked up yet" : "") + ")");
		}

		// Most dangerous first; nicks, which nobody can rate, after everybody who can be.
		called.sort((left, right) -> Double.compare(right.danger(), left.danger()));

		List<String> lines = new ArrayList<>();
		for (Entry entry : called.subList(0, Math.min(maxLines, called.size()))) {
			lines.add(describe(entry, streakThreshold, scale));
		}

		if (called.size() > maxLines) {
			lines.add("+" + (called.size() - maxLines) + " more, see the Scout screen");
		}

		return lines;
	}

	private static boolean onARun(PlayerStats player, int threshold) {
		return player.getWinstreak() != null && player.getWinstreak() > threshold;
	}

	private static String describe(Entry entry, int streakThreshold, ThreatScale scale) {
		String team = entry.team().toUpperCase(Locale.ROOT);
		PlayerStats player = entry.stats();

		if (player.isNicked()) {
			return team + " " + player.getName() + " is nicked";
		}

		String line = team + " " + player.getName() + " " + player.getStars() + "* - "
				+ scale.threatOf(player).label() + " - " + StatLines.oneDecimal(player.getFkdr()) + " FKDR";
		return onARun(player, streakThreshold) ? line + " - " + player.getWinstreak() + " WS" : line;
	}
}
