package de.raindancer118.hypixelscout.core;

/**
 * Whether the player is in a Bedwars game, as opposed to standing in a Bedwars lobby.
 *
 * <p>Hypixel reports the same server type for both. The difference is the map: a running match
 * names one, the lobby does not. Without this distinction the overlay lists everybody in the lobby
 * — sixty people, none of whom you are about to fight.
 */
public final class BedwarsLocation {
	private static final String LOBBY = "LOBBY";

	private BedwarsLocation() {
	}

	public static boolean isInGame(boolean bedwars, String mode, String map) {
		if (!bedwars) {
			return false;
		}

		if (mode == null || LOBBY.equalsIgnoreCase(mode.trim())) {
			return false;
		}

		return map != null && !map.trim().isEmpty();
	}
}
