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
import java.util.Arrays;
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
 *
 * <p>Ported from 26.2's {@code ui.PlayerRow}, a Java record there; a plain final class with
 * hand-written accessors here since Java 8 has no records.
 */
public final class PlayerRow {
	/** Bedwars' own order of teams, so a grouped list reads the way the scoreboard does. */
	private static final List<String> TEAM_ORDER =
			Arrays.asList("Red", "Blue", "Green", "Yellow", "Aqua", "White", "Pink", "Gray");

	private final UUID uuid;
	private final String name;
	private final Teams.Team team;
	private final PlayerStats stats;
	private final boolean pending;
	private final String failure;

	public PlayerRow(UUID uuid, String name, Teams.Team team, PlayerStats stats, boolean pending, String failure) {
		this.uuid = uuid;
		this.name = name;
		this.team = team;
		this.stats = stats;
		this.pending = pending;
		this.failure = failure;
	}

	public static PlayerRow of(Roster.Member member, StatsService stats) {
		return of(member, stats, TeamOf.DEFAULT);
	}

	public static PlayerRow of(Roster.Member member, StatsService stats, Function<String, Teams.Team> teamOf) {
		return new PlayerRow(member.uuid(), member.name(), teamOf.apply(member.name()),
				stats.peek(member.uuid()), stats.isPending(member.uuid()), stats.failureFor(member.uuid()));
	}

	/** Every player, filtered and ordered the way the settings ask. */
	public static List<PlayerRow> all(Roster roster, StatsService stats, ScoutSettings settings,
			SortMode sort, int limit) {
		return all(roster, stats, settings, sort, limit, TeamOf.DEFAULT);
	}

	public static List<PlayerRow> all(Roster roster, StatsService stats, ScoutSettings settings,
			SortMode sort, int limit, Function<String, Teams.Team> teamOf) {
		List<PlayerRow> rows = new ArrayList<PlayerRow>();

		for (Roster.Member member : roster.members()) {
			if (settings.table.hideOwnTeam && Teams.isOwnTeam(member.name())) {
				continue;
			}

			rows.add(of(member, stats, teamOf));
		}

		Comparator<PlayerRow> order = by(sort);
		if (settings.table.groupByTeam) {
			final Comparator<PlayerRow> rest = order;
			order = new Comparator<PlayerRow>() {
				@Override
				public int compare(PlayerRow left, PlayerRow right) {
					int byTeam = Integer.compare(left.teamRank(), right.teamRank());
					return byTeam != 0 ? byTeam : rest.compare(left, right);
				}
			};
		}

		java.util.Collections.sort(rows, order);
		return rows.size() > limit ? rows.subList(0, limit) : rows;
	}

	public static Comparator<PlayerRow> by(final SortMode sort) {
		if (sort == SortMode.NAME) {
			return new Comparator<PlayerRow>() {
				@Override
				public int compare(PlayerRow left, PlayerRow right) {
					return StatsSorting.BY_NAME.compare(left.name(), right.name());
				}
			};
		}

		// Highest first; unknown and nicked players answer -1 and sink to the bottom.
		return new Comparator<PlayerRow>() {
			@Override
			public int compare(PlayerRow left, PlayerRow right) {
				int byValue = Double.compare(StatsSorting.value(right.stats(), sort), StatsSorting.value(left.stats(), sort));
				return byValue != 0 ? byValue : StatsSorting.BY_NAME.compare(left.name(), right.name());
			}
		};
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

	public UUID uuid() {
		return uuid;
	}

	public String name() {
		return name;
	}

	public Teams.Team team() {
		return team;
	}

	public PlayerStats stats() {
		return stats;
	}

	public boolean pending() {
		return pending;
	}

	public String failure() {
		return failure;
	}

	/** Kept as a static constant rather than a method reference so Java 8's inference stays simple. */
	private static final class TeamOf {
		static final Function<String, Teams.Team> DEFAULT = new Function<String, Teams.Team>() {
			@Override
			public Teams.Team apply(String name) {
				return Teams.of(name);
			}
		};
	}
}
