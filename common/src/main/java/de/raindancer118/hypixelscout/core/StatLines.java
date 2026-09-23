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
		return detail(name, stats, pending, failure, ThreatScale.ABSOLUTE);
	}

	/** The same, with the threat level measured on {@code scale}. */
	public static List<String> detail(String name, PlayerStats stats, boolean pending,
			String failure, ThreatScale scale) {
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

		Threat threat = scale.threatOf(stats);
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
	 * One player as a line for team or party chat, written to be read by a person:
	 * {@code Sundial [MVP+] 1502* - EXTREME threat - 13.8 FKDR, 5.3 WLR, 104 WS, 1.7 beds/game, ...}.
	 *
	 * <p>Plain ASCII — Hypixel's chat drops some symbols and takes no colour — and at most 100
	 * characters. A line that does not fit loses words before numbers: first the rank, then
	 * "threat", then the long unit names; it is never cut off in the middle.
	 */
	public static String chatLine(String name, PlayerStats stats) {
		return chatLine(name, stats, ThreatScale.ABSOLUTE);
	}

	/** The same, with the threat level measured on {@code scale}. */
	public static String chatLine(String name, PlayerStats stats, ThreatScale scale) {
		if (stats.isNicked()) {
			return name + " is nicked (no Hypixel profile under that name)";
		}

		String rank = plain(Ranks.tag(stats.getRank()));
		String threat = scale.threatOf(stats).label();
		String streak = stats.getWinstreak() == null ? "" : ", " + stats.getWinstreak() + " WS";
		String ratios = oneDecimal(stats.getFkdr()) + " FKDR, " + oneDecimal(stats.getWlr()) + " WLR" + streak;
		String beds = oneDecimal(ProfileMetrics.bedsPerGame(stats));
		String kills = oneDecimal(ProfileMetrics.killsPerGame(stats));
		String who = name + (rank.isEmpty() ? "" : " " + rank) + " " + stats.getStars() + "*";
		String bare = name + " " + stats.getStars() + "*";

		// Most readable first; the first that fits is sent.
		String[] candidates = {
				who + " - " + threat + " threat - " + ratios + ", " + beds + " beds/game, " + kills + " kills/game",
				bare + " - " + threat + " threat - " + ratios + ", " + beds + " beds/game, " + kills + " kills/game",
				bare + " - " + threat + " - " + ratios + ", " + beds + " beds/g, " + kills + " kills/g",
				bare + " " + threat + " " + ratios + ", " + beds + " beds/g, " + kills + " kills/g",
		};

		for (String candidate : candidates) {
			if (candidate.length() <= MAX_CHAT) {
				return candidate;
			}
		}

		String last = candidates[candidates.length - 1];
		return last.substring(0, MAX_CHAT).strip();
	}

	/** The longest line the mod puts into chat; the length every version of the game will send. */
	public static final int MAX_CHAT = 100;

	/** One decimal: a figure read at a glance in chat, where the second one is noise. */
	public static String oneDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** A formatted line with its colour codes taken out, for measuring and for narration. */
	public static String plain(String formatted) {
		return formatted == null ? "" : formatted.replaceAll("§.", "");
	}
}
