package de.raindancer118.hypixelscout.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TablePlacementTest {
	@Test
	void aTopLeftPlacementIsMeasuredFromTheTopLeft() {
		TablePlacement placement = new TablePlacement(TableAnchor.TOP_LEFT, 0.01, 0.02);

		assertThat(placement.x(1000, 200)).isEqualTo(10);
		assertThat(placement.y(500, 100)).isEqualTo(10);
	}

	@Test
	void aBottomRightPlacementStaysInTheCornerWhenTheWindowGrows() {
		TablePlacement placement = new TablePlacement(TableAnchor.BOTTOM_RIGHT, 0, 0);

		assertThat(placement.x(1000, 200)).isEqualTo(800);
		assertThat(placement.x(2000, 200)).isEqualTo(1800);
		assertThat(placement.y(600, 100)).isEqualTo(500);
	}

	@Test
	void theTableNeverLeavesTheScreen() {
		TablePlacement placement = new TablePlacement(TableAnchor.TOP_LEFT, 5.0, -5.0);

		assertThat(placement.x(1000, 200)).isEqualTo(800);
		assertThat(placement.y(500, 100)).isZero();
	}

	@Test
	void aTableWiderThanTheScreenIsPinnedLeft() {
		assertThat(TablePlacement.DEFAULT.x(100, 300)).isZero();
	}

	@Test
	void droppingItSomewhereRoundTripsToTheSamePixel() {
		for (int x : new int[] {0, 37, 400, 780}) {
			for (int y : new int[] {0, 55, 200, 380}) {
				TablePlacement dropped = TablePlacement.fromTopLeft(1000, 500, 220, 120, x, y);

				assertThat(dropped.x(1000, 220)).isEqualTo(x);
				assertThat(dropped.y(500, 120)).isEqualTo(y);
			}
		}
	}

	@Test
	void droppingItInACornerAdoptsThatCornerAsItsAnchor() {
		assertThat(TablePlacement.fromTopLeft(1000, 500, 100, 50, 890, 440).anchor())
				.isEqualTo(TableAnchor.BOTTOM_RIGHT);
		assertThat(TablePlacement.fromTopLeft(1000, 500, 100, 50, 450, 5).anchor())
				.isEqualTo(TableAnchor.TOP_CENTER);
	}

	@Test
	void snapPullsOntoTheNearestGuideWithinTheThreshold() {
		int[] guides = {0, 100, 200};

		assertThat(TablePlacement.snap(97, guides, 4)).isEqualTo(100);
		assertThat(TablePlacement.snap(90, guides, 4)).isEqualTo(90);
	}
}
