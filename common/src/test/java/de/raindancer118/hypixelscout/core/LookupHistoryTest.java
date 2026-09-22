package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LookupHistoryTest {
	@Test
	void theMostRecentLookupComesFirst() {
		LookupHistory history = new LookupHistory(5);
		history.add("Alpha");
		history.add("Bravo");

		assertThat(history.names()).containsExactly("Bravo", "Alpha");
	}

	@Test
	void lookingSomebodyUpAgainMovesThemToTheTopRegardlessOfCase() {
		LookupHistory history = new LookupHistory(5);
		history.add("Alpha");
		history.add("Bravo");
		history.add("alpha");

		assertThat(history.names()).containsExactly("alpha", "Bravo");
	}

	@Test
	void theOldestFallsOffTheEnd() {
		LookupHistory history = new LookupHistory(2);
		history.add("Alpha");
		history.add("Bravo");
		history.add("Charlie");

		assertThat(history.names()).containsExactly("Charlie", "Bravo");
	}

	@Test
	void blanksAreIgnoredAndStartingEntriesAreKept() {
		LookupHistory history = new LookupHistory(3, List.of("Alpha", "", "Bravo"));
		history.add("  ");
		history.add(null);

		assertThat(history.names()).containsExactly("Alpha", "Bravo");
	}
}
