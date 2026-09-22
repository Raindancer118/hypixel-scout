package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatScale;

import java.util.function.Supplier;

/**
 * The threat scale every surface uses, so the table, the cards, the tooltip and the chat lines all
 * measure a player against the same thing. Installed by the mod, which keeps it current as the
 * player's own stats and their team come in.
 */
public final class Threats {
	private static Supplier<ThreatScale> scale = () -> ThreatScale.ABSOLUTE;

	private Threats() {
	}

	public static void use(Supplier<ThreatScale> source) {
		scale = source;
	}

	public static ThreatScale scale() {
		return scale.get();
	}

	public static Threat of(PlayerStats stats) {
		return scale().threatOf(stats);
	}
}
