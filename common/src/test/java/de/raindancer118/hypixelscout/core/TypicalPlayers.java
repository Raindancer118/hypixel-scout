package de.raindancer118.hypixelscout.core;

import java.util.UUID;

/** Test players whose numbers all sit where an ordinary player's do for their FKDR. */
final class TypicalPlayers {
	private TypicalPlayers() {
	}

	/**
	 * KDR, WLR and BBLR at the typical share of the FKDR, the typical finals and beds a game and no
	 * known winstreak. For such a player the combat index is the old stars × FKDR², and the bed
	 * index is the same number.
	 */
	static PlayerStats of(int stars, double fkdr) {
		int losses = 4000;
		int wins = (int) Math.round(losses * fkdr / Threat.WLR_TO_FKDR);
		int games = wins + losses;
		int finals = (int) Math.round(games * Threat.TYPICAL_FINALS_PER_GAME);
		int deaths = 30_000;
		int bedsBroken = (int) Math.round(games * Threat.TYPICAL_BEDS_PER_GAME);

		return PlayerStats.builder("P" + stars, UUID.randomUUID()).stars(stars)
				.finals(finals, (int) Math.round(finals / fkdr))
				.kills((int) Math.round(deaths * fkdr / Threat.KDR_TO_FKDR), deaths)
				.games(wins, losses)
				.beds(bedsBroken, (int) Math.round(bedsBroken * Threat.BBLR_TO_FKDR / fkdr))
				.build();
	}

	/** A typical player with FKDR 1, whose index is their star count. */
	static PlayerStats index(int stars) {
		return of(stars, 1.0);
	}

	/**
	 * A typical player with the given finals: games, kills, wins and beds filled in around them at
	 * the typical shares, so the combat and bed index are stars × FKDR² times the winstreak bonus.
	 */
	static PlayerStats like(String name, int stars, int finalKills, int finalDeaths, Integer winstreak) {
		double fkdr = finalDeaths == 0 ? finalKills : (double) finalKills / finalDeaths;
		int games = Math.max(1, (int) Math.round(finalKills / Threat.TYPICAL_FINALS_PER_GAME));
		int losses = (int) Math.round(games / (1 + fkdr / Threat.WLR_TO_FKDR));
		int deaths = Math.max(1, finalDeaths * 3);
		int bedsBroken = (int) Math.round(games * Threat.TYPICAL_BEDS_PER_GAME);

		return PlayerStats.builder(name, UUID.randomUUID()).stars(stars).finals(finalKills, finalDeaths)
				.games(games - losses, losses)
				.kills((int) Math.round(deaths * fkdr / Threat.KDR_TO_FKDR), deaths)
				.beds(bedsBroken, (int) Math.round(bedsBroken * Threat.BBLR_TO_FKDR / Math.max(fkdr, 0.01)))
				.winstreak(winstreak)
				.build();
	}
}
