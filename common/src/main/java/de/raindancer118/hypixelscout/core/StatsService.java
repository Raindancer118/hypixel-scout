package de.raindancer118.hypixelscout.core;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Everything between "the overlay wants this player" and "the stats are in the cache".
 *
 * <p>The render thread only ever calls {@link #peek(UUID)} and {@link #request(UUID, String)}, and
 * neither blocks. A game start asks for sixteen players in one frame, so the same player must not
 * be fetched twice while the first request is still out, and a player who came back rejected must
 * not be asked again on the next frame — sixty frames a second would burn the key's whole budget
 * in five seconds.
 */
public final class StatsService {
	/** Four at a time: sixteen players resolve in four rounds without opening sixteen sockets. */
	private static final int THREADS = 4;

	private final StatsSource source;
	private final StatsCache cache;
	private final ExecutorService pool;

	private final Set<UUID> inFlight =
			Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
	/** Players whose failure was permanent enough not to retry until the roster changes. */
	private final Map<UUID, String> failed = new ConcurrentHashMap<UUID, String>();

	private volatile String lastError;
	private volatile StatsListener listener;

	public StatsService(StatsSource source, StatsCache cache) {
		this.source = source;
		this.cache = cache;
		this.pool = Executors.newFixedThreadPool(THREADS, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				// Daemon: a request still in the air must not keep the game from closing.
				Thread thread = new Thread(runnable, "HypixelScout-fetch");
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	/** One listener is enough: the mod has a single place that reacts to a player being resolved. */
	public void setListener(StatsListener listener) {
		this.listener = listener;
	}

	/** What is known right now. Never blocks, never fetches — safe on the render thread. */
	public PlayerStats peek(UUID uuid) {
		return cache.get(uuid);
	}

	/** Why this player has no stats, or {@code null} if nothing went wrong for them. */
	public String failureFor(UUID uuid) {
		return failed.get(uuid);
	}

	public boolean isPending(UUID uuid) {
		return inFlight.contains(uuid);
	}

	public int pendingCount() {
		return inFlight.size();
	}

	/** The most recent failure of any kind, for the chat warning and the status command. */
	public String getLastError() {
		return lastError;
	}

	/** Asks for a player unless they are already known, already on their way, or already refused. */
	public void request(final UUID uuid, final String observedName) {
		if (uuid == null || cache.get(uuid) != null || failed.containsKey(uuid)) {
			return;
		}

		if (!inFlight.add(uuid)) {
			return;
		}

		pool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					PlayerStats fetched = source.fetch(uuid, observedName);
					cache.put(uuid, fetched);
					failed.remove(uuid);

					notifyListener(uuid, fetched);
				} catch (RateLimitedException e) {
					// The mod's own budget, not Hypixel's answer: the next frame may well succeed,
					// so this player is left untouched rather than marked as failed.
					lastError = e.getMessage();
				} catch (HypixelApiException e) {
					failed.put(uuid, e.getMessage());
					lastError = e.getMessage();
				} catch (RuntimeException e) {
					failed.put(uuid, String.valueOf(e.getMessage()));
					lastError = String.valueOf(e.getMessage());
				} finally {
					inFlight.remove(uuid);
				}
			}
		});
	}

	/**
	 * A listener that throws has a bug of its own, and it is not this player's fault: the stats
	 * were fetched and belong in the cache either way.
	 */
	private void notifyListener(UUID uuid, PlayerStats stats) {
		StatsListener current = listener;
		if (current == null) {
			return;
		}

		try {
			current.onStats(uuid, stats);
		} catch (RuntimeException e) {
			lastError = "Notification failed: " + e.getMessage();
		}
	}

	/** Drops one player's answer, so the next request fetches them again. */
	public void forget(UUID uuid) {
		cache.remove(uuid);
		failed.remove(uuid);
	}

	/** A new game, a new key: everything refused before deserves another try. */
	public void clearFailures() {
		failed.clear();
		lastError = null;
	}

	public void invalidate() {
		cache.clear();
		clearFailures();
	}

	public void shutdown() {
		pool.shutdownNow();
	}
}
