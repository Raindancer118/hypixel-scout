package de.raindancer118.hypixelscout.cheat;

/**
 * One sighting of a check: who, what, and the evidence in a few words ({@code 4.1 blocks}).
 *
 * <p>A {@linkplain #relief() relief} is the opposite: the player just did the legit thing where the
 * check looks — swung with the sword down, took a hit's knockback — which takes some of their
 * sightings back ({@link Suspicion}).
 */
public record Violation(String player, Check check, String detail, long tick, boolean relief) {
	public Violation(String player, Check check, String detail, long tick) {
		this(player, check, detail, tick, false);
	}

	public static Violation relief(String player, Check check, long tick) {
		return new Violation(player, check, "", tick, true);
	}
}
