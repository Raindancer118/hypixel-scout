package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProximityWatchTest {
	private static final UUID SUNDIAL = UUID.randomUUID();
	private static final UUID ORCHARD = UUID.randomUUID();
	private static final UUID MOSSY = UUID.randomUUID();
	private static final UUID CINDER = UUID.randomUUID();

	private long now;
	private ProximityWatch watch;

	@BeforeEach
	void setUp() {
		now = 1_000_000;
		watch = new ProximityWatch(() -> now);
	}

	private void see(ProximityWatch.Sighting... sightings) {
		watch.observe(List.of(sightings), 12, 4_000);
	}

	private static ProximityWatch.Sighting at(UUID uuid, String name, double distance) {
		return new ProximityWatch.Sighting(uuid, name, distance);
	}

	private List<String> shown() {
		return watch.showing().stream().map(ProximityWatch.Popup::name).toList();
	}

	@Test
	void somebodyWalkingIntoTheRadiusPopsUp() {
		see(at(SUNDIAL, "Sundial", 30));
		assertThat(shown()).isEmpty();

		see(at(SUNDIAL, "Sundial", 11));
		assertThat(shown()).containsExactly("Sundial");
	}

	@Test
	void thePopupGoesAwayAfterItsTime() {
		see(at(SUNDIAL, "Sundial", 5));
		now += 3_999;
		assertThat(shown()).containsExactly("Sundial");

		now += 2;
		assertThat(shown()).isEmpty();
	}

	@Test
	void stayingInsideDoesNotPopUpAgain() {
		see(at(SUNDIAL, "Sundial", 5));
		now += 60_000;
		see(at(SUNDIAL, "Sundial", 6));

		assertThat(shown()).isEmpty();
	}

	@Test
	void pacingAlongTheEdgeDoesNotFlicker() {
		see(at(SUNDIAL, "Sundial", 11.5));
		now += 60_000;
		// Just outside, then back in: not far enough out to count as having left.
		see(at(SUNDIAL, "Sundial", 12.5));
		see(at(SUNDIAL, "Sundial", 11.5));

		assertThat(shown()).isEmpty();
	}

	@Test
	void comingBackAfterReallyLeavingPopsUpAgainButNotStraightAway() {
		see(at(SUNDIAL, "Sundial", 5));
		see(at(SUNDIAL, "Sundial", 40));
		now += 5_000;
		see(at(SUNDIAL, "Sundial", 5));
		assertThat(shown()).as("too soon after the last one").isEmpty();

		see(at(SUNDIAL, "Sundial", 40));
		now += ProximityWatch.COOLDOWN_MILLIS;
		see(at(SUNDIAL, "Sundial", 5));
		assertThat(shown()).containsExactly("Sundial");
	}

	@Test
	void somebodyWhoDisappearedAndCameBackCountsAsNew() {
		see(at(SUNDIAL, "Sundial", 5));
		see();
		now += ProximityWatch.COOLDOWN_MILLIS;
		see(at(SUNDIAL, "Sundial", 5));

		assertThat(shown()).containsExactly("Sundial");
	}

	@Test
	void theNewestIsOnTopAndOnlyAFewAreShown() {
		see(at(SUNDIAL, "Sundial", 5));
		now += 100;
		see(at(SUNDIAL, "Sundial", 5), at(ORCHARD, "Orchard", 5));
		now += 100;
		see(at(SUNDIAL, "Sundial", 5), at(ORCHARD, "Orchard", 5), at(MOSSY, "mossy", 5));
		now += 100;
		see(at(SUNDIAL, "Sundial", 5), at(ORCHARD, "Orchard", 5), at(MOSSY, "mossy", 5), at(CINDER, "Cinderling", 5));

		assertThat(shown()).containsExactly("Cinderling", "mossy", "Orchard");
	}

	@Test
	void aResetForgetsEverything() {
		see(at(SUNDIAL, "Sundial", 5));
		watch.reset();
		assertThat(shown()).isEmpty();

		see(at(SUNDIAL, "Sundial", 5));
		assertThat(shown()).containsExactly("Sundial");
	}
}
