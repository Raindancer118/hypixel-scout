package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The overlay is read at a glance mid-fight, so the colours carry as much as the numbers do. */
class StatFormatTest {
	@Test
	void coloursTheStarByPrestige() {
		assertEquals("§7[12✴]", StatFormat.star(12));
		assertEquals("§f[134✴]", StatFormat.star(134));
		assertEquals("§6[212✴]", StatFormat.star(212));
		assertEquals("§b[301✴]", StatFormat.star(301));
	}

	@Test
	void givesAThousandStarPlayerTheRainbowBracket() {
		String rendered = StatFormat.star(1000);

		assertTrue(rendered.startsWith("§c["), "the rainbow prestige opens in red");
		assertTrue(rendered.contains("1000") || rendered.contains("0§"),
				"every digit is coloured separately: " + rendered);
	}

	@Test
	void warnsAboveAThreateningRatio() {
		assertEquals("§a", StatFormat.ratioColour(0.8));
		assertEquals("§c", StatFormat.ratioColour(5.0));
		assertEquals("§4", StatFormat.ratioColour(12.0));
	}

	@Test
	void roundsRatiosToTwoPlaces() {
		assertEquals("8.31", StatFormat.ratio(8.3142));
		assertEquals("0.00", StatFormat.ratio(0.0));
	}

	@Test
	void shortensLargeCounts() {
		assertEquals("412", StatFormat.count(412));
		assertEquals("41.2k", StatFormat.count(41234));
		assertEquals("1.2M", StatFormat.count(1234567));
	}

	@Test
	void showsAHiddenWinstreakAsUnknownRatherThanZero() {
		assertEquals("?", StatFormat.winstreak(null));
		assertEquals("27", StatFormat.winstreak(Integer.valueOf(27)));
	}

	@Test
	void reportsAccountAgeInDays() {
		long day = 86_400_000L;
		assertEquals("30d", StatFormat.age(1000 * day, 1030 * day));
		assertEquals("?", StatFormat.age(0L, 1030 * day), "a hidden first login is not an age");
	}
}
