package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.Threat;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A figure column, shared by the HUD table, the tab list and the game list so the same number is
 * never worded two ways.
 */
public enum Column {
	THREAT("column.threat", null, settings -> true,
			stats -> Threat.of(stats).colour() + Threat.of(stats).label()),
	FKDR("column.fkdr", SortMode.FKDR, settings -> true,
			stats -> StatFormat.ratioColour(stats.getFkdr()) + StatFormat.ratio(stats.getFkdr())),
	WLR("column.wlr", SortMode.WLR, settings -> settings.table.showWlr,
			stats -> StatFormat.ratioColour(stats.getWlr()) + StatFormat.ratio(stats.getWlr())),
	WINSTREAK("column.winstreak", null, settings -> settings.table.showWinstreak,
			stats -> "§f" + StatFormat.winstreak(stats.getWinstreak())),
	BEDS_PER_GAME("column.beds_per_game", null, settings -> settings.table.showBedsPerGame,
			stats -> perGame(ProfileMetrics.bedsPerGame(stats), StatFormat.bedsPerGameColour(ProfileMetrics.bedsPerGame(stats)))),
	KILLS_PER_GAME("column.kills_per_game", null, settings -> settings.table.showKillsPerGame,
			stats -> perGame(ProfileMetrics.killsPerGame(stats), StatFormat.killsPerGameColour(ProfileMetrics.killsPerGame(stats)))),
	BEDS("column.beds", null, settings -> settings.table.showBeds,
			stats -> "§f" + StatFormat.count(stats.getBedsBroken())),
	AGE("column.age", null, settings -> settings.table.showAccountAge,
			stats -> "§7" + StatFormat.age(stats.getFirstLogin(), System.currentTimeMillis()));

	private final String key;
	private final SortMode sort;
	private final Predicate<ScoutSettings> enabled;
	private final Function<PlayerStats, String> value;

	Column(String key, SortMode sort, Predicate<ScoutSettings> enabled,
			Function<PlayerStats, String> value) {
		this.key = key;
		this.sort = sort;
		this.enabled = enabled;
		this.value = value;
	}

	/** One decimal: a per-game rate read at a glance, where the second one is noise. */
	private static String perGame(double value, String colour) {
		return colour + String.format(java.util.Locale.ROOT, "%.1f", value);
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

		return value.apply(row.stats());
	}

	/** The columns the HUD and the tab list show; the threat column is for the game list only. */
	public static List<Column> compact(ScoutSettings settings) {
		return java.util.Arrays.stream(values())
				.filter(column -> column != THREAT && column.enabled.test(settings))
				.toList();
	}

	public static List<Column> full(ScoutSettings settings) {
		return java.util.Arrays.stream(values()).filter(column -> column.enabled.test(settings)).toList();
	}
}
