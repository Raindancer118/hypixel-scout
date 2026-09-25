package de.raindancer118.hypixelscout.ui;

import de.raindancer118.cheatwatch.Suspicion;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Who has been flagged for cheating this round, and how sure the mod is, for every surface that
 * names players: the lists put a warning sign in front of the name, the nametag adds the
 * confidence. Installed by the mod; empty while detection or marking is off.
 */
public final class Suspects {
	/** In front of a flagged player's name. */
	public static final String MARK = "\u00a7c\u26a0 ";

	private static Function<String, List<Suspicion.Flag>> flags = name -> List.of();
	private static ToDoubleFunction<String> confidence = name -> 0;

	private Suspects() {
	}

	public static void use(Function<String, List<Suspicion.Flag>> flagSource, ToDoubleFunction<String> confidenceSource) {
		flags = flagSource;
		confidence = confidenceSource;
	}

	public static List<Suspicion.Flag> of(String name) {
		return name == null ? List.of() : flags.apply(name);
	}

	/** {@link #MARK} for a flagged player, nothing for anybody else. */
	public static String mark(String name) {
		return of(name).isEmpty() ? "" : MARK;
	}

	/** How sure the mod is that this player cheats, 0 to 1, from everything seen this round. */
	public static double confidence(String name) {
		return name == null ? 0 : confidence.applyAsDouble(name);
	}

	/** {@code 91%} in yellow, gold or red as the confidence rises. */
	public static String percent(double confidence) {
		String colour = confidence >= 0.8 ? "\u00a7c" : confidence >= 0.5 ? "\u00a76" : "\u00a7e";
		return colour + Math.round(confidence * 100) + "%";
	}

	/** The section-sign colour of a player's Bedwars team, white when there is none. */
	public static String teamCode(String name) {
		return switch (de.raindancer118.hypixelscout.game.Teams.of(name).name()) {
			case "Red" -> "\u00a7c";
			case "Blue" -> "\u00a79";
			case "Green" -> "\u00a7a";
			case "Yellow" -> "\u00a7e";
			case "Aqua" -> "\u00a7b";
			case "Pink" -> "\u00a7d";
			case "Gray" -> "\u00a77";
			default -> "\u00a7f";
		};
	}

	/** What a card's cheat field says about this player: {@code ⚠ 91%} when flagged, else nothing. */
	public static java.util.Map<de.raindancer118.hypixelscout.core.CardField, String> cardExtras(String name) {
		return of(name).isEmpty() ? java.util.Map.of()
				: java.util.Map.of(de.raindancer118.hypixelscout.core.CardField.CHEATS, MARK + percent(confidence(name)));
	}

	/** {@code ⚠ 91% } in front of a flagged player's nametag; nothing for anybody else. */
	public static String tagMark(String name) {
		return of(name).isEmpty() ? "" : MARK + percent(confidence(name)) + " ";
	}
}
