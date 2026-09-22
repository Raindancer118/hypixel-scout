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
	private static final long DAY = 86_400_000L;

	/**
	 * How a star level is coloured, one entry per hundred stars from 0 to 10 000: which colour each
	 * part of {@code [1234✪]} takes — bracket, each digit, the star, the closing bracket.
	 *
	 * <p>These are Hypixel's own prestige colours, as Statsify has them (github.com/Statsify,
	 * GPL-3.0; the colour table is data, re-typed here). Only 1000 is the rainbow; every hundred
	 * after it has a scheme of its own.
	 */
	private interface Scheme {
		/** The colour code of part {@code index} out of {@code count}. */
		String colour(int index, int count);
	}

	private static final Scheme[] PRESTIGES = {
			uniform("§7"), // 0 none
			uniform("§f"), // 100 iron
			uniform("§6"), // 200 gold
			uniform("§b"), // 300 diamond
			uniform("§2"), // 400 emerald
			uniform("§3"), // 500 sapphire
			uniform("§4"), // 600 ruby
			uniform("§d"), // 700 crystal
			uniform("§9"), // 800 opal
			uniform("§5"), // 900 amethyst
			cycle("§c", "§6", "§e", "§a", "§b", "§d", "§5"), // 1000 rainbow
			uniform("§7", "§f", "§7"), // 1100 iron_prime
			uniform("§7", "§e", "§6"), // 1200 gold_prime
			uniform("§7", "§b", "§3"), // 1300 diamond_prime
			uniform("§7", "§a", "§2"), // 1400 emerald_prime
			uniform("§7", "§3", "§9"), // 1500 sapphire_prime
			uniform("§7", "§c", "§4"), // 1600 ruby_prime
			uniform("§7", "§d", "§5"), // 1700 crystal_prime
			uniform("§7", "§9", "§1"), // 1800 opal_prime
			uniform("§7", "§5", "§8"), // 1900 amethyst_prime
			cycle("§8", "§7", "§f", "§f", "§7", "§7"), // 2000 mirror
			cycle("§f", "§f", "§e", "§e", "§6", "§6", "§6"), // 2100 light
			cycle("§6", "§6", "§f", "§f", "§b", "§3", "§3"), // 2200 dawn
			cycle("§5", "§5", "§d", "§d", "§6", "§e", "§e"), // 2300 dusk
			cycle("§b", "§b", "§f", "§f", "§7", "§7", "§8", "§8"), // 2400 air
			cycle("§f", "§f", "§a", "§a", "§2", "§2", "§2", "§2"), // 2500 wind
			cycle("§4", "§4", "§c", "§c", "§d", "§d", "§5", "§5"), // 2600 nebula
			cycle("§e", "§e", "§f", "§f", "§8", "§8", "§8"), // 2700 thunder
			cycle("§a", "§a", "§2", "§2", "§6", "§6", "§e", "§e"), // 2800 earth
			cycle("§b", "§b", "§3", "§3", "§9", "§9", "§1", "§1"), // 2900 water
			cycle("§e", "§e", "§6", "§6", "§c", "§c", "§4", "§4"), // 3000 fire
			cycle("§9", "§9", "§3", "§3", "§6", "§6", "§e"), // 3100 sunrise
			cycle("§c", "§4", "§7", "§7", "§4", "§c", "§c"), // 3200 eclipse
			cycle("§9", "§9", "§9", "§d", "§c", "§c", "§4"), // 3300 gamma
			cycle("§2", "§a", "§d", "§d", "§5", "§5"), // 3400 majestic
			cycle("§c", "§c", "§4", "§4", "§2", "§a", "§a"), // 3500 andesine
			cycle("§a", "§a", "§a", "§b", "§9", "§9", "§1"), // 3600 marine
			cycle("§4", "§4", "§c", "§c", "§b", "§3", "§3"), // 3700 element
			cycle("§1", "§1", "§9", "§5", "§5", "§d", "§1"), // 3800 galaxy
			cycle("§c", "§c", "§a", "§a", "§3", "§9", "§9"), // 3900 atomic
			cycle("§5", "§5", "§c", "§c", "§6", "§6", "§e"), // 4000 sunset
			cycle("§e", "§e", "§6", "§c", "§d", "§d", "§5"), // 4100 time
			cycle("§1", "§9", "§3", "§b", "§f", "§7", "§7"), // 4200 winter
			cycle("§0", "§5", "§8", "§8", "§5", "§5"), // 4300 obsidian
			cycle("§2", "§2", "§a", "§e", "§6", "§5", "§d"), // 4400 spring
			cycle("§f", "§f", "§b", "§b", "§3", "§3", "§3"), // 4500 ice
			cycle("§3", "§b", "§e", "§e", "§6", "§d", "§5"), // 4600 summer
			cycle("§f", "§4", "§c", "§c", "§9", "§1", "§9"), // 4700 spinel
			cycle("§5", "§5", "§c", "§6", "§e", "§b", "§3"), // 4800 autumn
			cycle("§2", "§a", "§f", "§f", "§a", "§a"), // 4900 mystic
			cycle("§4", "§4", "§5", "§9", "§9", "§1", "§0"), // 5000 eternal
			cycle("§4", "§c", "§c", "§6", "§e", "§f", "§4"), // 5100 burnout
			cycle("§1", "§9", "§3", "§b", "§f", "§e", "§1"), // 5200 cooldown
			cycle("§5", "§d", "§e", "§f", "§e", "§d"), // 5300 obliteration
			cycle("§3", "§a", "§2", "§8", "§2", "§a", "§3"), // 5400 ender
			cycle("§2", "§a", "§e", "§f", "§b", "§d", "§5"), // 5500 brust
			cycle("§4", "§c", "§e", "§f", "§e", "§c"), // 5600 comical
			cycle("§4", "§6", "§2", "§3", "§9", "§5", "§8"), // 5700 lusterlost
			cycle("§5", "§c", "§6", "§f", "§b", "§3", "§9"), // 5800 maelstrom
			cycle("§7", "§0", "§8", "§7", "§f", "§f"), // 5900 time_undone
			cycle("§c", "§f", "§f", "§f", "§f"), // 6000 umbrella
			cycle("§6", "§e", "§f", "§f", "§f", "§b", "§3"), // 6100 luminous
			cycle("§e", "§f", "§e", "§6", "§6", "§f", "§e"), // 6200 tortilla
			uniform("§a", "§e", "§a"), // 6300 corn
			cycle("§b", "§b", "§c", "§c", "§c", "§a", "§a"), // 6400 bittersweet
			cycle("§3", "§3", "§a", "§a", "§f", "§a", "§3"), // 6500 sweetsour
			uniform("§9", "§d", "§b"), // 6600 pop
			uniform("§5", "§d", "§f"), // 6700 bubblegum
			cycle("§0", "§6", "§6", "§e", "§e", "§f", "§f"), // 6800 contrast
			cycle("§a", "§a", "§a", "§a", "§2", "§2", "§8"), // 6900 blended
			uniform("§3", "§b", "§f"), // 7000 allay
			cycle("§4", "§c", "§6", "§e", "§c", "§6", "§e", "§4"), // 7100 blaze
			cycle("§2", "§a", "§f", "§2", "§a", "§f", "§8"), // 7200 creeper
			cycle("§2", "§3", "§3", "§b", "§b", "§a", "§2"), // 7300 drowned
			uniform("§8", "§8", "§d"), // 7400 enderman
			cycle("§6", "§6", "§2", "§2", "§f", "§f", "§f"), // 7500 frog
			cycle("§f", "§f", "§f", "§7", "§7", "§c", "§8"), // 7600 ghast
			uniform("§d", "§c", "§6"), // 7700 hoglin
			cycle("§8", "§7", "§f", "§f", "§f", "§e", "§8"), // 7800 iron_golem
			cycle("§6", "§f", "§2", "§6", "§2", "§f"), // 7900 jerry
			cycle("§2", "§a", "§a", "§a", "§c", "§4", "§2"), // 8000 kringle
			cycle("§8", "§7", "§f", "§b", "§3", "§9", "§1"), // 8100 liquid
			uniform("§f", "§f", "§a"), // 8200 mint
			cycle("§8", "§8", "§4", "§4", "§c", "§c", "§8"), // 8300 neglected
			cycle("§f", "§d", "§d", "§d", "§a", "§a", "§f"), // 8400 onion
			uniform("§3", "§6", "§e"), // 8500 poser
			uniform("§d", "§f", "§e"), // 8600 quartz
			uniform("§8", "§6"), // 8700 rich
			cycle("§4", "§4", "§4", "§c", "§c", "§f", "§f"), // 8800 sanguine
			cycle("§9", "§b", "§b", "§b", "§3", "§3", "§9"), // 8900 titanic
			suffix("§d", new String[] {"§5", "§8"}), // 9000 unorthodox
			cycle("§0", "§c", "§6", "§6", "§c", "§c", "§4"), // 9100 volcanic
			uniform("§2", "§d", "§a"), // 9200 weeping_cherry
			uniform("§f", "§8", "§f"), // 9300 x_ray
			prefix(new String[] {"§e", "§6", "§4"}, "§8"), // 9400 yearn
			cycle("§0", "§0", "§8", "§8", "§7", "§7", "§f", "§f"), // 9500 zebra
			cycle("§e", "§e", "§e", "§0", "§0", "§e", "§0"), // 9600 caution
			cycle("§d", "§d", "§d", "§e", "§e", "§b", "§e"), // 9700 indescribable
			uniform("§0", "§8"), // 9800 forgotten
			cycle("§8", "§7", "§f", "§f", "§f", "§e", "§f"), // 9900 fuse
			cycle("§9", "§b", "§f", "§f", "§f", "§c", "§4"), // 10000 prestigious
	};

	private StatFormat() {
	}

	/** Bracket, bold digits and all: the star level exactly as Hypixel prints it in Bedwars. */
	public static String star(int stars) {
		int level = Math.max(0, stars);
		Scheme scheme = PRESTIGES[Math.min(level / 100, PRESTIGES.length - 1)];

		String digits = Integer.toString(level);
		String[] parts = new String[digits.length() + 3];
		parts[0] = "[";
		for (int i = 0; i < digits.length(); i++) {
			parts[i + 1] = String.valueOf(digits.charAt(i));
		}
		parts[parts.length - 2] = starSymbol(level);
		parts[parts.length - 1] = "]";

		// A colour code only where the colour changes, so the string stays short in chat.
		StringBuilder out = new StringBuilder();
		String current = null;
		for (int i = 0; i < parts.length; i++) {
			String colour = scheme.colour(i, parts.length);
			if (!colour.equals(current)) {
				out.append(colour);
				current = colour;
			}
			out.append(parts[i]);
		}

		return out.toString();
	}

	/**
	 * The symbol after the number. It changes one prestige after each thousand — 1000 still has the
	 * old star, 1100 the new one — which is how the game shows it.
	 */
	public static String starSymbol(int stars) {
		if (stars >= 4100) {
			return "✭";
		}
		if (stars >= 3100) {
			return "✥";
		}
		if (stars >= 2100) {
			return "⚝";
		}
		if (stars >= 1100) {
			return "✪";
		}
		return "✫";
	}

	/** One colour for the brackets, one for the digits, one for the star. */
	private static Scheme uniform(String bracket, String digit, String star) {
		return (index, count) -> index == 0 || index == count - 1 ? bracket
				: index == count - 2 ? star : digit;
	}

	private static Scheme uniform(String bracket, String digit) {
		return uniform(bracket, digit, digit);
	}

	private static Scheme uniform(String colour) {
		return uniform(colour, colour, colour);
	}

	/** Each part the next colour of the list, starting over when it runs out. */
	private static Scheme cycle(String... colours) {
		return (index, count) -> colours[index % colours.length];
	}

	/** The first parts in their own colours, everything after in one. */
	private static Scheme prefix(String[] first, String rest) {
		return (index, count) -> index < first.length ? first[index] : rest;
	}

	/** Everything in one colour but the last parts, counted from the end as Statsify does. */
	private static Scheme suffix(String rest, String[] last) {
		return (index, count) -> {
			int fromEnd = count - index;
			return fromEnd >= last.length ? rest : last[fromEnd];
		};
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

	/** Beds broken per game: under half a bed is a player who rarely leaves, two is a rusher. */
	public static String bedsPerGameColour(double perGame) {
		return scale(perGame, 0.5, 1.0, 1.5, 2.0);
	}

	/** Kills per game: two is ordinary, eight means they spend the game in fights and win them. */
	public static String killsPerGameColour(double perGame) {
		return scale(perGame, 2.0, 3.0, 5.0, 8.0);
	}

	/** Green, yellow, gold, red, dark red, stepping at each bound. */
	private static String scale(double value, double green, double yellow, double gold, double red) {
		if (value < green) {
			return "§a";
		}
		if (value < yellow) {
			return "§e";
		}
		if (value < gold) {
			return "§6";
		}
		if (value < red) {
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
