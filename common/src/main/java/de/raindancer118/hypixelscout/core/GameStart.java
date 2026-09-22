package de.raindancer118.hypixelscout.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether the match has begun, as opposed to the waiting lobby on the same server.
 *
 * <p>Hypixel's Mod API says which game and which map, but not when the waiting ends. What changes
 * at that moment is the scoreboard: in the waiting lobby nobody is on a real team, and once the
 * game is on, every player is on one that holds no more players than the mode allows. The chat
 * line that opens every game counts as well, for a scoreboard that says nothing useful.
 */
public final class GameStart {
	/** The line Hypixel opens every Bedwars game with, in English. */
	private static final String START_LINE = "Protect your bed and destroy the enemy beds.";

	private GameStart() {
	}

	/**
	 * @param teamOfPlayer every listed player's scoreboard team name, {@code ""} for none
	 * @param self         the name of the player running the mod
	 */
	public static boolean hasStarted(String mode, Map<String, String> teamOfPlayer, String self) {
		String own = teamOfPlayer.get(self);
		if (own == null || own.isEmpty()) {
			return false;
		}

		Map<String, Integer> sizes = new HashMap<>();
		teamOfPlayer.values().stream().filter(team -> !team.isEmpty())
				.forEach(team -> sizes.merge(team, 1, Integer::sum));

		int largest = sizes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		return sizes.size() >= 2 && largest <= BedwarsModes.teamSize(mode);
	}

	/** Whether a chat message is the opening line of a game, on a line of its own. */
	public static boolean isStartLine(String message) {
		if (message == null) {
			return false;
		}

		for (String line : message.split("\n")) {
			if (line.strip().equals(START_LINE)) {
				return true;
			}
		}

		return false;
	}
}
