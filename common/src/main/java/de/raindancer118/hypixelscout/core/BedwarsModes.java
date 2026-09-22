package de.raindancer118.hypixelscout.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hypixel's Bedwars mode ids, and what players actually call them.
 *
 * <p>{@code BEDWARS_FOUR_FOUR} is what the API and {@code /play} speak; nobody reads that mid-game.
 * The table header, the queue slot buttons and the lobby screen all go through {@link #shortName}.
 */
public final class BedwarsModes {
	/**
	 * What a quick-queue slot can be set to, in the order the settings cycle through them. The
	 * first entry is the empty slot, whose key then does nothing.
	 */
	public static final List<String> QUEUEABLE = List.of(
			"",
			"bedwars_eight_one",
			"bedwars_eight_two",
			"bedwars_four_three",
			"bedwars_four_four",
			"bedwars_two_four",
			"bedwars_eight_two_rush",
			"bedwars_four_four_rush",
			"bedwars_eight_two_ultimate",
			"bedwars_four_four_ultimate",
			"bedwars_eight_two_lucky",
			"bedwars_four_four_lucky",
			"bedwars_eight_two_voidless",
			"bedwars_four_four_voidless",
			"bedwars_castle");

	/** The team layouts; anything after them in an id is a variant and is appended as words. */
	private static final Map<String, String> LAYOUTS = Map.of(
			"EIGHT_ONE", "Solo",
			"EIGHT_TWO", "Doubles",
			"FOUR_THREE", "3v3v3v3",
			"FOUR_FOUR", "4v4v4v4",
			"TWO_FOUR", "4v4");

	private BedwarsModes() {
	}

	public static String shortName(String mode) {
		if (mode == null || mode.isBlank()) {
			return "";
		}

		String name = mode.trim().toUpperCase(Locale.ROOT).replaceFirst("^BEDWARS_", "");

		for (Map.Entry<String, String> layout : LAYOUTS.entrySet()) {
			if (name.equals(layout.getKey())) {
				return layout.getValue();
			}

			if (name.startsWith(layout.getKey() + "_")) {
				return layout.getValue() + " " + words(name.substring(layout.getKey().length() + 1));
			}
		}

		return words(name);
	}

	/**
	 * Whether a mode puts players in teams. In Solo there is nobody to report to, and the chat that
	 * would be team chat anywhere else goes to the whole lobby.
	 */
	public static boolean hasTeammates(String mode) {
		if (mode == null || mode.isBlank()) {
			return false;
		}

		String name = mode.trim().toUpperCase(Locale.ROOT).replaceFirst("^BEDWARS_", "");
		return !name.equals("EIGHT_ONE") && !name.startsWith("EIGHT_ONE_");
	}

	/** {@code CASTLE} → {@code Castle}, {@code LUCKY_V2} → {@code Lucky V2}. */
	private static String words(String snake) {
		StringBuilder out = new StringBuilder();

		for (String word : snake.split("_")) {
			if (word.isEmpty()) {
				continue;
			}

			if (!out.isEmpty()) {
				out.append(' ');
			}

			out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
		}

		return out.toString();
	}
}
