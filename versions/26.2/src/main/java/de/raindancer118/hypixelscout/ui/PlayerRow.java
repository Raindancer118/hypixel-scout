package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.StatsSorting;
import de.raindancer118.hypixelscout.game.Teams;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * One player of the current game as every list in the mod shows them: face, team, name and the
 * figures for each {@link Column}.
 *
 * <p>Built fresh each frame from the roster and the cache — both are cheap to read — so nothing
 * here can go stale.
 */
public record PlayerRow(UUID uuid, String name, Teams.Team team, PlayerStats stats, boolean pending,
		String failure) {

	/** Bedwars' own order of teams, so a grouped list reads the way the scoreboard does. */
	private static final List<String> TEAM_ORDER =
			List.of("Red", "Blue", "Green", "Yellow", "Aqua", "White", "Pink", "Gray");

	public static PlayerRow of(Roster.Member member, StatsService stats) {
		return of(member, stats, Teams::of);
	}

	public static PlayerRow of(Roster.Member member, StatsService stats,
			Function<String, Teams.Team> teamOf) {
		return new PlayerRow(member.uuid(), member.name(), teamOf.apply(member.name()),
				stats.peek(member.uuid()), stats.isPending(member.uuid()),
				stats.failureFor(member.uuid()));
	}

	/** Every player, filtered and ordered the way the settings ask. */
	public static List<PlayerRow> all(Roster roster, StatsService stats, ScoutSettings settings,
			SortMode sort, int limit) {
		return all(roster, stats, settings, sort, limit, Teams::of);
	}

	public static List<PlayerRow> all(Roster roster, StatsService stats, ScoutSettings settings,
			SortMode sort, int limit, Function<String, Teams.Team> teamOf) {
		List<PlayerRow> rows = new ArrayList<>();

		for (Roster.Member member : roster.members()) {
			if (settings.table.hideOwnTeam && Teams.isOwnTeam(member.name())) {
				continue;
			}

			rows.add(of(member, stats, teamOf));
		}

		Comparator<PlayerRow> order = by(sort);
		if (settings.table.groupByTeam) {
			order = Comparator.comparingInt(PlayerRow::teamRank).thenComparing(order);
		}

		rows.sort(order);
		return rows.size() > limit ? rows.subList(0, limit) : rows;
	}

	public static Comparator<PlayerRow> by(SortMode sort) {
		if (sort == SortMode.NAME) {
			return Comparator.comparing(PlayerRow::name, StatsSorting.BY_NAME);
		}

		// Highest first; unknown and nicked players answer -1 and sink to the bottom.
		return Comparator.comparingDouble((PlayerRow row) -> StatsSorting.value(row.stats(), sort))
				.reversed().thenComparing(PlayerRow::name, StatsSorting.BY_NAME);
	}

	private int teamRank() {
		int index = TEAM_ORDER.indexOf(team.name());
		return index < 0 ? TEAM_ORDER.size() : index;
	}

	/** Star, rank and name as Hypixel prints them. */
	public String displayName() {
		return Suspects.mark(name) + StatLines.name(name, stats);
	}

	public boolean nicked() {
		return stats != null && stats.isNicked();
	}

	/** What an empty figure cell says: still coming, or went wrong. */
	public String placeholder() {
		return stats == null && !pending && failure != null ? "§c!" : "§8…";
	}
}
