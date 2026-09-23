package de.raindancer118.hypixelscout.core;

/**
 * Which kind of danger a threat level is about.
 *
 * <p>A player can be deadly in a fight and harmless to a bed, or the other way round: the rusher
 * who breaks three beds a game and loses every duel. The player picks what they want to be warned
 * about — or both, in which case each is rated on its own and the worse one counts.
 */
public enum ThreatFocus {
	/** How likely they are to kill you: finals, kills, wins, form. */
	COMBAT,
	/** How likely they are to take your bed: beds broken against lost, beds a game, wins, form. */
	BEDS,
	/** Both, each on its own scale; the level shown is the worse of the two. */
	BOTH;

	public boolean ratesCombat() {
		return this != BEDS;
	}

	public boolean ratesBeds() {
		return this != COMBAT;
	}
}
