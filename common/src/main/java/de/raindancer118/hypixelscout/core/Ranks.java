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

		return switch (rank) {
			case "VIP", "VIP_PLUS" -> "§a";
			case "MVP", "MVP_PLUS" -> "§b";
			case "SUPERSTAR" -> "§6";
			case "YOUTUBER", "ADMIN", "OWNER" -> "§c";
			case "GAME_MASTER", "MODERATOR" -> "§2";
			case "HELPER" -> "§9";
			default -> "§6";
		};
	}

	/** The bracket in front of the name, or an empty string for a player without a rank. */
	public static String tag(String rank) {
		if (rank == null) {
			return "";
		}

		return switch (rank) {
			case "VIP" -> "§a[VIP]";
			case "VIP_PLUS" -> "§a[VIP§6+§a]";
			case "MVP" -> "§b[MVP]";
			case "MVP_PLUS" -> "§b[MVP§c+§b]";
			case "SUPERSTAR" -> "§6[MVP§c++§6]";
			case "YOUTUBER" -> "§c[§fYOUTUBE§c]";
			case "ADMIN" -> "§c[ADMIN]";
			case "OWNER" -> "§c[OWNER]";
			case "GAME_MASTER" -> "§2[GM]";
			case "MODERATOR" -> "§2[MOD]";
			case "HELPER" -> "§9[HELPER]";
			default -> "";
		};
	}
}
