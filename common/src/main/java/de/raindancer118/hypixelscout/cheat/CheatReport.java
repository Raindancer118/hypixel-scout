package de.raindancer118.hypixelscout.cheat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The flags of the round as chat lines for the party or the team, one per flagged player, the surest
 * first: {@code CHEATER? YELLOW Sundial 91% sure - Reach x4, Scaffold x8}.
 *
 * <p>Plain ASCII and at most {@link #MAX_LENGTH} characters, like the threat reports: Hypixel's chat
 * mangles anything else, and a long line is one nobody reads mid-fight. Checks that do not fit are
 * left off the end.
 */
public final class CheatReport {
	public static final int MAX_LENGTH = 100;

	private CheatReport() {
	}

	/**
	 * @param teamOf the Bedwars team a player is on ({@code Yellow}), empty when unknown
	 */
	public static List<String> lines(Suspicion suspicion, Function<String, String> teamOf) {
		Map<String, List<Suspicion.Flag>> byPlayer = new LinkedHashMap<>();
		for (Suspicion.Flag flag : suspicion.flagged()) {
			byPlayer.computeIfAbsent(flag.player(), name -> new ArrayList<>()).add(flag);
		}

		List<String> players = new ArrayList<>(byPlayer.keySet());
		players.sort(Comparator.comparingDouble(suspicion::confidence).reversed());

		List<String> lines = new ArrayList<>();
		for (String player : players) {
			String team = ascii(teamOf.apply(player)).toUpperCase(Locale.ROOT);
			StringBuilder line = new StringBuilder("CHEATER? ");
			if (!team.trim().isEmpty()) {
				line.append(team).append(' ');
			}
			line.append(ascii(player)).append(' ')
					.append(Math.round(suspicion.confidence(player) * 100)).append("% sure -");

			boolean first = true;
			for (Suspicion.Flag flag : byPlayer.get(player)) {
				String part = (first ? " " : ", ") + ascii(flag.check().label()) + " x" + flag.count();
				if (line.length() + part.length() > MAX_LENGTH) {
					break;
				}
				line.append(part);
				first = false;
			}
			lines.add(line.toString());
		}
		return lines;
	}

	private static String ascii(String text) {
		return text == null ? "" : text.replaceAll("[^\\x20-\\x7E]", "");
	}
}
