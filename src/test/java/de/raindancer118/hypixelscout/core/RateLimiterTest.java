package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hypixel answers a 429 with a cooldown and, repeated, revokes the key. Staying under the limit is
 * the mod's job, not the server's.
 */
class RateLimiterTest {
	private final AtomicLong now = new AtomicLong(0L);
	private final RateLimiter limiter = new RateLimiter(now::get, 300, 300_000L);

	@Test
	void allowsAFullWindowOfRequests() {
		for (int i = 0; i < 300; i++) {
			assertTrue(limiter.tryAcquire(), "request " + i + " is inside the budget");
		}
	}

	@Test
	void refusesTheRequestAfterTheBudgetIsSpent() {
		for (int i = 0; i < 300; i++) {
			limiter.tryAcquire();
		}

		assertFalse(limiter.tryAcquire());
	}

	@Test
	void letsTheBudgetRecoverAsTheWindowSlides() {
		for (int i = 0; i < 300; i++) {
			limiter.tryAcquire();
		}
		assertFalse(limiter.tryAcquire());

		now.set(300_001L);
		assertTrue(limiter.tryAcquire(), "the oldest request has aged out of the window");
	}

	@Test
	void slidesRatherThanResettingInBlocks() {
		// Two hundred now, a hundred much later: at the moment the first batch expires only that
		// batch may come back, or a burst of 400 would slip through one window.
		for (int i = 0; i < 200; i++) {
			limiter.tryAcquire();
		}
		now.set(200_000L);
		for (int i = 0; i < 100; i++) {
			limiter.tryAcquire();
		}

		now.set(300_001L);
		for (int i = 0; i < 200; i++) {
			assertTrue(limiter.tryAcquire(), "the first batch is free again");
		}
		assertFalse(limiter.tryAcquire(), "the second batch still counts");
	}
}
