package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The geometry behind "am I looking at that player", kept out of the renderer so it can be read. */
class AimTest {
	@Test
	void scoresSomethingStraightAheadAsAPerfectMatch() {
		assertEquals(1.0, Aim.alignment(0, 0, 0, 1, 0, 0, 40, 0, 0), 1e-9);
	}

	@Test
	void scoresSomethingBehindAsTheOpposite() {
		assertEquals(-1.0, Aim.alignment(0, 0, 0, 1, 0, 0, -40, 0, 0), 1e-9);
	}

	@Test
	void scoresARightAngleAsZero() {
		assertEquals(0.0, Aim.alignment(0, 0, 0, 1, 0, 0, 0, 0, 40), 1e-9);
	}

	@Test
	void doesNotCareHowFarAwayTheTargetIs() {
		// The same four degrees off centre has to count the same across a map as across a room,
		// or the tooltip would only ever pick the nearest player.
		double near = Aim.alignment(0, 0, 0, 1, 0, 0, 10, 0.7, 0);
		double far = Aim.alignment(0, 0, 0, 1, 0, 0, 100, 7.0, 0);

		assertEquals(near, far, 1e-9);
	}

	@Test
	void treatsATargetInsideTheEyeAsNoMatch() {
		assertEquals(-1.0, Aim.alignment(5, 5, 5, 1, 0, 0, 5, 5, 5), 1e-9,
				"a zero-length direction has no angle, and must not come out as a hit");
	}

	@Test
	void turnsADegreeLimitIntoTheCosineTheComparisonNeeds() {
		assertEquals(1.0, Aim.cosineOf(0.0), 1e-9);
		assertEquals(0.0, Aim.cosineOf(90.0), 1e-9);
		assertTrue(Aim.alignment(0, 0, 0, 1, 0, 0, 100, 3.0, 0) > Aim.cosineOf(4.0),
				"three degrees off centre is inside a four degree cone");
		assertFalse(Aim.alignment(0, 0, 0, 1, 0, 0, 100, 9.0, 0) > Aim.cosineOf(4.0),
				"five degrees off centre is outside it");
	}

	@Test
	void lookingAtTheHeadOfSomebodyCloseIsAHit() {
		// Eye at 1.62, looking straight ahead at a player 3.5 blocks away whose body spans 0..1.8:
		// the ray passes their head, so they are dead centre, however far their chest is below it.
		double alignment = Aim.bodyAlignment(0, 1.62, 0, 0, 0, 1, 0, 0, 1.8, 3.5);

		assertEquals(1.0, alignment, 1e-9);
	}

	@Test
	void aimingAboveSomebodyIsMeasuredToTheTopOfTheirHead() {
		// Looking level from 10 blocks up: the nearest part of the body is the top of the head.
		double alignment = Aim.bodyAlignment(0, 10, 0, 0, 0, 1, 0, 0, 1.8, 100);

		assertEquals(Aim.alignment(0, 10, 0, 0, 0, 1, 0, 1.8, 100), alignment, 1e-9);
	}

	@Test
	void somebodyBehindIsNeverAHit() {
		assertTrue(Aim.bodyAlignment(0, 1.62, 0, 0, 0, 1, 0, 0, 1.8, -5) < 0);
	}
}
