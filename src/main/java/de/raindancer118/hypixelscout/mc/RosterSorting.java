package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.StatsSorting;

import java.util.Comparator;

/**
 * Orders roster entries by looking each player's stats up as the comparison needs them.
 *
 * <p>The decision of what beats what is in {@code StatsSorting}, where it is tested without a game;
 * all this adds is the lookup from a roster entry to the stats behind it.
 */
public final class RosterSorting {
	private RosterSorting() {
	}

	public static Comparator<RosterTracker.Member> comparator(final StatsService stats,
			final SortMode mode) {
		return new Comparator<RosterTracker.Member>() {
			@Override
			public int compare(RosterTracker.Member left, RosterTracker.Member right) {
				if (mode == SortMode.NAME) {
					return StatsSorting.BY_NAME.compare(left.getName(), right.getName());
				}

				return Double.compare(
						StatsSorting.value(stats.peek(right.getUuid()), mode),
						StatsSorting.value(stats.peek(left.getUuid()), mode));
			}
		};
	}
}
