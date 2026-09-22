package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.StatFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The one place that decides what a player's stats look like as text.
 *
 * <p>The table, the look tooltip, the nametag and the chat hover all show the same numbers, so they
 * all come from here: a change to the wording happens once rather than in four renderers that
 * slowly drift apart.
 */
public final class StatsLines {
	private StatsLines() {
	}

	/** The single line the table draws for one player. */
	public static String row(String name, PlayerStats stats, boolean pending, String failure,
			ScoutConfig config) {
		if (stats == null) {
			return "§8" + name + " §7" + (pending || failure == null ? "…" : "§c!");
		}

		if (stats.isNicked()) {
			// Said out loud rather than left blank: a nick is itself information about a player.
			return "§d[NICK] §f" + name;
		}

		StringBuilder row = new StringBuilder();
		row.append(StatFormat.star(stats.getStars())).append(' ');
		row.append(rankColour(stats.getRank())).append(name);
		row.append("  ").append(StatFormat.ratioColour(stats.getFkdr()))
				.append(StatFormat.ratio(stats.getFkdr())).append("§7 fkdr");

		if (config.isShowWlr()) {
			row.append("  ").append(StatFormat.ratioColour(stats.getWlr()))
					.append(StatFormat.ratio(stats.getWlr())).append("§7 wlr");
		}

		if (config.isShowWinstreak()) {
			row.append("  §f").append(StatFormat.winstreak(stats.getWinstreak()))
					.append("§7 ws");
		}

		if (config.isShowAccountAge()) {
			row.append("  §8").append(StatFormat.age(stats.getFirstLogin(),
					System.currentTimeMillis()));
		}

		return row.toString();
	}

	/** What goes above a player's head: the star, in its prestige colour, before the name. */
	public static String nametag(String name, PlayerStats stats) {
		if (stats == null) {
			return "§8[…] §f" + name;
		}
		if (stats.isNicked()) {
			return "§d[NICK] §f" + name;
		}

		return StatFormat.star(stats.getStars()) + " " + rankColour(stats.getRank()) + name
				+ " " + StatFormat.ratioColour(stats.getFkdr()) + StatFormat.ratio(stats.getFkdr());
	}

	/** The block shown when looking at a player, or hovering their name in chat. */
	public static List<String> detail(String name, PlayerStats stats, boolean pending,
			String failure) {
		List<String> lines = new ArrayList<String>();

		if (stats == null) {
			lines.add("§f" + name);
			lines.add(pending ? "§7Looking them up…"
					: failure == null ? "§7No stats yet" : "§c" + failure);
			return lines;
		}

		if (stats.isNicked()) {
			lines.add("§f" + name);
			lines.add("§dNicked");
			lines.add("§7Hypixel does not know this name, so there is nothing to look up.");
			return lines;
		}

		lines.add(StatFormat.star(stats.getStars()) + " " + rankColour(stats.getRank()) + name);
		lines.add("§7FKDR  " + StatFormat.ratioColour(stats.getFkdr())
				+ StatFormat.ratio(stats.getFkdr())
				+ "§8  (" + StatFormat.count(stats.getFinalKills()) + " / "
				+ StatFormat.count(stats.getFinalDeaths()) + ")");
		lines.add("§7W/L   " + StatFormat.ratioColour(stats.getWlr())
				+ StatFormat.ratio(stats.getWlr())
				+ "§8  (" + StatFormat.count(stats.getWins()) + " / "
				+ StatFormat.count(stats.getLosses()) + ")");
		lines.add("§7Beds  §f" + StatFormat.count(stats.getBedsBroken())
				+ "§7   Streak §f" + StatFormat.winstreak(stats.getWinstreak()));
		lines.add("§7F/Game §f" + StatFormat.ratio(ProfileMetrics.finalsPerGame(stats))
				+ "§7   F/Star §f" + StatFormat.ratio(ProfileMetrics.finalsPerStar(stats)));
		lines.add("§7Level §f" + (int) stats.getNetworkLevel()
				+ "§7   Account §f"
				+ StatFormat.age(stats.getFirstLogin(), System.currentTimeMillis()));

		Map<String, String> socials = stats.getSocials();
		if (!socials.isEmpty()) {
			StringBuilder line = new StringBuilder("§8");
			for (String service : socials.keySet()) {
				line.append(service.toLowerCase(java.util.Locale.ROOT)).append(' ');
			}
			lines.add(line.toString().trim());
		}

		return lines;
	}

	/** Hypixel's own rank colours, so a name reads the same here as it does in chat. */
	public static String rankColour(String rank) {
		if (rank == null) {
			return "§7";
		}
		if (rank.startsWith("MVP")) {
			return "§b";
		}
		if (rank.startsWith("VIP")) {
			return "§a";
		}
		if ("YOUTUBER".equals(rank)) {
			return "§c";
		}

		return "§6";
	}
}
