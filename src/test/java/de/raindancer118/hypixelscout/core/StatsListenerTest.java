package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** A nick is only worth announcing the moment it is found out, which is when the answer lands. */
class StatsListenerTest {
	@Test
	void announcesEveryPlayerItResolves() throws InterruptedException {
		final List<String> seen = new CopyOnWriteArrayList<String>();

		StatsService service = new StatsService(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				return "Nicked".equals(name) ? PlayerStats.nicked(name, uuid)
						: PlayerStats.builder(name, uuid).stars(5).build();
			}
		}, new StatsCache(Clock.SYSTEM, 600_000L));

		service.setListener(new StatsListener() {
			@Override
			public void onStats(UUID uuid, PlayerStats stats) {
				seen.add(stats.getName() + (stats.isNicked() ? ":nick" : ":ok"));
			}
		});

		service.request(UUID.randomUUID(), "Nicked");
		service.request(UUID.randomUUID(), "Normal");
		for (int i = 0; i < 200 && seen.size() < 2; i++) {
			Thread.sleep(10L);
		}

		assertTrue(seen.contains("Nicked:nick"), "" + seen);
		assertTrue(seen.contains("Normal:ok"), "" + seen);
	}

	@Test
	void doesNotFailAPlayerBecauseTheListenerThrew() throws InterruptedException {
		StatsService service = new StatsService(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String name) {
				return PlayerStats.builder(name, uuid).stars(5).build();
			}
		}, new StatsCache(Clock.SYSTEM, 600_000L));

		service.setListener(new StatsListener() {
			@Override
			public void onStats(UUID uuid, PlayerStats stats) {
				throw new IllegalStateException("the chat was not ready");
			}
		});

		UUID id = UUID.randomUUID();
		service.request(id, "Normal");
		for (int i = 0; i < 200 && service.pendingCount() > 0; i++) {
			Thread.sleep(10L);
		}

		assertNotNull(service.peek(id), "the stats were fetched; the notification is a side show");
		assertNull(service.failureFor(id));
	}
}
