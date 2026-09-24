package de.raindancer118.hypixelscout.cheat;

import java.util.Objects;

/**
 * One sighting of a check: who, what, and the evidence in a few words ({@code 4.1 blocks}).
 *
 * <p>A {@linkplain #relief() relief} is the opposite: the player just did the legit thing where the
 * check looks — swung with the sword down, took a hit's knockback — which takes some of their
 * sightings back ({@link Suspicion}).
 */
public final class Violation {
	private final String player;
	private final Check check;
	private final String detail;
	private final long tick;
	private final boolean relief;

	public Violation(String player, Check check, String detail, long tick, boolean relief) {
		this.player = player;
		this.check = check;
		this.detail = detail;
		this.tick = tick;
		this.relief = relief;
	}

	public Violation(String player, Check check, String detail, long tick) {
		this(player, check, detail, tick, false);
	}

	public static Violation relief(String player, Check check, long tick) {
		return new Violation(player, check, "", tick, true);
	}

	public String player() {
		return player;
	}

	public Check check() {
		return check;
	}

	public String detail() {
		return detail;
	}

	public long tick() {
		return tick;
	}

	public boolean relief() {
		return relief;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Violation)) return false;
		Violation other = (Violation) obj;
		return tick == other.tick && relief == other.relief && check == other.check
				&& Objects.equals(player, other.player) && Objects.equals(detail, other.detail);
	}

	@Override
	public int hashCode() {
		return Objects.hash(player, check, detail, tick, relief);
	}

	@Override
	public String toString() {
		return "Violation[player=" + player + ", check=" + check + ", detail=" + detail + ", tick=" + tick
				+ ", relief=" + relief + "]";
	}
}
