package de.raindancer118.hypixelscout.cheat;

/** One sighting of a check: who, what, and the evidence in a few words ({@code 4.1 blocks}). */
public record Violation(String player, Check check, String detail, long tick) {
}
