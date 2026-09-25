package de.raindancer118.hypixelscout.ui;

import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.hypixelscout.core.CardField;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Who has been flagged for cheating this round, and how sure the mod is, for every surface that
 * names players: the lists put a warning sign in front of the name, the nametag adds the
 * confidence. Installed by the mod (a later phase's {@code game.CheatSensor}); empty, safe no-op
 * defaults until then, mirroring how 26.2 default-initializes these fields before {@link #use} is
 * ever called.
 *
 * <p>Ported closely from 26.2's {@code ui.Suspects}; the only 1.8.9-forced change is the switch
 * expression in {@link #teamCode} becoming a classic switch statement (Java 8 has no switch
 * expressions).
 */
public final class Suspects {
	/** In front of a flagged player's name. */
	public static final String MARK = "§c⚠ ";

	private static Function<String, List<Suspicion.Flag>> flags = new Function<String, List<Suspicion.Flag>>() {
		@Override
		public List<Suspicion.Flag> apply(String name) {
			return Collections.emptyList();
		}
	};
	private static ToDoubleFunction<String> confidence = new ToDoubleFunction<String>() {
		@Override
		public double applyAsDouble(String name) {
			return 0;
		}
	};

	private Suspects() {
	}

	/** Installed by a later phase's {@code game.CheatSensor}, fed from {@code cheat.Suspicion}. */
	public static void use(Function<String, List<Suspicion.Flag>> flagSource, ToDoubleFunction<String> confidenceSource) {
		flags = flagSource;
		confidence = confidenceSource;
	}

	public static List<Suspicion.Flag> of(String name) {
		return name == null ? Collections.<Suspicion.Flag>emptyList() : flags.apply(name);
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
		String colour = confidence >= 0.8 ? "§c" : confidence >= 0.5 ? "§6" : "§e";
		return colour + Math.round(confidence * 100) + "%";
	}

	/**
	 * The section-sign colour of a player's Bedwars team, white when there is none. A later phase's
	 * {@code game.Teams} supplies the team lookup, mirroring 26.2's own {@code game.Teams}.
	 */
	public static String teamCode(String name) {
		String team = de.raindancer118.hypixelscout.game.Teams.of(name).name();
		switch (team) {
			case "Red":
				return "§c";
			case "Blue":
				return "§9";
			case "Green":
				return "§a";
			case "Yellow":
				return "§e";
			case "Aqua":
				return "§b";
			case "Pink":
				return "§d";
			case "Gray":
				return "§7";
			default:
				return "§f";
		}
	}

	/** What a card's cheat field says about this player: {@code ⚠ 91%} when flagged, else nothing. */
	public static Map<CardField, String> cardExtras(String name) {
		if (of(name).isEmpty()) {
			return Collections.emptyMap();
		}
		return Collections.singletonMap(CardField.CHEATS, MARK + percent(confidence(name)));
	}

	/** {@code ⚠ 91% } in front of a flagged player's nametag; nothing for anybody else. */
	public static String tagMark(String name) {
		return of(name).isEmpty() ? "" : MARK + percent(confidence(name)) + " ";
	}
}
