package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatScale;

import java.util.function.Supplier;

/**
 * The threat scale every surface uses, so the table, the cards, the tooltip and the chat lines all
 * measure a player against the same thing. Installed by the mod, which keeps it current as the
 * player's own stats and their team come in.
 *
 * <p>Ported verbatim from 26.2's {@code ui.Threats}: pure logic over {@code common}'s
 * {@link Threat}/{@link ThreatScale}, no Minecraft API at all.
 */
public final class Threats {
	private static Supplier<ThreatScale> scale = new Supplier<ThreatScale>() {
		@Override
		public ThreatScale get() {
			return ThreatScale.ABSOLUTE;
		}
	};

	private Threats() {
	}

	/** Installed by the mod; a later phase keeps this current with the player's own stats and team. */
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
