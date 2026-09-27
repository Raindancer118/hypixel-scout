package de.raindancer118.hypixelscout.startup;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.scout.api.ScoutApi;

/**
 * Leaving the game ends Scout's round with it: this mod drives the rounds, so Scout does not keep
 * watching a lobby. {@code clientStopping} is what quitting mid-round runs (the shutdown hook). Ends
 * the game, so it comes last.
 */
public final class LeavingCheck implements StartupCheck {

	@Override
	public String name() {
		return "leaving-ends-scouts-round";
	}

	@Override
	public boolean tick() {
		// With a host driving the rounds, Scout watches exactly while the host says a game is on.
		if (!ScoutApi.hosts().watching()) {
			throw new IllegalStateException("Scout did not watch the game before it ended");
		}
		HypixelScout.get().clientStopping();
		if (ScoutApi.hosts().watching()) {
			throw new IllegalStateException("Scout still watches after the game ended");
		}
		return true;
	}
}
