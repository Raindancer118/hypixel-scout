package de.raindancer118.hypixelscout.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parser is the one piece that has to survive Hypixel changing its mind: every field it reads
 * is optional on the wire, because a player can switch each of them off in their API settings.
 */
class BedwarsStatsParserTest {
	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	@Test
	void readsStarsAndRatiosFromAFullProfile() {
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'Technoblade',"
				+ "'achievements':{'bedwars_level':412},"
				+ "'stats':{'Bedwars':{'final_kills_bedwars':8310,'final_deaths_bedwars':1000,"
				+ "'wins_bedwars':420,'losses_bedwars':100,'beds_broken_bedwars':1500,"
				+ "'winstreak':27}}}}".replace('\'', '"')), "Technoblade");

		assertFalse(stats.isNicked());
		assertEquals(412, stats.getStars());
		assertEquals(8.31, stats.getFkdr(), 0.001);
		assertEquals(4.2, stats.getWlr(), 0.001);
		assertEquals(1500, stats.getBedsBroken());
		assertEquals(Integer.valueOf(27), stats.getWinstreak());
	}

	@Test
	void treatsAMissingPlayerObjectAsANick() {
		// A UUID sitting in a Hypixel game that Hypixel has never seen cannot be a real account.
		PlayerStats stats = BedwarsStatsParser.parse(
				json("{\"success\":true,\"player\":null}"), "xX_Sn1per_Xx");

		assertTrue(stats.isNicked());
		assertEquals("xX_Sn1per_Xx", stats.getName());
	}

	@Test
	void treatsAMismatchedDisplaynameAsANick() {
		// Hypixel hands out a stranger's profile for a nicked player often enough that the name
		// has to be checked, not just the presence of the object.
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'SomeoneElse',"
				+ "'achievements':{'bedwars_level':3}}}".replace('\'', '"')), "xX_Sn1per_Xx");

		assertTrue(stats.isNicked());
	}

	@Test
	void survivesAProfileWithEveryOptionalFieldMissing() {
		// A brand new account, or one with the API settings turned off: no stats object at all.
		PlayerStats stats = BedwarsStatsParser.parse(
				json("{\"success\":true,\"player\":{\"displayname\":\"Fresh\"}}"), "Fresh");

		assertFalse(stats.isNicked());
		assertEquals(0, stats.getStars());
		assertEquals(0.0, stats.getFkdr(), 0.001);
		assertNull(stats.getWinstreak(), "a hidden winstreak is absent, not zero");
		assertEquals(0L, stats.getLastLogin());
	}

	@Test
	void countsAFlawlessPlayerAsTheirFinalKills() {
		// Dividing by zero deaths would render as Infinity; every overlay shows the kill count.
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'Fresh','stats':{'Bedwars':"
				+ "{'final_kills_bedwars':7,'final_deaths_bedwars':0}}}}".replace('\'', '"')),
				"Fresh");

		assertEquals(7.0, stats.getFkdr(), 0.001);
	}

	@Test
	void readsTheAccountAgeSignals() {
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'Alt','firstLogin':1600000000000,"
				+ "'lastLogin':1700000000000,'networkExp':50000,'karma':1234,"
				+ "'newPackageRank':'MVP_PLUS'}}".replace('\'', '"')), "Alt");

		assertEquals(1600000000000L, stats.getFirstLogin());
		assertEquals(1700000000000L, stats.getLastLogin());
		assertEquals("MVP_PLUS", stats.getRank());
	}

	@Test
	void readsTheCountsTheProfileCardShows() {
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'Technoblade','karma':91000,"
				+ "'stats':{'Bedwars':{'kills_bedwars':9000,'deaths_bedwars':4000,"
				+ "'beds_broken_bedwars':1500,'beds_lost_bedwars':700,"
				+ "'coins':12345,'games_played_bedwars':4200}}}}".replace('\'', '"')),
				"Technoblade");

		assertEquals(9000, stats.getKills());
		assertEquals(4000, stats.getDeaths());
		assertEquals(700, stats.getBedsLost());
		assertEquals(91000, stats.getKarma());
	}

	@Test
	void readsTheSocialLinksAPlayerHasPublished() {
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'Technoblade','socialMedia':{'links':"
				+ "{'DISCORD':'techno#0001','YOUTUBE':'https://youtube.com/technoblade'}}}}"
						.replace('\'', '"')), "Technoblade");

		assertEquals("techno#0001", stats.getSocials().get("DISCORD"));
		assertEquals("https://youtube.com/technoblade", stats.getSocials().get("YOUTUBE"));
	}

	@Test
	void hasAnEmptySocialMapRatherThanNullWhenThereAreNone() {
		PlayerStats stats = BedwarsStatsParser.parse(
				json("{\"success\":true,\"player\":{\"displayname\":\"Fresh\"}}"), "Fresh");

		assertNotNull(stats.getSocials());
		assertTrue(stats.getSocials().isEmpty());
	}

	@Test
	void rejectsAnUnsuccessfulResponse() {
		HypixelApiException thrown = assertThrows(HypixelApiException.class,
				() -> BedwarsStatsParser.parse(
						json("{\"success\":false,\"cause\":\"Invalid API key\"}"), "Anyone"));

		assertTrue(thrown.getMessage().contains("Invalid API key"));
	}

	@Test
	void aLookupWithoutAnObservedNameTakesTheProfilesOwnName() {
		// Looked up by UUID alone — the key check, or a player out of sight — there is no nametag
		// to compare against, so there is nothing to call a nick.
		PlayerStats stats = BedwarsStatsParser.parse(json("{"
				+ "'success':true,'player':{'displayname':'Technoblade',"
				+ "'achievements':{'bedwars_level':150}}}"), null);

		assertFalse(stats.isNicked());
		assertEquals("Technoblade", stats.getName());
		assertEquals(150, stats.getStars());
	}
}
