package de.raindancer118.hypixelscout.core;

import java.util.UUID;

/**
 * Tells a nick apart before Hypixel is asked, so a nick costs nothing from the key's budget.
 *
 * <p>A nicked player sits in the tab list under a stand-in ID. So Mojang is asked about the name:
 * if no account has it, or it belongs to a different ID than the one in the tab list, the player
 * is nicked — Hypixel would only have confirmed it with a request. Only a real player is looked up
 * at Hypixel. When Mojang cannot be reached, Hypixel decides: better one request than a real player
 * called a nick.
 */
public final class NickAwareSource implements StatsSource {
	/** Name to account ID, or {@code null} for a name no account has. May throw when unreachable. */
	public interface NameLookup {
		UUID uuidOf(String name);
	}

	private final StatsSource hypixel;
	private final NameLookup mojang;

	public NickAwareSource(StatsSource hypixel, NameLookup mojang) {
		this.hypixel = hypixel;
		this.mojang = mojang;
	}

	@Override
	public PlayerStats fetch(UUID uuid, String observedName) {
		if (observedName != null && uuid != null) {
			try {
				UUID account = mojang.uuidOf(observedName);
				if (account == null || !account.equals(uuid)) {
					return PlayerStats.nicked(observedName, uuid);
				}
			} catch (HypixelApiException e) {
				// Mojang unreachable or throttling: no guess, Hypixel answers instead.
			}
		}

		return hypixel.fetch(uuid, observedName);
	}
}
