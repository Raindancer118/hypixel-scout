package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One enemy team, added up.
 *
 * <p>The ratios pool the raw counts instead of averaging the players' ratios: a team is dangerous
 * because of the finals it gets between them, and the mean of 10.0 and 0.5 says nothing useful
 * about either player.
 *
 * <p>Players nobody can look up — nicks — are counted in the head count and left out of the
 * numbers, and the message says how many those were. A silent zero would make a stacked team look
 * harmless.
 */
public final class TeamReport {
	private final String team;
	private final int size;
	private final int unknown;
	private final int combinedStars;
	private final long finalKills;
	private final long finalDeaths;
	private final long wins;
	private final long losses;
	private final List<Streak> streaks;

	private TeamReport(String team, int size, int unknown, int combinedStars, long finalKills,
			long finalDeaths, long wins, long losses, List<Streak> streaks) {
		this.team = team;
		this.size = size;
		this.unknown = unknown;
		this.combinedStars = combinedStars;
		this.finalKills = finalKills;
		this.finalDeaths = finalDeaths;
		this.wins = wins;
		this.losses = losses;
		this.streaks = streaks;
	}

	public static TeamReport of(String team, Collection<PlayerStats> members) {
		int size = 0;
		int unknown = 0;
		int stars = 0;
		long finalKills = 0;
		long finalDeaths = 0;
		long wins = 0;
		long losses = 0;
		List<Streak> streaks = new ArrayList<Streak>();

		for (PlayerStats member : members) {
			size++;

			if (member == null || member.isNicked()) {
				unknown++;
				continue;
			}

			stars += member.getStars();
			finalKills += member.getFinalKills();
			finalDeaths += member.getFinalDeaths();
			wins += member.getWins();
			losses += member.getLosses();

			if (member.getWinstreak() != null) {
				streaks.add(new Streak(member.getName(), member.getWinstreak().intValue()));
			}
		}

		return new TeamReport(team, size, unknown, stars, finalKills, finalDeaths, wins, losses,
				streaks);
	}

	public String getTeam() {
		return team;
	}

	public int getSize() {
		return size;
	}

	/** How many of the team could not be looked up, which is to say how many are nicked. */
	public int getUnknown() {
		return unknown;
	}

	public int getCombinedStars() {
		return combinedStars;
	}

	public double getCombinedFkdr() {
		return ratio(finalKills, finalDeaths);
	}

	public double getCombinedWlr() {
		return ratio(wins, losses);
	}

	/** The players on a streak worth warning about, biggest first. */
	public List<Streak> streaksAbove(int threshold) {
		List<Streak> hot = new ArrayList<Streak>();
		for (Streak streak : streaks) {
			if (streak.winstreak > threshold) {
				hot.add(streak);
			}
		}

		Collections.sort(hot);
		return hot;
	}

	/**
	 * The line as it goes into party chat: no colour codes, because the chat strips them, and
	 * short enough that Minecraft will send it at all.
	 */
	public String toChatMessage(int streakThreshold) {
		StringBuilder message = new StringBuilder();
		message.append(team).append(": ").append(combinedStars).append(" stars");
		message.append(" | FKDR ").append(String.format(Locale.ROOT, "%.2f",
				Double.valueOf(getCombinedFkdr())));
		message.append(" | WLR ").append(String.format(Locale.ROOT, "%.2f",
				Double.valueOf(getCombinedWlr())));

		List<Streak> hot = streaksAbove(streakThreshold);
		if (!hot.isEmpty()) {
			message.append(" | WS");
			for (Streak streak : hot) {
				message.append(' ').append(streak.name).append(' ').append(streak.winstreak);
			}
		}

		if (unknown > 0) {
			message.append(" | ").append(unknown).append(" unknown");
		}

		// Kept to 100 characters, which every version of the game will send and every party member
		// can read at a glance; the streak list is what gives way rather than the numbers.
		return message.length() <= 100 ? message.toString()
				: message.substring(0, 100).trim();
	}

	private static double ratio(long top, long bottom) {
		return bottom == 0 ? top : (double) top / bottom;
	}

	/** A player who is on a run, for the part of the message that names names. */
	public static final class Streak implements Comparable<Streak> {
		private final String name;
		private final int winstreak;

		Streak(String name, int winstreak) {
			this.name = name;
			this.winstreak = winstreak;
		}

		public String getName() {
			return name;
		}

		public int getWinstreak() {
			return winstreak;
		}

		@Override
		public int compareTo(Streak other) {
			return other.winstreak - winstreak;
		}
	}
}
