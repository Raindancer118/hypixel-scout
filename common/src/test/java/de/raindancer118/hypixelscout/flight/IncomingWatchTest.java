package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IncomingWatchTest {
	private static final FlightPath.Obstacle OPEN_AIR = (from, to) -> null;

	/** The player: standing at the origin, eyes at 1.62, looking along +z. */
	private static final Box SELF = new Box(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
	private static final Vec EYE = new Vec(0, 1.62, 0);
	private static final Vec LOOK = new Vec(0, 0, 1);
	/** About a 90° wide view. */
	private static final double FOV_COS = Math.cos(Math.toRadians(45));

	private final IncomingWatch watch = new IncomingWatch();

	private static IncomingWatch.Seen fireball(int id, Vec at, Vec towards, boolean mine) {
		Vec velocity = towards.subtract(at).normalize().scale(1.0);
		return new IncomingWatch.Seen(id, ProjectileKind.FIREBALL, at,
				FlightPath.predict(ProjectileKind.FIREBALL, at, velocity, 0.1, 80, OPEN_AIR), mine);
	}

	private static IncomingWatch.Seen arrow(int id, Vec at, Vec velocity) {
		return new IncomingWatch.Seen(id, ProjectileKind.ARROW, at,
				FlightPath.predict(ProjectileKind.ARROW, at, velocity, 0, 80, OPEN_AIR), false);
	}

	private Optional<IncomingWatch.Warning> update(IncomingWatch.Seen... seen) {
		return watch.update(List.of(seen), SELF, EYE, LOOK, FOV_COS);
	}

	@Test
	void aFireballFromAfarStraightAtMeIsAWarning() {
		Optional<IncomingWatch.Warning> warning = update(fireball(1, new Vec(0, 1, 30), new Vec(0, 1, 0), false));

		assertThat(warning).isPresent();
		assertThat(warning.get().kind()).isEqualTo(ProjectileKind.FIREBALL);
		assertThat(warning.get().ticks()).isPositive();
		assertThat(warning.get().fresh()).isTrue();
		// Still coming: the same warning, no longer new.
		assertThat(update(fireball(1, new Vec(0, 1, 28), new Vec(0, 1, 0), false)).orElseThrow().fresh()).isFalse();
	}

	@Test
	void aFireballThatPassesByIsNone() {
		assertThat(update(fireball(1, new Vec(10, 1, 30), new Vec(10, 1, 0), false))).isEmpty();
	}

	@Test
	void aFireballThrownRightInFrontOfMeIsNoWarning() {
		// Two blocks ahead, in plain view: whoever threw it is in my face, and I see it anyway.
		assertThat(update(fireball(1, new Vec(0, 1.5, 2), new Vec(0, 1, 0), false))).isEmpty();
		// And it stays that way as it comes closer.
		assertThat(update(fireball(1, new Vec(0, 1.5, 1), new Vec(0, 1, 0), false))).isEmpty();
	}

	@Test
	void aFireballThrownCloseBehindMeIsStillAWarning() {
		// Close, but out of sight: exactly the one that knocks you off the bridge.
		assertThat(update(fireball(1, new Vec(0, 1.5, -2), new Vec(0, 1, 0), false))).isPresent();
	}

	@Test
	void aFireballSeenFarAwayFirstIsNotExcusedLaterByComingClose() {
		assertThat(update(fireball(1, new Vec(0, 1, 20), new Vec(0, 1, 0), false))).isPresent();
		assertThat(update(fireball(1, new Vec(0, 1, 2), new Vec(0, 1, 0), false))).isPresent();
	}

	@Test
	void myOwnFireballIsNeverAWarning() {
		assertThat(update(fireball(1, new Vec(0, 1, -20), new Vec(0, 1, 0), true))).isEmpty();
	}

	@Test
	void aFireballThatBlowsUpRightBesideMeIsAWarning() {
		// It hits the ground a block in front of my feet: the blast still throws me.
		FlightPath.Obstacle floor = (from, to) -> {
			if (to.y() > 0) {
				return null;
			}
			double t = from.y() / (from.y() - to.y());
			return from.add(to.subtract(from).scale(t));
		};
		Vec at = new Vec(0, 12, 20);
		Vec target = new Vec(0, 0, 1.5);
		IncomingWatch.Seen seen = new IncomingWatch.Seen(1, ProjectileKind.FIREBALL, at,
				FlightPath.predict(ProjectileKind.FIREBALL, at, target.subtract(at).normalize(), 0.1, 80, floor), false);

		assertThat(seen.path().blocked()).isTrue();
		assertThat(update(seen)).isPresent();
	}

	@Test
	void anArrowArcsIntoMe() {
		// Shot from 20 blocks away, aimed a little high: gravity brings it down onto me.
		Optional<IncomingWatch.Warning> warning = update(arrow(1, new Vec(0, 1.5, 20), new Vec(0, 0.15, -2.5)));

		assertThat(warning).isPresent();
		assertThat(warning.get().kind()).isEqualTo(ProjectileKind.ARROW);
	}

	@Test
	void anArrowThatFallsShortIsNone() {
		assertThat(update(arrow(1, new Vec(0, 1.5, 40), new Vec(0, -0.3, -1.0)))).isEmpty();
	}

	@Test
	void theSoonestOfSeveralIsTheOneShown() {
		Optional<IncomingWatch.Warning> warning = update(
				fireball(1, new Vec(0, 1, 40), new Vec(0, 1, 0), false),
				fireball(2, new Vec(30, 1, 0), new Vec(0, 1, 0), false));

		assertThat(warning.orElseThrow().id()).isEqualTo(2);
	}

	@Test
	void aProjectileGoneIsForgotten() {
		update(fireball(1, new Vec(0, 1.5, 2), new Vec(0, 1, 0), false));
		update();
		// The id comes back as a new fireball far away: it is judged afresh.
		assertThat(update(fireball(1, new Vec(0, 1, 30), new Vec(0, 1, 0), false))).isPresent();
	}

	@Test
	void whereItComesFromIsTheAngleFromMyViewInDegrees() {
		// From the left: looking along +z, left is +x in Minecraft.
		IncomingWatch.Warning left = update(fireball(1, new Vec(20, 1, 0), new Vec(0, 1, 0), false)).orElseThrow();
		assertThat(left.bearing(EYE, LOOK)).isBetween(-95.0, -85.0);

		IncomingWatch.Warning behind = new IncomingWatch().update(
				List.of(fireball(2, new Vec(0, 1, -20), new Vec(0, 1, 0), false)), SELF, EYE, LOOK, FOV_COS).orElseThrow();
		assertThat(Math.abs(behind.bearing(EYE, LOOK))).isGreaterThan(170.0);
	}
}
