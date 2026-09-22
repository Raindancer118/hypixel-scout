package de.raindancer118.hypixelscout.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A sliding window over the last five minutes of requests.
 *
 * <p>Hypixel answers a request over the limit with a 429 and, if that keeps happening, takes the
 * key away. A fixed window that resets in blocks would let a burst of twice the budget through
 * across the seam, so the timestamps are kept and aged out one at a time.
 */
public final class RateLimiter {
	private final Deque<Long> timestamps = new ArrayDeque<Long>();
	private final Clock clock;
	private final int limit;
	private final long windowMillis;

	public RateLimiter(Clock clock, int limit, long windowMillis) {
		this.clock = clock;
		this.limit = limit;
		this.windowMillis = windowMillis;
	}

	/** Records and permits a request, or reports that the budget for this window is spent. */
	public synchronized boolean tryAcquire() {
		long now = clock.millis();

		while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
			timestamps.pollFirst();
		}

		if (timestamps.size() >= limit) {
			return false;
		}

		timestamps.addLast(Long.valueOf(now));
		return true;
	}

	/** What is left in the current window, for the status line of the command. */
	public synchronized int remaining() {
		long now = clock.millis();
		while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
			timestamps.pollFirst();
		}

		return limit - timestamps.size();
	}
}
