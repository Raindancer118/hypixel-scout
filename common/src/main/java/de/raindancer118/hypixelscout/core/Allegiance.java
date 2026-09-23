package de.raindancer118.hypixelscout.core;

/**
 * Whose side somebody is on, from the two team names.
 *
 * <p>Compared by name, not by colour value: Hypixel colours a team through its prefix, and two
 * members' prefixes need not carry the exact same shade. Anything the scoreboard does not say —
 * no team of one's own yet, or none for the other player — is {@link #UNKNOWN}, never an enemy.
 */
public enum Allegiance {
	ALLY,
	ENEMY,
	UNKNOWN;

	public static Allegiance of(String ownTeam, String otherTeam) {
		if (ownTeam == null || ownTeam.isBlank() || otherTeam == null || otherTeam.isBlank()) {
			return UNKNOWN;
		}

		return ownTeam.equalsIgnoreCase(otherTeam) ? ALLY : ENEMY;
	}
}
