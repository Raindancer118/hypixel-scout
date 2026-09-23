package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BoxTest {
	private final Box unit = new Box(0, 0, 0, 1, 1, 1);

	@Test
	void aSegmentThroughTheBoxEntersWhereItCrossesTheFirstFace() {
		assertThat(unit.entry(new Vec(-1, 0.5, 0.5), new Vec(3, 0.5, 0.5))).isCloseTo(0.25, within(1e-9));
	}

	@Test
	void aSegmentThatMissesOrStopsShortDoesNotEnter() {
		assertThat(unit.entry(new Vec(-1, 2, 0.5), new Vec(3, 2, 0.5))).isNegative();
		assertThat(unit.entry(new Vec(-3, 0.5, 0.5), new Vec(-1, 0.5, 0.5))).isNegative();
		// Parallel to a face and outside it.
		assertThat(unit.entry(new Vec(-1, 1.5, 0.5), new Vec(3, 1.5, 0.5))).isNegative();
	}

	@Test
	void aSegmentStartingInsideEntersAtOnce() {
		assertThat(unit.entry(new Vec(0.5, 0.5, 0.5), new Vec(5, 5, 5))).isZero();
	}

	@Test
	void inflatingGrowsEveryFace() {
		Box grown = unit.inflate(0.5);
		assertThat(grown).isEqualTo(new Box(-0.5, -0.5, -0.5, 1.5, 1.5, 1.5));
	}

	@Test
	void theDistanceIsZeroInsideAndToTheNearestFaceOutside() {
		assertThat(unit.distanceTo(new Vec(0.5, 0.5, 0.5))).isZero();
		assertThat(unit.distanceTo(new Vec(3, 0.5, 0.5))).isCloseTo(2.0, within(1e-9));
		assertThat(unit.distanceTo(new Vec(4, 5, 0.5))).isCloseTo(5.0, within(1e-9));
	}
}
