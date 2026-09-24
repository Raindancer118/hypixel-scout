package de.raindancer118.hypixelscout.flight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EdgeTest {
	private static final int W = 400;
	private static final int H = 200;
	private static final int M = 10;

	@Test
	void aheadIsTheTopBehindTheBottom() {
		assertThat(Edge.place(0, W, H, M)).containsExactly(200.0, 10.0);
		assertThat(Edge.place(180, W, H, M)[1]).isCloseTo(190.0, within(1e-6));
		assertThat(Edge.place(180, W, H, M)[0]).isCloseTo(200.0, within(1e-6));
	}

	@Test
	void theSidesAreTheSides() {
		assertThat(Edge.place(90, W, H, M)).containsExactly(390.0, 100.0);
		assertThat(Edge.place(-90, W, H, M)).containsExactly(10.0, 100.0);
	}

	@Test
	void inBetweenItStaysOnTheFrame() {
		double[] at = Edge.place(135, W, H, M);
		boolean onFrame = Math.abs(at[0] - 390) < 1e-6 || Math.abs(at[1] - 190) < 1e-6;
		assertThat(onFrame).isTrue();
		assertThat(at[0]).isGreaterThan(200);
		assertThat(at[1]).isGreaterThan(100);
	}
}
