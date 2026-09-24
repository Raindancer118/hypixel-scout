package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.game.Teams;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A figure column, shared by the HUD table, the tab list and the game list so the same number is
 * never worded two ways.
 *
 * <p>Ported closely from 26.2's {@code ui.Column}; {@code net.minecraft.client.resources.language
 * .I18n} does not exist on 1.8.9, so translated strings go through {@link StatCollector} instead,
 * and the {@code Stream.toList()} call ({@code java.util.stream}, Java 16+) becomes a plain
 * {@code Collectors.toList()} for Java 8.
 */
public enum Column {
	THREAT("column.threat", null, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return true;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			return Threats.of(stats).colour() + Threats.of(stats).label();
		}
	}),
	FKDR("column.fkdr", SortMode.FKDR, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return true;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			return StatFormat.ratioColour(stats.getFkdr()) + StatFormat.ratio(stats.getFkdr());
		}
	}),
	WLR("column.wlr", SortMode.WLR, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return settings.table.showWlr;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			return StatFormat.ratioColour(stats.getWlr()) + StatFormat.ratio(stats.getWlr());
		}
	}),
	WINSTREAK("column.winstreak", null, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return settings.table.showWinstreak;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			return "§f" + StatFormat.winstreak(stats.getWinstreak());
		}
	}),
	BEDS_PER_GAME("column.beds_per_game", null, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return settings.table.showBedsPerGame;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			double value = ProfileMetrics.bedsPerGame(stats);
			return perGame(value, StatFormat.bedsPerGameColour(value));
		}
	}),
	KILLS_PER_GAME("column.kills_per_game", null, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return settings.table.showKillsPerGame;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			double value = ProfileMetrics.killsPerGame(stats);
			return perGame(value, StatFormat.killsPerGameColour(value));
		}
	}),
	BEDS("column.beds", null, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return settings.table.showBeds;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			return "§f" + StatFormat.count(stats.getBedsBroken());
		}
	}),
	AGE("column.age", null, new Predicate<ScoutSettings>() {
		@Override
		public boolean test(ScoutSettings settings) {
			return settings.table.showAccountAge;
		}
	}, new Function<PlayerStats, String>() {
		@Override
		public String apply(PlayerStats stats) {
			return "§7" + StatFormat.age(stats.getFirstLogin(), System.currentTimeMillis());
		}
	});

	private final String key;
	private final SortMode sort;
	private final Predicate<ScoutSettings> enabled;
	private final Function<PlayerStats, String> value;

	Column(String key, SortMode sort, Predicate<ScoutSettings> enabled, Function<PlayerStats, String> value) {
		this.key = key;
		this.sort = sort;
		this.enabled = enabled;
		this.value = value;
	}

	/** One decimal: a per-game rate read at a glance, where the second one is noise. */
	private static String perGame(double value, String colour) {
		return colour + String.format(Locale.ROOT, "%.1f", value);
	}

	public String translationKey() {
		return "message.hypixelscout." + key;
	}

	/** The sort this column's header switches to when clicked, or {@code null} for none. */
	public SortMode sort() {
		return sort;
	}

	/** The figure for a row: the value, a nick marker, or a placeholder while it is unknown. */
	public String text(PlayerRow row) {
		if (row.stats() == null) {
			return this == FKDR || this == THREAT ? row.placeholder() : "";
		}

		if (row.nicked()) {
			return this == FKDR ? "§dNICK" : "";
		}

		// A teammate is not a threat to the team; the column says so instead of rating them.
		if (this == THREAT && !row.team().equals(Teams.NONE) && row.team().equals(Teams.own())) {
			return "§8" + StatCollector.translateToLocal("message.hypixelscout.column.ally");
		}

		return value.apply(row.stats());
	}

	/** The columns the HUD and the tab list show; the threat column is for the game list only. */
	public static List<Column> compact(ScoutSettings settings) {
		List<Column> columns = new ArrayList<Column>();
		for (Column column : values()) {
			if (column != THREAT && column.enabled.test(settings)) {
				columns.add(column);
			}
		}
		return columns;
	}

	public static List<Column> full(ScoutSettings settings) {
		List<Column> columns = new ArrayList<Column>();
		for (Column column : values()) {
			if (column.enabled.test(settings)) {
				columns.add(column);
			}
		}
		return columns;
	}
}
