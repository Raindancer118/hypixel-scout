package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NickAwareSourceTest {
	private static final UUID REAL = UUID.fromString("00000000-0000-4000-8000-000000000001");
	private static final UUID STAND_IN = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

	private final AtomicInteger hypixelCalls = new AtomicInteger();
	private final StatsSource hypixel = (uuid, name) -> {
		hypixelCalls.incrementAndGet();
		return PlayerStats.builder(name, uuid).stars(300).build();
	};

	private NickAwareSource source(Map<String, UUID> accounts) {
		return new NickAwareSource(hypixel, name -> accounts.get(name.toLowerCase(java.util.Locale.ROOT)));
	}

	@Test
	void aNameNoAccountHasIsANickAndCostsNoHypixelRequest() {
		PlayerStats stats = source(Map.of()).fetch(STAND_IN, "Glimmer");

		assertThat(stats.isNicked()).isTrue();
		assertThat(stats.getName()).isEqualTo("Glimmer");
		assertThat(hypixelCalls).hasValue(0);
	}

	@Test
	void aNameThatBelongsToSomebodyElseIsANickToo() {
		// Hypixel handed out a real player's name; the tab list's ID is not theirs.
		PlayerStats stats = source(Map.of("sundial", REAL)).fetch(STAND_IN, "Sundial");

		assertThat(stats.isNicked()).isTrue();
		assertThat(hypixelCalls).hasValue(0);
	}

	@Test
	void aRealPlayerIsAskedAboutAtHypixel() {
		PlayerStats stats = source(Map.of("sundial", REAL)).fetch(REAL, "Sundial");

		assertThat(stats.isNicked()).isFalse();
		assertThat(stats.getStars()).isEqualTo(300);
		assertThat(hypixelCalls).hasValue(1);
	}

	@Test
	void whenMojangDoesNotAnswerHypixelDecides() {
		NickAwareSource source = new NickAwareSource(hypixel, name -> {
			throw new HypixelApiException("Could not reach Mojang", HypixelApiException.NO_RESPONSE);
		});

		assertThat(source.fetch(REAL, "Sundial").isNicked()).isFalse();
		assertThat(hypixelCalls).hasValue(1);
	}

	@Test
	void aLookupWithoutANameGoesStraightToHypixel() {
		source(Map.of()).fetch(REAL, null);

		assertThat(hypixelCalls).hasValue(1);
	}
}
