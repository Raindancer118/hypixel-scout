package de.raindancer118.hypixelscout.core;

import java.util.Locale;

/**
 * Renders the numbers the way Bedwars players already read them: the star in its prestige colour,
 * the ratios coloured by how much trouble they mean, the counts shortened so a column stays a
 * column.
 *
 * <p>Plain strings with section-sign colour codes, so the same output works in the HUD, in a
 * tooltip and in chat.
 */
public final class StatFormat {
	private static final char STAR = '✴';
	private static final long DAY = 86_400_000L;

	/** One colour per hundred stars, in the order Hypixel itself uses for the prestige names. */
	private static final String[] PRESTIGE = {
			"§7", "§f", "§6", "§b", "§2",
			"§3", "§4", "§d", "§9", "§5"
	};

	/** The rainbow prestige from 1000 on: each character of the bracket takes the next colour. */
	private static final String[] RAINBOW = {
			"§c", "§6", "§e", "§a", "§b", "§d", "§5"
	};

	private StatFormat() {
	}

	public static String star(int stars) {
		String text = "[" + stars + STAR + "]";

		if (stars >= 1000) {
			StringBuilder rainbow = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				rainbow.append(RAINBOW[i % RAINBOW.length]).append(text.charAt(i));
			}

			return rainbow.toString();
		}

		return PRESTIGE[Math.min(stars / 100, PRESTIGE.length - 1)] + text;
	}

	/** Green below one, then up through yellow to dark red — the colour is the warning. */
	public static String ratioColour(double ratio) {
		if (ratio < 1.0) {
			return "§a";
		}
		if (ratio < 3.0) {
			return "§e";
		}
		if (ratio < 5.0) {
			return "§6";
		}
		if (ratio < 10.0) {
			return "§c";
		}

		return "§4";
	}

	public static String ratio(double value) {
		return String.format(Locale.ROOT, "%.2f", Double.valueOf(value));
	}

	public static String count(int value) {
		if (value < 10_000) {
			return Integer.toString(value);
		}
		if (value < 1_000_000) {
			return String.format(Locale.ROOT, "%.1fk", Double.valueOf(value / 1000.0));
		}

		return String.format(Locale.ROOT, "%.1fM", Double.valueOf(value / 1_000_000.0));
	}

	/** A hidden winstreak is not a streak of zero, and saying so would be a lie about the player. */
	public static String winstreak(Integer value) {
		return value == null ? "?" : value.toString();
	}

	/** Days since the account first logged in — a young account with high stats is the tell. */
	public static String age(long firstLogin, long now) {
		if (firstLogin <= 0L) {
			return "?";
		}

		long days = (now - firstLogin) / DAY;
		if (days < 365) {
			return days + "d";
		}

		return String.format(Locale.ROOT, "%.1fy", Double.valueOf(days / 365.0));
	}
}
