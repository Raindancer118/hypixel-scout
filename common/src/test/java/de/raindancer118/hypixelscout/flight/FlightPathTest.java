package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FlightPathTest {
	private static final FlightPath.Obstacle OPEN_AIR = (from, to) -> null;

	@Test
	void anArrowMovesThenSlowsThenFalls() {
		// Vanilla's order in AbstractArrow.tick: move by the velocity, drag 0.99, gravity 0.05.
		FlightPath path = FlightPath.predict(ProjectileKind.ARROW, new Vec(0, 100, 0), new Vec(3, 0, 0), 0, 3, OPEN_AIR);

		assertThat(path.points()).hasSize(4);
		assertThat(path.points().get(1).x()).isCloseTo(3.0, within(1e-9));
		assertThat(path.points().get(1).y()).isCloseTo(100.0, within(1e-9));
		assertThat(path.points().get(2).x()).isCloseTo(3.0 + 2.97, within(1e-9));
		assertThat(path.points().get(2).y()).isCloseTo(100.0 - 0.05, within(1e-9));
		assertThat(path.points().get(3).y()).isCloseTo(100.0 - 0.05 - (0.05 * 0.99 + 0.05), within(1e-9));
		assertThat(path.blocked()).isFalse();
	}

	@Test
	void aFireballFliesStraightAndSettlesAtItsTopSpeed() {
		Vec direction = new Vec(1, -0.5, 2).normalize();
		FlightPath path = FlightPath.predict(ProjectileKind.FIREBALL, Vec.ZERO, direction.scale(0.1), 0.1, 200, OPEN_AIR);

		for (Vec point : path.points().subList(1, path.points().size())) {
			assertThat(point.normalize().dot(direction)).isCloseTo(1.0, within(1e-9));
		}
		// (v + 0.1) × 0.95 settles where v = 0.095 / 0.05.
		Vec velocity = direction.scale(0.1);
		for (int tick = 0; tick < 500; tick++) {
			velocity = ProjectileKind.FIREBALL.nextVelocity(velocity, 0.1);
		}
		assertThat(velocity.length()).isCloseTo(1.9, within(1e-6));
	}

	@Test
	void aFireballWithoutAnyMotionYetHasNoPath() {
		FlightPath path = FlightPath.predict(ProjectileKind.FIREBALL, Vec.ZERO, Vec.ZERO, 0.1, 50, OPEN_AIR);

		assertThat(path.points()).containsExactly(Vec.ZERO);
	}

	@Test
	void thePathEndsWhereItHitsABlock() {
		// A wall at x = 10.
		FlightPath.Obstacle wall = (from, to) -> {
			if (to.x() < 10) {
				return null;
			}
			double t = (10 - from.x()) / (to.x() - from.x());
			return from.add(to.subtract(from).scale(t));
		};

		FlightPath path = FlightPath.predict(ProjectileKind.ARROW, new Vec(0, 64, 0), new Vec(3, 0, 0), 0, 50, wall);

		assertThat(path.blocked()).isTrue();
		assertThat(path.points().getLast().x()).isCloseTo(10.0, within(1e-9));
		assertThat(path.points()).hasSize(5);
	}

	@Test
	void thePathStopsAtTheReachLimit() {
		FlightPath path = FlightPath.predict(ProjectileKind.FIREBALL, Vec.ZERO, new Vec(0, 0, 2), 0.1, 10_000, OPEN_AIR);

		assertThat(path.points().getLast().length()).isLessThanOrEqualTo(FlightPath.MAX_REACH + 2.5);
		assertThat(path.blocked()).isFalse();
	}

	@Test
	void theTimeToABoxIsCountedInTicksAlongThePath() {
		FlightPath path = FlightPath.predict(ProjectileKind.FIREBALL, Vec.ZERO, new Vec(1, 0, 0), 0, 30, OPEN_AIR);
		// Inertia 0.95 with no acceleration: 0.95, 0.9025, … — the box sits across the path at x 2 to 3.
		Box box = new Box(2, -1, -1, 3, 1, 1);

		double ticks = path.ticksUntil(box);
		assertThat(ticks).isGreaterThan(2.0).isLessThan(3.0);
		assertThat(path.ticksUntil(new Box(2, 5, -1, 3, 6, 1))).isNegative();
	}

	@Test
	void aPathThatStartsInsideTheBoxHitsAtOnce() {
		FlightPath path = FlightPath.predict(ProjectileKind.ARROW, new Vec(0.5, 0.5, 0.5), new Vec(1, 0, 0), 0, 5, OPEN_AIR);

		assertThat(path.ticksUntil(new Box(0, 0, 0, 1, 1, 1))).isZero();
	}

	@Test
	void wherePathIsPartWayThroughATick() {
		FlightPath path = FlightPath.predict(ProjectileKind.FIREBALL, Vec.ZERO, new Vec(1, 0, 0), 0, 30, OPEN_AIR);
		Box box = new Box(2, -1, -1, 3, 1, 1);

		// Exactly where it enters the box: on its near face.
		assertThat(path.at(path.ticksUntil(box)).x()).isCloseTo(2.0, org.assertj.core.api.Assertions.within(1e-9));
		assertThat(path.at(0)).isEqualTo(Vec.ZERO);
		// Past the end it stays at the end.
		assertThat(path.at(1000)).isEqualTo(path.end());
	}
}
