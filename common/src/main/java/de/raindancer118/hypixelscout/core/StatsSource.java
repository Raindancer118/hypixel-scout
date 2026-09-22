package de.raindancer118.hypixelscout.core;

import java.util.UUID;

/**
 * Where one player's stats come from.
 *
 * <p>An interface so {@link StatsService} can be tested without a socket, and so a second source
 * (a cache file, a different API) could be slotted in without touching the scheduling.
 */
public interface StatsSource {
	/** Blocks. Throws {@link HypixelApiException} rather than returning a half-filled record. */
	PlayerStats fetch(UUID uuid, String observedName);
}
