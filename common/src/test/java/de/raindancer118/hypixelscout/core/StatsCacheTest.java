package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sixteen players per game and a 300-request budget per five minutes means the same lobby must not
 * be fetched twice. The clock is injected so an expiry can be tested without waiting for one.
 */
class StatsCacheTest {
	private final AtomicLong now = new AtomicLong(0L);
	private final StatsCache cache = new StatsCache(now::get, 600_000L);

	private static PlayerStats stats(String name) {
		return PlayerStats.builder(name, UUID.randomUUID()).stars(100).build();
	}

	@Test
	void returnsWhatWasPutIn() {
		UUID id = UUID.randomUUID();
		cache.put(id, stats("Someone"));

		assertNotNull(cache.get(id));
		assertEquals("Someone", cache.get(id).getName());
	}

	@Test
	void missesForAnUnknownPlayer() {
		assertNull(cache.get(UUID.randomUUID()));
	}

	@Test
	void forgetsAnEntryOnceItIsStale() {
		UUID id = UUID.randomUUID();
		cache.put(id, stats("Someone"));

		now.set(599_999L);
		assertNotNull(cache.get(id), "still fresh one millisecond before the TTL");

		now.set(600_001L);
		assertNull(cache.get(id), "a player's stars change while you are in the lobby with them");
	}

	@Test
	void doesNotGrowWithoutBound() {
		for (int i = 0; i < StatsCache.MAX_ENTRIES + 50; i++) {
			cache.put(UUID.randomUUID(), stats("Player" + i));
		}

		assertTrue(cache.size() <= StatsCache.MAX_ENTRIES,
				"a long session must not leak one entry per player ever seen");
	}

	@Test
	void aShorterLifetimeSetLaterAppliesToWhatIsAlreadyStored() {
		UUID id = UUID.randomUUID();
		cache.put(id, stats("Someone"));
		now.set(120_000L);

		cache.setTtlMillis(60_000L);

		assertNull(cache.get(id));
	}
}
