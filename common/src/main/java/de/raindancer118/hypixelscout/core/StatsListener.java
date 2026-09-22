package de.raindancer118.hypixelscout.core;

import java.util.UUID;

/**
 * Told when a player's stats have arrived.
 *
 * <p>Called on the fetching thread, not the game thread: an implementation that touches Minecraft
 * has to hand the work over itself.
 */
public interface StatsListener {
	void onStats(UUID uuid, PlayerStats stats);
}
