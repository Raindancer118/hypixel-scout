package de.raindancer118.hypixelscout.core;

/**
 * Hypixel's ranks as they look in chat, so a name reads the same in this mod as it does there.
 *
 * <p>Section-sign strings, like {@link StatFormat}: the same output works on a screen, in a tooltip
 * and in a chat line.
 */
public final class Ranks {
	private Ranks() {
	}

	public static String colour(String rank) {
		if (rank == null) {
			return "§7";
		}

		switch (rank) {
			case "VIP":
			case "VIP_PLUS":
				return "§a";
			case "MVP":
			case "MVP_PLUS":
				return "§b";
			case "SUPERSTAR":
				return "§6";
			case "YOUTUBER":
			case "ADMIN":
			case "OWNER":
				return "§c";
			case "GAME_MASTER":
			case "MODERATOR":
				return "§2";
			case "HELPER":
				return "§9";
			default:
				return "§6";
		}
	}

	/** The bracket in front of the name, or an empty string for a player without a rank. */
	public static String tag(String rank) {
		if (rank == null) {
			return "";
		}

		switch (rank) {
			case "VIP":
				return "§a[VIP]";
			case "VIP_PLUS":
				return "§a[VIP§6+§a]";
			case "MVP":
				return "§b[MVP]";
			case "MVP_PLUS":
				return "§b[MVP§c+§b]";
			case "SUPERSTAR":
				return "§6[MVP§c++§6]";
			case "YOUTUBER":
				return "§c[§fYOUTUBE§c]";
			case "ADMIN":
				return "§c[ADMIN]";
			case "OWNER":
				return "§c[OWNER]";
			case "GAME_MASTER":
				return "§2[GM]";
			case "MODERATOR":
				return "§2[MOD]";
			case "HELPER":
				return "§9[HELPER]";
			default:
				return "";
		}
	}
}
