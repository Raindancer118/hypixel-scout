package de.raindancer118.hypixelscout.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A sliding window over the last five minutes of requests, corrected by what Hypixel reports.
 *
 * <p>Hypixel answers a request over the limit with a 429 and, if that keeps happening, takes the
 * key away. A fixed window that resets in blocks would let a burst of twice the budget through
 * across the seam, so the timestamps are kept and aged out one at a time.
 */
public final class RateLimiter {
	private final Deque<Long> timestamps = new ArrayDeque<Long>();
	private final Clock clock;
	private final long windowMillis;
	private int limit;

	/** What Hypixel last said is left, and until when that count stands; -1 when it said nothing. */
	private int observedRemaining = -1;
	private long observedUntil;
	/** Requests let through since that answer, which Hypixel had not counted yet. */
	private int sentSinceObservation;

	public RateLimiter(Clock clock, int limit, long windowMillis) {
		this.clock = clock;
		this.limit = limit;
		this.windowMillis = windowMillis;
	}

	/**
	 * Takes what Hypixel reported with an answer: its limit for this key, what is left, and the
	 * seconds until the window resets. The server's numbers win over the local guess — a production
	 * key has a larger budget, and another program on the same key spends from it unseen.
	 */
	public synchronized void observe(int reportedLimit, int reportedRemaining, long resetSeconds) {
		if (reportedLimit > 0) {
			limit = reportedLimit;
		}

		if (reportedRemaining >= 0 && resetSeconds >= 0) {
			observedRemaining = reportedRemaining;
			observedUntil = clock.millis() + resetSeconds * 1000L;
			sentSinceObservation = 0;
		}
	}

	/** Records and permits a request, or reports that the budget for this window is spent. */
	public synchronized boolean tryAcquire() {
		long now = clock.millis();
		prune(now);

		if (observationStands(now) && observedRemaining - sentSinceObservation <= 0) {
			return false;
		}

		if (timestamps.size() >= limit) {
			return false;
		}

		timestamps.addLast(Long.valueOf(now));
		sentSinceObservation++;
		return true;
	}

	/** What is left in the current window, for the status line and the game tab. */
	public synchronized int remaining() {
		long now = clock.millis();
		prune(now);

		int local = limit - timestamps.size();
		if (observationStands(now)) {
			return Math.max(0, Math.min(local, observedRemaining - sentSinceObservation));
		}

		return Math.max(0, local);
	}

	/** The budget per window, as Hypixel reported it for this key once it has answered. */
	public synchronized int limit() {
		return limit;
	}

	/** How long until Hypixel opens the window again, or {@code 0} when nothing is known. */
	public synchronized long millisUntilReset() {
		long now = clock.millis();
		return observationStands(now) ? observedUntil - now : 0L;
	}

	private boolean observationStands(long now) {
		return observedRemaining >= 0 && now <= observedUntil;
	}

	private void prune(long now) {
		while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
			timestamps.pollFirst();
		}
	}
}
