package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The table and the lobby screen order players the same way, so the ordering lives here. */
class StatsSortingTest {
	private static PlayerStats player(String name, int stars, int fk, int fd, int w, int l) {
		return PlayerStats.builder(name, UUID.randomUUID())
				.stars(stars).finals(fk, fd).games(w, l).build();
	}

	@Test
	void ranksByWhicheverFieldWasAskedFor() {
		PlayerStats starry = player("Starry", 500, 100, 100, 10, 10);
		PlayerStats deadly = player("Deadly", 50, 1000, 100, 10, 10);

		assertTrue(StatsSorting.value(starry, SortMode.STARS)
				> StatsSorting.value(deadly, SortMode.STARS));
		assertTrue(StatsSorting.value(deadly, SortMode.FKDR)
				> StatsSorting.value(starry, SortMode.FKDR));
	}

	@Test
	void sinksPlayersNobodyKnowsToTheBottom() {
		// Otherwise a row would jump up the list the moment its answer arrived, right as you are
		// trying to read the one above it.
		assertEquals(-1.0, StatsSorting.value(null, SortMode.STARS), 1e-9);
		assertEquals(-1.0, StatsSorting.value(PlayerStats.nicked("Nick", UUID.randomUUID()),
				SortMode.FKDR), 1e-9);
	}

	@Test
	void sortsNamesAlphabeticallyRegardlessOfCase() {
		List<String> names = Arrays.asList("zebra", "Apple", "mango");
		names = new java.util.ArrayList<String>(names);
		java.util.Collections.sort(names, StatsSorting.BY_NAME);

		assertEquals(Arrays.asList("Apple", "mango", "zebra"), names);
	}

	@Test
	void readsAnUnknownModeAsStars() {
		PlayerStats stats = player("Someone", 42, 0, 0, 0, 0);

		assertEquals(42.0, StatsSorting.value(stats, null), 1e-9);
	}
}
