package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.core.CardField;
import de.raindancer118.scout.api.ScoutApi;
import de.raindancer118.scout.hud.Marks;

import java.util.Collections;
import java.util.Map;

/**
 * Scout's verdicts on the players, for this mod's own lists and cards: a warning sign in front of a
 * flagged name, the confidence on the card's cheat field. Scout does the watching and draws its own
 * HUD and nametag mark; this only reads what it has seen. Empty while its detection or marks are off.
 */
public final class Suspects {
	private Suspects() {
	}

	/** {@link Marks#MARK} for a flagged player, nothing for anybody else. */
	public static String mark(String name) {
		return name != null && ScoutApi.get().marked(name) ? Marks.MARK : "";
	}

	/** What a card's cheat field says about this player: {@code ⚠ 91%} when flagged, else nothing. */
	public static Map<CardField, String> cardExtras(String name) {
		return mark(name).isEmpty() ? Collections.<CardField, String>emptyMap()
				: Collections.singletonMap(CardField.CHEATS, Marks.MARK + Marks.percent(ScoutApi.get().confidence(name)));
	}
}
