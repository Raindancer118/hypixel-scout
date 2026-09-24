package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FallTest {
	private static final FlightPath.Obstacle OPEN_AIR = (from, to) -> null;
	private static final double VOID = 0.0;

	/** A platform at y 64 (top face) from x -2 to 2, z -2 to 2, nothing else. */
	private static final FlightPath.Obstacle PLATFORM = (from, to) -> {
		if (from.y() < 64 || to.y() >= 64) {
			return null;
		}
		double t = (from.y() - 64) / (from.y() - to.y());
		Vec hit = from.add(to.subtract(from).scale(t));
		return Math.abs(hit.x()) <= 2 && Math.abs(hit.z()) <= 2 ? hit : null;
	};

	@Test
	void droppingOntoThePlatformLandsOnIt() {
		Fall fall = Fall.predict(new Vec(0, 70, 0), Vec.ZERO, 0.3, PLATFORM, VOID, 200);

		assertThat(fall.landing()).isNotNull();
		assertThat(fall.landing().y()).isCloseTo(64, within(1e-6));
		assertThat(fall.intoTheVoid()).isFalse();
		assertThat(fall.ticks()).isPositive();
	}

	@Test
	void flyingOffTheEdgeIsNoSafeLanding() {
		// Knocked sideways at speed: past the platform's edge before it comes up.
		Fall fall = Fall.predict(new Vec(0, 66, 0), new Vec(1.5, 0.4, 0), 0.3, PLATFORM, VOID, 200);

		assertThat(fall.landing()).isNull();
		assertThat(fall.intoTheVoid()).isTrue();
	}

	@Test
	void anyCornerOfTheFeetOnTheEdgeCatchesMe() {
		// Standing so that only the edge of the hitbox is over the platform.
		Fall fall = Fall.predict(new Vec(2.25, 66, 0), Vec.ZERO, 0.3, PLATFORM, VOID, 200);
		assertThat(fall.landing()).isNotNull();
	}

	@Test
	void airDragAndGravityAreThePlayersOwn() {
		// LivingEntity.travel in air: move, then y (v - 0.08) · 0.98 and x, z · 0.91.
		Fall fall = Fall.predict(new Vec(0, 100, 0), new Vec(1, 0, 0), 0.3, OPEN_AIR, VOID, 2);
		assertThat(fall.points().get(1)).isEqualTo(new Vec(1, 100, 0));
		assertThat(fall.points().get(2).x()).isCloseTo(1.91, within(1e-9));
		assertThat(fall.points().get(2).y()).isCloseTo(100 - 0.08 * 0.98, within(1e-9));
	}

	@Test
	void aWallStopsTheSidewaysMotionNotTheFall() {
		FlightPath.Obstacle wallAndFloor = (from, to) -> {
			if (from.x() < 1 && to.x() >= 1) {
				double t = (1 - from.x()) / (to.x() - from.x());
				return from.add(to.subtract(from).scale(t));
			}
			return PLATFORM.clip(from, to);
		};
		Fall fall = Fall.predict(new Vec(0, 70, 0), new Vec(0.6, 0, 0), 0.3, wallAndFloor, VOID, 200);

		assertThat(fall.landing()).isNotNull();
		assertThat(fall.landing().x()).isLessThan(1.0);
	}
}
