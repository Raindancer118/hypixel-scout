package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Telling a Bedwars game from a Bedwars lobby.
 *
 * <p>Both report the same server type, so the overlay would otherwise list every one of the sixty
 * people standing around the lobby — which is what it did until this existed.
 */
class BedwarsLocationTest {
	@Test
	void countsAMatchWithAMapAsAGame() {
		assertTrue(BedwarsLocation.isInGame(true, "BEDWARS_FOUR_FOUR", "Waterfall"));
	}

	@Test
	void doesNotCountTheLobbyAsAGame() {
		// The main Bedwars lobby: the server type is still BEDWARS, and there is no map.
		assertFalse(BedwarsLocation.isInGame(true, "LOBBY", null));
		assertFalse(BedwarsLocation.isInGame(true, null, null));
		assertFalse(BedwarsLocation.isInGame(true, "BEDWARS_FOUR_FOUR", null),
				"a mode without a map is the queue, not a game");
	}

	@Test
	void doesNotCountAnEmptyMapNameAsAMap() {
		assertFalse(BedwarsLocation.isInGame(true, "BEDWARS_FOUR_FOUR", "  "));
	}

	@Test
	void doesNotCountAnotherGameAtAll() {
		assertFalse(BedwarsLocation.isInGame(false, "DUELS_BRIDGE", "Bridge"));
	}

	@Test
	void ignoresTheCaseOfTheLobbyMarker() {
		assertFalse(BedwarsLocation.isInGame(true, "lobby", "Lobby"));
	}
}
