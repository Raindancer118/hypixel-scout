package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.HypixelApiException;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsCache;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.Teams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A made-up game, so the table can be placed and sized before there is a real one to look at.
 *
 * <p>Invented names and numbers only; nothing here is fetched. The spread is deliberate — a nick,
 * a failed lookup, a rainbow star — so every kind of row the table can hold is on screen
 * while it is being positioned.
 */
public final class SampleGame {
	private record Sample(String name, String team, int rgb, int stars, int finalKills,
			int finalDeaths, int wins, int losses, Integer winstreak, String rank) {
	}

	private static final List<Sample> SAMPLES = List.of(
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
	private final Map<String, Teams.Team> teams = new HashMap<>();

	public SampleGame() {
		StatsCache cache = new StatsCache(Clock.SYSTEM, Long.MAX_VALUE / 4);
		List<Roster.Member> members = new ArrayList<>();

		for (Sample sample : SAMPLES) {
			UUID uuid = UUID.nameUUIDFromBytes(("hypixelscout-sample:" + sample.name()).getBytes());
			members.add(new Roster.Member(sample.name(), uuid));
			teams.put(sample.name(), new Teams.Team(sample.team(), sample.rgb()));

			if (sample.stars() > 0 || sample.finalKills() > 0) {
				cache.put(uuid, PlayerStats.builder(sample.name(), uuid).stars(sample.stars())
						.finals(sample.finalKills(), sample.finalDeaths())
						.games(sample.wins(), sample.losses())
						.beds(sample.wins() * 2, sample.losses())
						.winstreak(sample.winstreak()).rank(sample.rank())
						.logins(System.currentTimeMillis() - 400L * 86_400_000L, System.currentTimeMillis())
						.build());
			} else if (sample.stars() == 0) {
				cache.put(uuid, PlayerStats.nicked(sample.name(), uuid));
			}
			// The last one is left out of the cache on purpose: its lookup fails, which is a row the
			// table has to be able to show as well.
		}

		// Nothing is ever fetched: every sample is answered from the cache, or refused here.
		stats = new StatsService((uuid, name) -> {
			throw new HypixelApiException("Sample lookup failed");
		}, cache);

		roster = new Roster(stats);
		roster.onLocationChanged(true, "BEDWARS_FOUR_FOUR", "Lighthouse");
		roster.refresh(members);
	}

	public TableHud table(Supplier<ScoutSettings> settings) {
		return new TableHud(roster, stats, settings, () -> true,
				name -> teams.getOrDefault(name, Teams.NONE));
	}
}
