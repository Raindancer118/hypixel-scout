package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sixteen players appear at once when a game starts, and the render thread must never be the one
 * waiting for them.
 */
class StatsServiceTest {
	private final AtomicInteger calls = new AtomicInteger();

	private StatsService service(StatsSource source) {
		return new StatsService(source, new StatsCache(Clock.SYSTEM, 600_000L));
	}

	private StatsSource sourceReturning(final int stars) {
		return new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				calls.incrementAndGet();
				return PlayerStats.builder(name, uuid).stars(stars).build();
			}
		};
	}

	private static void awaitQuiet(StatsService service) throws InterruptedException {
		for (int i = 0; i < 200 && service.pendingCount() > 0; i++) {
			Thread.sleep(10L);
		}
	}

	@Test
	void deliversTheStatsWithoutBlockingTheCaller() throws InterruptedException {
		StatsService service = service(sourceReturning(412));
		UUID id = UUID.randomUUID();

		assertNull(service.peek(id), "nothing is known the instant the request is made");
		service.request(id, "Technoblade");
		awaitQuiet(service);

		assertNotNull(service.peek(id));
		assertEquals(412, service.peek(id).getStars());
	}

	@Test
	void asksOnlyOnceWhileARequestIsStillInFlight() throws InterruptedException {
		final CountDownLatch release = new CountDownLatch(1);
		StatsService service = service(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				calls.incrementAndGet();
				try {
					release.await(2, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return PlayerStats.builder(name, uuid).build();
			}
		});

		UUID id = UUID.randomUUID();
		// The overlay re-renders sixty times a second and asks for every player it draws.
		for (int i = 0; i < 20; i++) {
			service.request(id, "Someone");
		}
		release.countDown();
		awaitQuiet(service);

		assertEquals(1, calls.get(), "twenty frames must not cost twenty requests");
	}

	@Test
	void servesTheSecondAskFromTheCache() throws InterruptedException {
		StatsService service = service(sourceReturning(1));
		UUID id = UUID.randomUUID();

		service.request(id, "Someone");
		awaitQuiet(service);
		service.request(id, "Someone");
		awaitQuiet(service);

		assertEquals(1, calls.get());
	}

	@Test
	void remembersThatAPlayerFailedInsteadOfRetryingForever() throws InterruptedException {
		StatsService service = service(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				calls.incrementAndGet();
				throw new HypixelApiException("Invalid API key", 403);
			}
		});

		UUID id = UUID.randomUUID();
		service.request(id, "Someone");
		awaitQuiet(service);
		service.request(id, "Someone");
		awaitQuiet(service);

		assertNull(service.peek(id));
		assertEquals(1, calls.get(), "a rejected key will not be accepted on the next frame either");
		assertNotNull(service.getLastError());
	}

	@Test
	void retriesAfterItsOwnRateLimitRatherThanGivingUp() throws InterruptedException {
		StatsService service = service(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				if (calls.incrementAndGet() == 1) {
					throw new RateLimitedException("window spent");
				}
				return PlayerStats.builder(name, uuid).stars(7).build();
			}
		});

		UUID id = UUID.randomUUID();
		service.request(id, "Someone");
		awaitQuiet(service);
		service.request(id, "Someone");
		awaitQuiet(service);

		assertEquals(2, calls.get(), "a local throttle is temporary, unlike a bad key");
		assertEquals(7, service.peek(id).getStars());
	}

	@Test
	void keepsANickInTheCacheSoItIsNotAskedAgain() throws InterruptedException {
		StatsService service = service(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				calls.incrementAndGet();
				return PlayerStats.nicked(name, uuid);
			}
		});

		UUID id = UUID.randomUUID();
		service.request(id, "xX_Nick_Xx");
		awaitQuiet(service);
		service.request(id, "xX_Nick_Xx");
		awaitQuiet(service);

		assertEquals(1, calls.get());
		assertTrue(service.peek(id).isNicked());
	}

	@Test
	void aForgottenPlayerIsFetchedAgain() throws InterruptedException {
		StatsService service = service(sourceReturning(7));
		UUID id = UUID.randomUUID();
		service.request(id, "Someone");
		awaitQuiet(service);

		service.forget(id);
		assertNull(service.peek(id));
		service.request(id, "Someone");
		awaitQuiet(service);

		assertEquals(2, calls.get());
		assertNotNull(service.peek(id));
	}
}
