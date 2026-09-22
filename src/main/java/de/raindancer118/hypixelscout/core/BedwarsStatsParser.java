package de.raindancer118.hypixelscout.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a {@code /v2/player} response into {@link PlayerStats}.
 *
 * <p>Nothing here assumes a field exists. Hypixel lets a player switch off every statistic
 * individually, a new account has none of them, and the whole {@code player} object is null for
 * anyone Hypixel has never seen — which, for somebody standing in a Hypixel lobby, means a nick.
 */
public final class BedwarsStatsParser {
	private BedwarsStatsParser() {
	}

	public static PlayerStats parse(JsonObject response, String observedName) {
		return parse(response, observedName, null);
	}

	public static PlayerStats parse(JsonObject response, String observedName, UUID uuid) {
		if (!bool(response, "success")) {
			throw new HypixelApiException(string(response, "cause", "Hypixel refused the request"));
		}

		JsonObject player = object(response, "player");
		if (player == null) {
			return PlayerStats.nicked(observedName, uuid);
		}

		// Hypixel hands out a stranger's profile for some nicks, so the name is checked too: a
		// profile that does not answer to the name on the nametag is not this player's profile.
		String displayName = string(player, "displayname", null);
		if (displayName != null && !displayName.equalsIgnoreCase(observedName)) {
			return PlayerStats.nicked(observedName, uuid);
		}

		JsonObject bedwars = object(object(player, "stats"), "Bedwars");

		return PlayerStats.builder(observedName, uuid)
				.stars(number(object(player, "achievements"), "bedwars_level", 0).intValue())
				.finals(number(bedwars, "final_kills_bedwars", 0).intValue(),
						number(bedwars, "final_deaths_bedwars", 0).intValue())
				.games(number(bedwars, "wins_bedwars", 0).intValue(),
						number(bedwars, "losses_bedwars", 0).intValue())
				.beds(number(bedwars, "beds_broken_bedwars", 0).intValue(),
						number(bedwars, "beds_lost_bedwars", 0).intValue())
				.kills(number(bedwars, "kills_bedwars", 0).intValue(),
						number(bedwars, "deaths_bedwars", 0).intValue())
				.karma(number(player, "karma", 0).intValue())
				.socials(socials(player))
				.winstreak(optionalInt(bedwars, "winstreak"))
				.networkLevel(networkLevel(number(player, "networkExp", 0).doubleValue()))
				.logins(number(player, "firstLogin", 0).longValue(),
						number(player, "lastLogin", 0).longValue())
				.rank(rank(player))
				.build();
	}

	/**
	 * The links a player chose to publish. Hypixel nests them one level deeper than the rest of
	 * the profile and omits the whole branch for anybody who has set none.
	 */
	private static Map<String, String> socials(JsonObject player) {
		Map<String, String> links = new LinkedHashMap<String, String>();
		JsonObject published = object(object(player, "socialMedia"), "links");
		if (published == null) {
			return links;
		}

		for (Map.Entry<String, JsonElement> link : published.entrySet()) {
			if (link.getValue() != null && !link.getValue().isJsonNull()) {
				links.put(link.getKey(), link.getValue().getAsString());
			}
		}

		return links;
	}

	/**
	 * Hypixel's published level curve: each level costs 2500 experience more than the last, so the
	 * level for a given total is the positive root of that quadratic.
	 */
	public static double networkLevel(double networkExp) {
		return (Math.sqrt(2 * networkExp + 30625) / 50) - 2.5;
	}

	/**
	 * The rank a player actually wears. {@code monthlyPackageRank} is the MVP++ subscription and
	 * outranks the purchased rank; {@code rank} carries staff and YouTube, and reads NORMAL for
	 * everyone else.
	 */
	private static String rank(JsonObject player) {
		String staff = string(player, "rank", null);
		if (staff != null && !"NORMAL".equals(staff)) {
			return staff;
		}

		String monthly = string(player, "monthlyPackageRank", null);
		if (monthly != null && !"NONE".equals(monthly)) {
			return monthly;
		}

		String purchased = string(player, "newPackageRank", null);
		return purchased == null || "NONE".equals(purchased) ? null : purchased;
	}

	private static Integer optionalInt(JsonObject parent, String key) {
		JsonElement element = element(parent, key);
		return element == null ? null : Integer.valueOf(element.getAsInt());
	}

	private static Number number(JsonObject parent, String key, Number fallback) {
		JsonElement element = element(parent, key);
		return element == null ? fallback : element.getAsNumber();
	}

	private static String string(JsonObject parent, String key, String fallback) {
		JsonElement element = element(parent, key);
		return element == null ? fallback : element.getAsString();
	}

	private static boolean bool(JsonObject parent, String key) {
		JsonElement element = element(parent, key);
		return element != null && element.getAsBoolean();
	}

	private static JsonObject object(JsonObject parent, String key) {
		JsonElement element = element(parent, key);
		return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
	}

	/** A key that is present but null is the same as an absent one everywhere in this response. */
	private static JsonElement element(JsonObject parent, String key) {
		if (parent == null) {
			return null;
		}

		JsonElement element = parent.get(key);
		return element == null || element.isJsonNull() ? null : element;
	}
}
