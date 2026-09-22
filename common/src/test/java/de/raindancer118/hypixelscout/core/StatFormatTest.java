package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The overlay is read at a glance mid-fight, so the colours carry as much as the numbers do. */
class StatFormatTest {
	@Test
	void coloursTheStarByPrestige() {
		assertEquals("§7[12✫]", StatFormat.star(12));
		assertEquals("§f[134✫]", StatFormat.star(134));
		assertEquals("§6[212✫]", StatFormat.star(212));
		assertEquals("§b[301✫]", StatFormat.star(301));
		assertEquals("§5[999✫]", StatFormat.star(999));
	}

	@Test
	void onlyTheThousandPrestigeIsTheRainbow() {
		assertEquals("§c[§61§e0§a0§b0§d✫§5]", StatFormat.star(1000));
		assertEquals("§c[§61§e0§a9§b9§d✫§5]", StatFormat.star(1099));
	}

	@Test
	void thePrimePrestigesKeepGreyBracketsAndChangeTheStar() {
		assertEquals("§7[§f1100§7✪]", StatFormat.star(1100));
		assertEquals("§7[§e1234§6✪§7]", StatFormat.star(1234));
		assertEquals("§7[§31502§9✪§7]", StatFormat.star(1502));
	}

	@Test
	void theLaterPrestigesColourEveryCharacterAsHypixelDoes() {
		assertEquals("§8[§72§f00§70✪§8]", StatFormat.star(2000));
		assertEquals("§f[2§e10§60⚝]", StatFormat.star(2100));
		assertEquals("§9[3§310§60✥§e]", StatFormat.star(3100));
	}

	@Test
	void pastTheLastPrestigeTheLastSchemeStays() {
		String rendered = StatFormat.star(12_345);

		assertTrue(rendered.startsWith("§9["), rendered);
		assertTrue(rendered.contains("✭"), rendered);
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
