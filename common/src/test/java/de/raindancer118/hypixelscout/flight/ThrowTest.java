package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ThrowTest {
	@Test
	void aBowDrawnLongerShootsHarderUpToASecond() {
		// BowItem.getPowerForTime: (f² + 2f) / 3 with f in seconds, at most 1.
		assertThat(Throw.bowPower(0)).isZero();
		assertThat(Throw.bowPower(10)).isCloseTo((0.25 + 1.0) / 3, within(1e-6));
		assertThat(Throw.bowPower(20)).isEqualTo(1.0);
		assertThat(Throw.bowPower(60)).isEqualTo(1.0);
	}

	@Test
	void tooShortADrawShootsNothing() {
		assertThat(Throw.bowShoots(2)).isFalse();
		assertThat(Throw.bowShoots(5)).isTrue();
	}

	@Test
	void theThrowerCarriesTheirOwnMovementIntoIt() {
		Vec look = new Vec(0, 0, 1);
		// Running along x on the ground: sideways movement added, falling not.
		Vec onGround = Throw.velocity(look, Throw.PEARL_SPEED, new Vec(0.2, -0.08, 0), true);
		assertThat(onGround.x()).isCloseTo(0.2, within(1e-9));
		assertThat(onGround.y()).isCloseTo(0.0, within(1e-9));
		assertThat(onGround.z()).isCloseTo(1.5, within(1e-9));

		Vec jumping = Throw.velocity(look, Throw.PEARL_SPEED, new Vec(0, 0.3, 0), false);
		assertThat(jumping.y()).isCloseTo(0.3, within(1e-9));
	}

	@Test
	void itLeavesJustBelowTheEyes() {
		Vec start = Throw.start(new Vec(1, 65.62, 2));
		assertThat(start.x()).isEqualTo(1.0);
		assertThat(start.y()).isCloseTo(65.52, within(1e-9));
		assertThat(start.z()).isEqualTo(2.0);
	}
}
