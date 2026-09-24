package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BlastTest {
	private static final FlightPath.Obstacle OPEN_AIR = (from, to) -> null;
	/** A floor: the top of the blocks at y 64. */
	private static final FlightPath.Obstacle FLOOR = (from, to) -> {
		if (to.y() >= 64 || from.y() < 64) {
			return null;
		}
		double t = (from.y() - 64) / (from.y() - to.y());
		return from.add(to.subtract(from).scale(t));
	};

	private static Box standingAt(double x, double y, double z) {
		return new Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
	}

	@Test
	void outInTheOpenTheBlastSeesAllOfMe() {
		assertThat(Blast.exposure(standingAt(3, 64, 0), new Vec(0, 64.06, 0), OPEN_AIR)).isEqualTo(1.0);
	}

	@Test
	void behindAWallItSeesNothing() {
		FlightPath.Obstacle wall = (from, to) -> (from.x() - 1.5) * (to.x() - 1.5) < 0 ? from : null;
		assertThat(Blast.exposure(standingAt(3, 64, 0), new Vec(0, 64.06, 0), wall)).isZero();
	}

	@Test
	void theKnockbackPushesAwayFromTheBlastAndFadesWithDistance() {
		Vec center = new Vec(0, 64, 0);
		// Four blocks away of a power-4 blast's eight: half strength.
		Blast.Knock knock = Blast.knockback(center, 4.0, new Vec(4, 64, 0), new Vec(4, 65.62, 0), 1.0);

		assertThat(knock.strength()).isCloseTo(0.5, within(1e-9));
		assertThat(knock.push().x()).isPositive();
		assertThat(knock.push().length()).isCloseTo(0.5, within(1e-9));
		// Measured to the eyes: a blast at the feet throws upwards too.
		assertThat(knock.push().y()).isPositive();
	}

	@Test
	void outOfReachIsNoKnockback() {
		assertThat(Blast.knockback(new Vec(0, 64, 0), 4.0, new Vec(9, 64, 0), new Vec(9, 65.62, 0), 1.0)).isNull();
	}

	@Test
	void coverWeakensTheKnockback() {
		Blast.Knock knock = Blast.knockback(new Vec(0, 64, 0), 4.0, new Vec(4, 64, 0), new Vec(4, 65.62, 0), 0.5);
		assertThat(knock.strength()).isCloseTo(0.25, within(1e-9));
	}

	@Test
	void primedTntFallsToTheFloorAndBlowsUpThere() {
		// Dropped two blocks above the floor with a second and a half left.
		Blast.Tnt tnt = Blast.tnt(new Vec(0, 66, 0), Vec.ZERO, 30, FLOOR);

		assertThat(tnt.center().y()).isCloseTo(64 + 0.98 * 0.0625, within(1e-6));
		assertThat(tnt.path().getLast().y()).isCloseTo(64, within(1e-6));
	}

	@Test
	void tntThatIsAboutToBlowUpStaysWhereItIs() {
		Blast.Tnt tnt = Blast.tnt(new Vec(0, 70, 0), Vec.ZERO, 1, FLOOR);
		// One tick of falling: 0.04 down.
		assertThat(tnt.center().y()).isCloseTo(70 - 0.04 + 0.98 * 0.0625, within(1e-6));
	}

	@Test
	void tntKeepsItsThrow() {
		Blast.Tnt tnt = Blast.tnt(new Vec(0, 64, 0), new Vec(0.5, 0, 0), 5, FLOOR);
		// Sliding along the floor: moves, slower each tick.
		assertThat(tnt.center().x()).isBetween(0.5, 2.5);
	}
}
