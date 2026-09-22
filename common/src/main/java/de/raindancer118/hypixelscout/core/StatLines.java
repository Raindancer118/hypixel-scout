package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one place that decides what a player's stats say as text.
 *
 * <p>The look tooltip, the chat hover and the nametag show the same numbers, so they all come from
 * here: a change to the wording happens once rather than in several renderers that slowly drift
 * apart. Section-sign strings, like {@link StatFormat}.
 */
public final class StatLines {
	private StatLines() {
	}

	/** Star, rank and name, the way Hypixel itself prints a player in Bedwars. */
	public static String name(String name, PlayerStats stats) {
		if (stats == null) {
			return "§7" + name;
		}
		if (stats.isNicked()) {
			return "§d" + name;
		}

		String tag = Ranks.tag(stats.getRank());
		return StatFormat.star(stats.getStars()) + " " + (tag.isEmpty() ? "" : tag + " ")
				+ Ranks.colour(stats.getRank()) + name;
	}

	/** The block shown when looking at a player, or hovering their name in chat. */
	public static List<String> detail(String name, PlayerStats stats, boolean pending,
			String failure) {
		List<String> lines = new ArrayList<>();

		if (stats == null) {
			lines.add("§f" + name);
			lines.add(pending || failure == null ? "§7Looking them up…" : "§c" + failure);
			return lines;
		}

		if (stats.isNicked()) {
			lines.add("§f" + name);
			lines.add("§dNicked");
			lines.add("§7Hypixel does not know this name, so there is nothing to look up.");
			return lines;
		}

		Threat threat = Threat.of(stats);
		lines.add(name(name, stats));
		lines.add("§7FKDR " + StatFormat.ratioColour(stats.getFkdr())
				+ StatFormat.ratio(stats.getFkdr()) + "  §7WLR "
				+ StatFormat.ratioColour(stats.getWlr()) + StatFormat.ratio(stats.getWlr())
				+ "  §7Threat " + threat.colour() + threat.label());
		lines.add("§7Finals §f" + StatFormat.count(stats.getFinalKills()) + "§8/§f"
				+ StatFormat.count(stats.getFinalDeaths()) + "  §7Wins §f"
				+ StatFormat.count(stats.getWins()) + "§8/§f" + StatFormat.count(stats.getLosses()));
		lines.add("§7Beds §f" + StatFormat.count(stats.getBedsBroken()) + "  §7Streak §f"
				+ StatFormat.winstreak(stats.getWinstreak()) + "  §7Account §f"
				+ StatFormat.age(stats.getFirstLogin(), System.currentTimeMillis()));

		lines.add("§7Beds/game §f" + StatFormat.ratio(ProfileMetrics.bedsPerGame(stats))
				+ "  §7Kills/game §f" + StatFormat.ratio(ProfileMetrics.killsPerGame(stats)));

		Map<String, String> socials = stats.getSocials();
		if (!socials.isEmpty()) {
			StringBuilder line = new StringBuilder("§8");
			for (String service : socials.keySet()) {
				line.append(service.toLowerCase(Locale.ROOT)).append(' ');
			}
			lines.add(line.toString().trim());
		}

		return lines;
	}

	/**
	 * One player as a line for team or party chat: name, rank, star, threat, then the figures that
	 * decide a fight. Plain ASCII — Hypixel's chat drops some symbols — and at most 100 characters.
	 * A winstreak the player hides is left out rather than sent as zero.
	 */
	public static String chatLine(String name, PlayerStats stats) {
		if (stats.isNicked()) {
			return name + " is nicked - Hypixel has no profile under that name";
		}

		String rank = plain(Ranks.tag(stats.getRank()));
		StringBuilder line = new StringBuilder(name);
		if (!rank.isEmpty()) {
			line.append(' ').append(rank);
		}
		line.append(' ').append(stats.getStars()).append("* ").append(Threat.of(stats).label());
		line.append(" | FKDR ").append(StatFormat.ratio(stats.getFkdr()));
		line.append(" | WLR ").append(StatFormat.ratio(stats.getWlr()));
		if (stats.getWinstreak() != null) {
			line.append(" | WS ").append(stats.getWinstreak());
		}
		line.append(" | Beds/g ").append(oneDecimal(ProfileMetrics.bedsPerGame(stats)));
		line.append(" | Kills/g ").append(oneDecimal(ProfileMetrics.killsPerGame(stats)));

		return line.length() <= 100 ? line.toString() : line.substring(0, 100);
	}

	private static String oneDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** A formatted line with its colour codes taken out, for measuring and for narration. */
	public static String plain(String formatted) {
		return formatted == null ? "" : formatted.replaceAll("§.", "");
	}
}
