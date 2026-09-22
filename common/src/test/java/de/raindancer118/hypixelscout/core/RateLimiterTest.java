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

	@Test
	void aLargerLimitReportedByHypixelIsUsed() {
		// A production key: Hypixel says 600, so the mod must not stop at its own guess of 300.
		limiter.observe(600, 599, 300);

		for (int i = 0; i < 599; i++) {
			assertTrue(limiter.tryAcquire(), "request " + i + " is inside the reported budget");
		}
		assertFalse(limiter.tryAcquire());
		assertEquals(600, limiter.limit());
	}

	@Test
	void theServersCountWinsOverTheLocalOne() {
		// Something else used the same key: Hypixel knows, the local counter does not.
		limiter.observe(300, 2, 120);

		assertEquals(2, limiter.remaining());
		assertTrue(limiter.tryAcquire());
		assertTrue(limiter.tryAcquire());
		assertFalse(limiter.tryAcquire());
		assertEquals(0, limiter.remaining());
	}

	@Test
	void aSpentBudgetOpensAgainWhenHypixelSaidItWould() {
		limiter.observe(300, 0, 42);
		assertFalse(limiter.tryAcquire());
		assertEquals(42_000L, limiter.millisUntilReset());

		now.addAndGet(42_001L);

		assertTrue(limiter.tryAcquire());
		assertEquals(0L, limiter.millisUntilReset());
	}

	@Test
	void nonsenseFromTheServerIsIgnored() {
		limiter.observe(-1, -5, -3);

		assertEquals(300, limiter.limit());
		assertTrue(limiter.tryAcquire());
	}
}
