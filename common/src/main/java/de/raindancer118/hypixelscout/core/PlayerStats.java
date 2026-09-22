package de.raindancer118.hypixelscout.core;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * One player's Bedwars record as the overlay shows it.
 *
 * <p>Every number is optional on the wire — a player can hide each of them in their API settings,
 * and a fresh account simply has none — so the absent case is represented rather than faked: a
 * hidden winstreak is {@code null} and not a zero that would read as "never won twice in a row".
 */
public final class PlayerStats {
	private final String name;
	private final UUID uuid;
	private final boolean nicked;
	private final int stars;
	private final int finalKills;
	private final int finalDeaths;
	private final int wins;
	private final int losses;
	private final int bedsBroken;
	private final Integer winstreak;
	private final double networkLevel;
	private final long firstLogin;
	private final long lastLogin;
	private final String rank;
	private final int kills;
	private final int deaths;
	private final int bedsLost;
	private final int karma;
	private final Map<String, String> socials;

	private PlayerStats(Builder builder) {
		this.name = builder.name;
		this.uuid = builder.uuid;
		this.nicked = builder.nicked;
		this.stars = builder.stars;
		this.finalKills = builder.finalKills;
		this.finalDeaths = builder.finalDeaths;
		this.wins = builder.wins;
		this.losses = builder.losses;
		this.bedsBroken = builder.bedsBroken;
		this.winstreak = builder.winstreak;
		this.networkLevel = builder.networkLevel;
		this.firstLogin = builder.firstLogin;
		this.lastLogin = builder.lastLogin;
		this.rank = builder.rank;
		this.kills = builder.kills;
		this.deaths = builder.deaths;
		this.bedsLost = builder.bedsLost;
		this.karma = builder.karma;
		this.socials = Collections.unmodifiableMap(builder.socials);
	}

	public static Builder builder(String name, UUID uuid) {
		return new Builder(name, uuid);
	}

	/** A player Hypixel will not identify: in a Hypixel game that can only mean a nick. */
	public static PlayerStats nicked(String name, UUID uuid) {
		return new Builder(name, uuid).nicked(true).build();
	}

	public String getName() {
		return name;
	}

	public UUID getUuid() {
		return uuid;
	}

	public boolean isNicked() {
		return nicked;
	}

	public int getStars() {
		return stars;
	}

	public int getFinalKills() {
		return finalKills;
	}

	public int getFinalDeaths() {
		return finalDeaths;
	}

	public int getWins() {
		return wins;
	}

	public int getLosses() {
		return losses;
	}

	public int getBedsBroken() {
		return bedsBroken;
	}

	/** {@code null} when the player hides it, which is not the same as a streak of zero. */
	public Integer getWinstreak() {
		return winstreak;
	}

	public double getNetworkLevel() {
		return networkLevel;
	}

	/** Epoch milliseconds, or {@code 0} when hidden — the account-age signal for alt hunting. */
	public long getFirstLogin() {
		return firstLogin;
	}

	/** Epoch milliseconds, or {@code 0} when hidden. */
	public long getLastLogin() {
		return lastLogin;
	}

	/** The raw Hypixel rank id such as {@code MVP_PLUS}, or {@code null} for a default player. */
	public String getRank() {
		return rank;
	}

	public int getKills() {
		return kills;
	}

	public int getDeaths() {
		return deaths;
	}

	public int getBedsLost() {
		return bedsLost;
	}

	public int getKarma() {
		return karma;
	}

	/**
	 * The social links the player published on Hypixel, keyed by service. Empty, never null: a
	 * player with no links and a player who hid them look the same from here.
	 */
	public Map<String, String> getSocials() {
		return socials;
	}

	public double getFkdr() {
		return ratio(finalKills, finalDeaths);
	}

	public double getWlr() {
		return ratio(wins, losses);
	}

	/** With no deaths there is nothing to divide by, and the kill count is what overlays show. */
	private static double ratio(int top, int bottom) {
		return bottom == 0 ? top : (double) top / bottom;
	}

	public static final class Builder {
		private final String name;
		private final UUID uuid;
		private boolean nicked;
		private int stars;
		private int finalKills;
		private int finalDeaths;
		private int wins;
		private int losses;
		private int bedsBroken;
		private Integer winstreak;
		private double networkLevel;
		private long firstLogin;
		private long lastLogin;
		private String rank;
		private int kills;
		private int deaths;
		private int bedsLost;
		private int karma;
		private Map<String, String> socials = new java.util.LinkedHashMap<String, String>();

		private Builder(String name, UUID uuid) {
			this.name = name;
			this.uuid = uuid;
		}

		public Builder nicked(boolean value) {
			this.nicked = value;
			return this;
		}

		public Builder stars(int value) {
			this.stars = value;
			return this;
		}

		public Builder finals(int kills, int deaths) {
			this.finalKills = kills;
			this.finalDeaths = deaths;
			return this;
		}

		public Builder games(int wins, int losses) {
			this.wins = wins;
			this.losses = losses;
			return this;
		}

		public Builder bedsBroken(int value) {
			this.bedsBroken = value;
			return this;
		}

		public Builder beds(int broken, int lost) {
			this.bedsBroken = broken;
			this.bedsLost = lost;
			return this;
		}

		public Builder kills(int kills, int deaths) {
			this.kills = kills;
			this.deaths = deaths;
			return this;
		}

		public Builder karma(int value) {
			this.karma = value;
			return this;
		}

		public Builder socials(Map<String, String> value) {
			this.socials = value == null
					? new java.util.LinkedHashMap<String, String>()
					: new java.util.LinkedHashMap<String, String>(value);
			return this;
		}

		public Builder winstreak(Integer value) {
			this.winstreak = value;
			return this;
		}

		public Builder networkLevel(double value) {
			this.networkLevel = value;
			return this;
		}

		public Builder logins(long first, long last) {
			this.firstLogin = first;
			this.lastLogin = last;
			return this;
		}

		public Builder rank(String value) {
			this.rank = value;
			return this;
		}

		public PlayerStats build() {
			return new PlayerStats(this);
		}
	}
}
