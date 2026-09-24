package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LockWatchTest {
	private static final FlightPath.Obstacle OPEN_AIR = (from, to) -> null;

	/** The player: standing at the origin, eyes at 1.62, looking along +z. */
	private static final Box SELF = new Box(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
	private static final Vec EYE = new Vec(0, 1.62, 0);
	private static final Vec LOOK = new Vec(0, 0, 1);
	private static final double FOV_COS = Math.cos(Math.toRadians(45));

	private final LockWatch watch = new LockWatch();

	/** Somebody holding a fire charge at {@code eye}, looking at {@code target}. */
	private static LockWatch.Aimer aimer(int id, String name, Vec eye, Vec target, FlightPath.Obstacle obstacle) {
		Vec look = target.subtract(eye).normalize();
		return new LockWatch.Aimer(id, name, eye,
				FlightPath.predict(ProjectileKind.FIREBALL, eye, look, 0.1, 80, obstacle));
	}

	private static LockWatch.Aimer aimer(int id, Vec eye, Vec target) {
		return aimer(id, "Enemy" + id, eye, target, OPEN_AIR);
	}

	private Optional<LockWatch.Lock> update(LockWatch.Aimer... aimers) {
		return watch.update(List.of(aimers), SELF, EYE, LOOK, FOV_COS);
	}

	@Test
	void somebodyAimingAFireChargeAtMeHasMeLocked() {
		Optional<LockWatch.Lock> lock = update(aimer(1, new Vec(0, 1.62, 20), new Vec(0, 1, 0)));

		assertThat(lock).isPresent();
		assertThat(lock.get().name()).isEqualTo("Enemy1");
		assertThat(lock.get().ticks()).isPositive();
	}

	@Test
	void thePointTheyAimAtIsWhereTheFireballWouldReachMe() {
		LockWatch.Lock lock = update(aimer(1, new Vec(0, 1.62, 20), new Vec(0, 1, 0))).orElseThrow();

		// Straight along z at my chest height: on my front, not somewhere in the air before me and
		// not at the wall behind.
		assertThat(SELF.inflate(0.01).contains(lock.point())).isTrue();
		assertThat(lock.point().z()).isCloseTo(0.3, within(0.01));
		assertThat(lock.blast()).isFalse();
	}

	@Test
	void aimingAtTheFloorBesideMeIsALockOnTheBlast() {
		// Two blocks beside my feet: it misses me, and the blast still throws me.
		FlightPath.Obstacle floor = (from, to) -> {
			if (to.y() > 0) {
				return null;
			}
			double t = from.y() / (from.y() - to.y());
			return from.add(to.subtract(from).scale(t));
		};
		Vec feet = new Vec(2.2, 0, 0);
		LockWatch.Lock lock = update(aimer(1, "Bomber", new Vec(0, 10, 20), feet, floor)).orElseThrow();

		assertThat(lock.blast()).isTrue();
		assertThat(lock.point().distanceTo(feet)).isLessThan(0.2);
	}

	@Test
	void aimingPastMeIsNoLock() {
		assertThat(update(aimer(1, new Vec(0, 1.62, 20), new Vec(5, 1, 0)))).isEmpty();
	}

	@Test
	void aimingSlightlyOffStillCounts() {
		// Head turns reach the client in steps of 1.4°: a hair beside me is still a lock.
		assertThat(update(aimer(1, new Vec(0, 1.62, 30), new Vec(0.6, 1, 0)))).isPresent();
	}

	@Test
	void aimedJustBesideMeTheSpotIsTheClosestTheFireballComes() {
		LockWatch.Lock lock = update(aimer(1, new Vec(0, 1.62, 30), new Vec(0.6, 1, 0))).orElseThrow();

		// It would graze me: marked where the path passes nearest, not a block out in front.
		assertThat(Math.abs(lock.point().z())).isLessThan(0.3);
	}

	@Test
	void aWallBetweenUsBreaksTheLock() {
		FlightPath.Obstacle wall = (from, to) -> {
			if (from.z() >= 10 && to.z() < 10) {
				double t = (from.z() - 10) / (from.z() - to.z());
				return from.add(to.subtract(from).scale(t));
			}
			return null;
		};
		assertThat(update(aimer(1, "Hidden", new Vec(0, 1.62, 20), new Vec(0, 1, 0), wall))).isEmpty();
	}

	@Test
	void tooFarForTheFireballToArriveIsNoLock() {
		assertThat(update(aimer(1, new Vec(0, 1.62, 110), new Vec(0, 1, 0)))).isEmpty();
	}

	@Test
	void somebodyRightInFrontOfMeIsNoLock() {
		// Two blocks ahead in plain view: I see the fire charge, a tone would only nag in a fight.
		assertThat(update(aimer(1, new Vec(0, 1.62, 2), new Vec(0, 1, 0)))).isEmpty();
	}

	@Test
	void somebodyRightBehindMeIsALock() {
		assertThat(update(aimer(1, new Vec(0, 1.62, -2.5), new Vec(0, 1, 0)))).isPresent();
	}

	@Test
	void theClosestToFiringIsTheOneShown() {
		LockWatch.Lock lock = update(
				aimer(1, new Vec(0, 1.62, 40), new Vec(0, 1, 0)),
				aimer(2, new Vec(15, 1.62, 0), new Vec(0, 1, 0))).orElseThrow();

		assertThat(lock.id()).isEqualTo(2);
	}

	@Test
	void whereTheyStandIsTheAngleFromMyView() {
		LockWatch.Lock left = update(aimer(1, new Vec(20, 1.62, 0), new Vec(0, 1, 0))).orElseThrow();
		assertThat(left.bearing(EYE, LOOK)).isBetween(-95.0, -85.0);
	}
}
