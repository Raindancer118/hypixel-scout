package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RosterTest {
	private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID BRAVO = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final UUID CHARLIE = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	/** Records who was asked for, and answers every request at once. */
	private final Set<UUID> asked = ConcurrentHashMap.newKeySet();
	private final StatsService stats = new StatsService((uuid, name) -> {
		asked.add(uuid);
		return PlayerStats.builder(name, uuid).stars(100).build();
	}, new StatsCache(Clock.SYSTEM, 600_000L));

	private final Roster roster = new Roster(stats);

	private static Roster.Member member(String name, UUID uuid) {
		return new Roster.Member(name, uuid);
	}

	@Test
	void nothingIsListedOrFetchedOutsideABedwarsGame() {
		roster.refresh(List.of(member("Alpha", ALPHA)));

		assertThat(roster.members()).isEmpty();
		assertThat(asked).isEmpty();
	}

	@Test
	void aGameListsEveryPlayerAndAsksForEachOfThem() throws Exception {
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		roster.refresh(List.of(member("Alpha", ALPHA), member("Bravo", BRAVO)));

		assertThat(roster.members()).extracting(Roster.Member::name).containsExactly("Alpha", "Bravo");
		assertThat(roster.uuidOf("Bravo")).isEqualTo(BRAVO);

		for (int i = 0; i < 100 && asked.size() < 2; i++) {
			Thread.sleep(10);
		}
		assertThat(asked).containsExactlyInAnyOrder(ALPHA, BRAVO);
	}

	@Test
	void decorationInTheTabListIsNotAPlayer() {
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		roster.refresh(List.of(member("Alpha", ALPHA), member("§cRED", UUID.randomUUID()),
				member("!!", UUID.randomUUID()), member("ab", UUID.randomUUID()),
				member(null, UUID.randomUUID()), member("Nobody", null)));

		assertThat(roster.members()).extracting(Roster.Member::name).containsExactly("Alpha");
	}

	@Test
	void playersWhoStayedKeepTheirPlaceAndNewcomersGoToTheEnd() {
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		roster.refresh(List.of(member("Alpha", ALPHA), member("Bravo", BRAVO)));
		roster.refresh(List.of(member("Charlie", CHARLIE), member("Bravo", BRAVO)));

		assertThat(roster.members()).extracting(Roster.Member::name).containsExactly("Bravo", "Charlie");
	}

	@Test
	void leavingTheGameEmptiesTheList() {
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		roster.refresh(List.of(member("Alpha", ALPHA)));

		roster.onLocationChanged(false, null, null);

		assertThat(roster.members()).isEmpty();
		assertThat(roster.isInGame()).isFalse();
		assertThat(roster.mode()).isNull();
	}

	@Test
	void theGameClockStartsWhenTheGameDoesAndNotOnEveryUpdate() throws Exception {
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		Thread.sleep(20);
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");

		assertThat(roster.millisSinceStart()).isGreaterThanOrEqualTo(20);
	}

	@Test
	void outsideAGameTheClockReadsForever() {
		assertThat(roster.millisSinceStart()).isEqualTo(Long.MAX_VALUE);
	}
}
