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

	/**
	 * Two lines for a glance: who, then threat, FKDR and a winstreak if there is one. For the popup
	 * when somebody comes close, which has to be read in a second.
	 */
	public static List<String> brief(String name, PlayerStats stats, boolean pending, String failure,
			ThreatScale scale) {
		if (stats == null) {
			return List.of("§f" + name, pending || failure == null ? "§7Looking them up…" : "§c" + failure);
		}
		if (stats.isNicked()) {
			return List.of("§f" + name, "§dNICK");
		}

		String line = threat(scale, stats) + "  " + (aboutBeds(scale, stats)
				? "§7BBLR " + StatFormat.ratioColour(ProfileMetrics.bedRatio(stats)) + StatFormat.ratio(ProfileMetrics.bedRatio(stats))
				: "§7FKDR " + StatFormat.ratioColour(stats.getFkdr()) + StatFormat.ratio(stats.getFkdr()));
		if (stats.getWinstreak() != null && stats.getWinstreak() > 0) {
			line += "  §7WS §f" + stats.getWinstreak();
		}
		return List.of(name(name, stats), line);
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

		ThreatScale.Rating rating = scale.rate(stats);
		lines.add(name(name, stats));
		String ratios = "§7FKDR " + StatFormat.ratioColour(stats.getFkdr())
				+ StatFormat.ratio(stats.getFkdr()) + "  §7WLR "
				+ StatFormat.ratioColour(stats.getWlr()) + StatFormat.ratio(stats.getWlr());
		if (scale.focus() == ThreatFocus.BOTH) {
			lines.add(ratios);
			lines.add("§7Fight " + rating.combat().colour() + rating.combat().label()
					+ "  §7Beds " + rating.beds().colour() + rating.beds().label());
		} else {
			Threat threat = rating.overall();
			lines.add(ratios + "  §7Threat " + threat.colour() + threat.label());
		}
		lines.add("§7Finals §f" + StatFormat.count(stats.getFinalKills()) + "§8/§f"
				+ StatFormat.count(stats.getFinalDeaths()) + "  §7Wins §f"
				+ StatFormat.count(stats.getWins()) + "§8/§f" + StatFormat.count(stats.getLosses()));
		lines.add("§7Beds §f" + StatFormat.count(stats.getBedsBroken()) + " §8(§7BBLR §f"
				+ StatFormat.ratio(ProfileMetrics.bedRatio(stats)) + "§8)  §7Streak §f"
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
	 * One player for team or party chat, in two short lines a person reads at a glance:
	 * {@code Sundial [MVP+] 1502* is EXTREME}, then
	 * {@code 13.8 FKDR, 5.3 WLR, 104 winstreak, 1.7 beds and 8.5 kills a game}.
	 *
	 * <p>Plain ASCII — Hypixel's chat drops some symbols and takes no colour. A nick is one line; a
	 * winstreak the player hides is left out rather than sent as zero.
	 */
	public static List<String> chatLines(String name, PlayerStats stats, ThreatScale scale) {
		if (stats.isNicked()) {
			return List.of(name + " is nicked (no Hypixel profile under that name)");
		}

		String rank = plain(Ranks.tag(stats.getRank()));
		String who = name + (rank.isEmpty() ? "" : " " + rank) + " " + stats.getStars() + "* is "
				+ plainThreat(scale, stats);

		String streak = stats.getWinstreak() == null ? "" : stats.getWinstreak() + " winstreak, ";
		String numbers = oneDecimal(stats.getFkdr()) + " FKDR, " + oneDecimal(stats.getWlr()) + " WLR, " + streak
				+ oneDecimal(ProfileMetrics.bedsPerGame(stats)) + " beds and "
				+ oneDecimal(ProfileMetrics.killsPerGame(stats)) + " kills a game";

		return List.of(fit(who), fit(numbers));
	}

	/**
	 * The level with its colour; when both dangers are rated and the beds are the worse, it says so,
	 * so a rusher is not mistaken for a duelist.
	 */
	public static String threat(ThreatScale scale, PlayerStats stats) {
		Threat threat = scale.threatOf(stats);
		return threat.colour() + threat.label() + (bedsAreTheWorse(scale, stats) ? " §7at beds" : "");
	}

	/** The same without colour, for chat: {@code EXTREME} or {@code EXTREME at beds}. */
	public static String plainThreat(ThreatScale scale, PlayerStats stats) {
		return scale.threatOf(stats).label() + (bedsAreTheWorse(scale, stats) ? " at beds" : "");
	}

	/**
	 * The one ratio a short line has room for: the FKDR, or the BBLR when the level is about beds —
	 * {@code 13.8 FKDR} or {@code 7.1 BBLR}.
	 */
	public static String headlineRatio(ThreatScale scale, PlayerStats stats) {
		return aboutBeds(scale, stats)
				? oneDecimal(ProfileMetrics.bedRatio(stats)) + " BBLR"
				: oneDecimal(stats.getFkdr()) + " FKDR";
	}

	private static boolean bedsAreTheWorse(ThreatScale scale, PlayerStats stats) {
		return scale.focus() == ThreatFocus.BOTH && scale.rate(stats).worseAtBeds();
	}

	/** Whether the level shown for this player comes from their beds rather than their fights. */
	static boolean aboutBeds(ThreatScale scale, PlayerStats stats) {
		return scale.focus() == ThreatFocus.BEDS || bedsAreTheWorse(scale, stats);
	}

	/** Cut at the chat limit, at a word where possible. */
	private static String fit(String line) {
		if (line.length() <= MAX_CHAT) {
			return line;
		}

		int space = line.lastIndexOf(' ', MAX_CHAT);
		return (space > 0 ? line.substring(0, space) : line.substring(0, MAX_CHAT)).strip();
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
