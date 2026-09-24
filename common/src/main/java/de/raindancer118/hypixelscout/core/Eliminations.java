package de.raindancer118.hypixelscout.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who is out of the current game, from Bedwars' kill lines.
 *
 * <p>Every kill line starts with the victim's name and a final one ends in "FINAL KILL!". Nothing
 * a player types can look like one: their chat always carries a colon after the name.
 */
public final class Eliminations {
	private static final Pattern FINAL_KILL = Pattern.compile("^([A-Za-z0-9_]{1,16}) [^:]*FINAL KILL!$");

	private final Set<String> out = new HashSet<>();

	/** The name of whoever this line says was eliminated, or {@code null} if it says nothing of the kind. */
	public static String finalKillVictim(String line) {
		if (line == null) {
			return null;
		}

		Matcher matcher = FINAL_KILL.matcher(line.trim());
		return matcher.matches() ? matcher.group(1) : null;
	}

	/**
	 * Whether it is time for the next game.
	 *
	 * @param partyInGame the other party members playing in this game; empty without a party
	 */
	public static boolean requeueDue(RequeueMode mode, String self, Collection<String> partyInGame,
			Predicate<String> isOut) {
		if (mode == RequeueMode.OFF || self == null || !isOut.test(self)) {
			return false;
		}

		return mode == RequeueMode.SELF || partyInGame.stream().allMatch(isOut);
	}

	/** Notes the victim of a final kill line; returns them, or {@code null} for any other line. */
	public synchronized String record(String line) {
		String victim = finalKillVictim(line);
		if (victim != null) {
			out.add(victim.toLowerCase(Locale.ROOT));
		}
		return victim;
	}

	public synchronized boolean isOut(String name) {
		return name != null && out.contains(name.toLowerCase(Locale.ROOT));
	}

	/** A new game: everybody is back in. */
	public synchronized void reset() {
		out.clear();
	}
}
