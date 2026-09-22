package de.raindancer118.hypixelscout.core;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a lobby's stats around so the same sixteen players are not fetched again every time
 * somebody leaves and rejoins.
 *
 * <p>The clock is a parameter rather than {@code System.currentTimeMillis()} so an expiry can be
 * tested without waiting ten minutes for one.
 */
public final class StatsCache {
	/** A session lasting all evening must not keep one entry per player ever seen. */
	public static final int MAX_ENTRIES = 512;

	private final Map<UUID, Entry> entries = new ConcurrentHashMap<UUID, Entry>();
	private final Clock clock;
	private volatile long ttlMillis;

	public StatsCache(Clock clock, long ttlMillis) {
		this.clock = clock;
		this.ttlMillis = ttlMillis;
	}

	/** The settings screen changes this while the game runs; it applies to every entry at once. */
	public void setTtlMillis(long ttlMillis) {
		this.ttlMillis = ttlMillis;
	}

	public PlayerStats get(UUID uuid) {
		Entry entry = entries.get(uuid);
		if (entry == null) {
			return null;
		}

		if (clock.millis() - entry.storedAt > ttlMillis) {
			entries.remove(uuid);
			return null;
		}

		return entry.stats;
	}

	public void put(UUID uuid, PlayerStats stats) {
		if (entries.size() >= MAX_ENTRIES) {
			evict();
		}

		entries.put(uuid, new Entry(stats, clock.millis()));
	}

	public void remove(UUID uuid) {
		entries.remove(uuid);
	}

	public void clear() {
		entries.clear();
	}

	public int size() {
		return entries.size();
	}

	/**
	 * Drops what has expired and, if that freed nothing, the oldest entry. Scanning the whole map
	 * is affordable at this size and keeps the class free of a second index to maintain.
	 */
	private void evict() {
		long now = clock.millis();
		UUID oldest = null;
		long oldestAt = Long.MAX_VALUE;
		boolean freed = false;

		for (Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Entry> each = it.next();
			if (now - each.getValue().storedAt > ttlMillis) {
				it.remove();
				freed = true;
			} else if (each.getValue().storedAt < oldestAt) {
				oldestAt = each.getValue().storedAt;
				oldest = each.getKey();
			}
		}

		if (!freed && oldest != null) {
			entries.remove(oldest);
		}
	}

	private static final class Entry {
		private final PlayerStats stats;
		private final long storedAt;

		private Entry(PlayerStats stats, long storedAt) {
			this.stats = stats;
			this.storedAt = storedAt;
		}
	}
}
