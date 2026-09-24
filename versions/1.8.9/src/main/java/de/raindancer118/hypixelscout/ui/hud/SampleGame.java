package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.HypixelApiException;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsCache;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.StatsSource;
import de.raindancer118.hypixelscout.game.Teams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A made-up game, so the table can be placed and sized before there is a real one to look at.
 *
 * <p>Invented names and numbers only; nothing here is fetched. The spread is deliberate — a nick,
 * a failed lookup, a rainbow star — so every kind of row the table can hold is on screen while it
 * is being positioned.
 *
 * <p>Ported from 26.2's {@code ui.hud.SampleGame}: the {@code record Sample} becomes a plain final
 * class and {@code List.of} becomes {@link Arrays#asList}, since Java 8 has neither.
 */
public final class SampleGame {
	private static final class Sample {
		private final String name;
		private final String team;
		private final int rgb;
		private final int stars;
		private final int finalKills;
		private final int finalDeaths;
		private final int wins;
		private final int losses;
		private final Integer winstreak;
		private final String rank;

		Sample(String name, String team, int rgb, int stars, int finalKills, int finalDeaths, int wins,
				int losses, Integer winstreak, String rank) {
			this.name = name;
			this.team = team;
			this.rgb = rgb;
			this.stars = stars;
			this.finalKills = finalKills;
			this.finalDeaths = finalDeaths;
			this.wins = wins;
			this.losses = losses;
			this.winstreak = winstreak;
			this.rank = rank;
		}
	}

	private static final List<Sample> SAMPLES = Arrays.asList(
			new Sample("Ashenvale", "Red", 0xFF5555, 1123, 24_310, 3_020, 4_820, 1_310, 38, "SUPERSTAR"),
			new Sample("quietfox", "Red", 0xFF5555, 212, 2_120, 1_700, 610, 540, null, "VIP_PLUS"),
			new Sample("Brickmason", "Blue", 0x5555FF, 488, 6_900, 1_150, 1_730, 690, 12, "MVP_PLUS"),
			new Sample("Nimbus_07", "Blue", 0x5555FF, 64, 180, 310, 55, 120, 0, null),
			new Sample("Lanternfish", "Green", 0x55FF55, 731, 15_200, 1_380, 3_120, 890, 61, "MVP_PLUS"),
			new Sample("mossy", "Green", 0x55FF55, 0, 0, 0, 0, 0, null, null),
			new Sample("Orchard", "Yellow", 0xFFFF55, 305, 3_900, 2_450, 980, 760, 4, "MVP"),
			new Sample("Sundial", "Yellow", 0xFFFF55, -1, 0, 0, 0, 0, null, null));

	private final Roster roster;
	private final StatsService stats;
	private final Map<String, Teams.Team> teams = new HashMap<String, Teams.Team>();

	public SampleGame() {
		StatsCache cache = new StatsCache(Clock.SYSTEM, Long.MAX_VALUE / 4);
		List<Roster.Member> members = new ArrayList<Roster.Member>();

		for (Sample sample : SAMPLES) {
			UUID uuid = UUID.nameUUIDFromBytes(("hypixelscout-sample:" + sample.name).getBytes());
			members.add(new Roster.Member(sample.name, uuid));
			teams.put(sample.name, new Teams.Team(sample.team, sample.rgb));

			if (sample.stars > 0 || sample.finalKills > 0) {
				cache.put(uuid, PlayerStats.builder(sample.name, uuid).stars(sample.stars)
						.finals(sample.finalKills, sample.finalDeaths)
						.games(sample.wins, sample.losses)
						.beds(sample.wins * 2, sample.losses)
						.winstreak(sample.winstreak).rank(sample.rank)
						.logins(System.currentTimeMillis() - 400L * 86_400_000L, System.currentTimeMillis())
						.build());
			} else if (sample.stars == 0) {
				cache.put(uuid, PlayerStats.nicked(sample.name, uuid));
			}
			// The last one is left out of the cache on purpose: its lookup fails, which is a row the
			// table has to be able to show as well.
		}

		// Nothing is ever fetched: every sample is answered from the cache, or refused here.
		stats = new StatsService(new StatsSource() {
			@Override
			public PlayerStats fetch(UUID uuid, String observedName) {
				throw new HypixelApiException("Sample lookup failed");
			}
		}, cache);

		roster = new Roster(stats);
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		roster.refresh(members);
	}

	public TableHud table(final Supplier<ScoutSettings> settings) {
		return new TableHud(roster, stats, settings, new java.util.function.BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return true;
			}
		}, new java.util.function.Function<String, Teams.Team>() {
			@Override
			public Teams.Team apply(String name) {
				Teams.Team team = teams.get(name);
				return team != null ? team : Teams.NONE;
			}
		});
	}
}
