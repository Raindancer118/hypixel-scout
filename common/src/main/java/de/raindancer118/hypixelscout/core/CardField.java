package de.raindancer118.hypixelscout.core;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * One piece of a player card, as the player picks them for each card: a label and a value in a
 * few characters ({@code FKDR 13.83}), or nothing where there is nothing to say — a hidden
 * winstreak, no socials — rather than an empty slot.
 */
public enum CardField {
	/** The one threat level, {@code INSANE} or {@code INSANE at beds}. */
	THREAT,
	/** Both dangers when both are rated ({@code Fight INSANE  Beds HIGH}), else the one. */
	RATINGS,
	/** FKDR, or the BBLR where the level is about beds. */
	HEADLINE_RATIO,
	FKDR, WLR, BBLR, KDR, WINSTREAK, FINALS, WINS, BEDS, WIN_RATE,
	BEDS_PER_GAME, KILLS_PER_GAME, FINALS_PER_GAME, GAMES, LEVEL, KARMA, ACCOUNT_AGE, LAST_LOGIN, SOCIALS,
	/** How sure the cheat detection is; filled in by whoever draws the card, since only it knows. */
	CHEATS;

	/** This field for a looked-up, un-nicked player; {@code null} when there is nothing to show. */
	public String render(PlayerStats p, ThreatScale scale, long now) {
		switch (this) {
			case THREAT:
				return StatLines.threat(scale, p);
			case RATINGS: {
				ThreatScale.Rating rating = scale.rate(p);
				return scale.focus() == ThreatFocus.BOTH
						? "§7Fight " + rating.combat().colour() + rating.combat().label() + "  §7Beds "
								+ rating.beds().colour() + rating.beds().label()
						: "§7Threat " + rating.overall().colour() + rating.overall().label();
			}
			case HEADLINE_RATIO:
				return StatLines.aboutBeds(scale, p)
						? "§7BBLR " + StatFormat.ratioColour(ProfileMetrics.bedRatio(p)) + StatFormat.ratio(ProfileMetrics.bedRatio(p))
						: "§7FKDR " + StatFormat.ratioColour(p.getFkdr()) + StatFormat.ratio(p.getFkdr());
			case FKDR:
				return "§7FKDR " + StatFormat.ratioColour(p.getFkdr()) + StatFormat.ratio(p.getFkdr());
			case WLR:
				return "§7WLR " + StatFormat.ratioColour(p.getWlr()) + StatFormat.ratio(p.getWlr());
			case BBLR:
				return "§7BBLR §f" + StatFormat.ratio(ProfileMetrics.bedRatio(p));
			case KDR:
				return "§7KDR §f" + StatFormat.ratio(ProfileMetrics.kdr(p));
			case WINSTREAK:
				return p.getWinstreak() == null || p.getWinstreak() <= 0 ? null : "§7WS §f" + p.getWinstreak();
			case FINALS:
				return "§7Finals §f" + StatFormat.count(p.getFinalKills()) + "§8/§f" + StatFormat.count(p.getFinalDeaths());
			case WINS:
				return "§7Wins §f" + StatFormat.count(p.getWins()) + "§8/§f" + StatFormat.count(p.getLosses());
			case BEDS:
				return "§7Beds §f" + StatFormat.count(p.getBedsBroken());
			case WIN_RATE:
				return "§7Win% §f" + StatFormat.ratio(ProfileMetrics.winRate(p));
			case BEDS_PER_GAME:
				return "§7B/G " + StatFormat.bedsPerGameColour(ProfileMetrics.bedsPerGame(p))
						+ StatFormat.ratio(ProfileMetrics.bedsPerGame(p));
			case KILLS_PER_GAME:
				return "§7K/G " + StatFormat.killsPerGameColour(ProfileMetrics.killsPerGame(p))
						+ StatFormat.ratio(ProfileMetrics.killsPerGame(p));
			case FINALS_PER_GAME:
				return "§7F/G §f" + StatFormat.ratio(ProfileMetrics.finalsPerGame(p));
			case GAMES:
				return "§7Games §f" + StatFormat.count(ProfileMetrics.gamesPlayed(p));
			case LEVEL:
				return "§7Level §f" + (int) p.getNetworkLevel();
			case KARMA:
				return "§7Karma §f" + StatFormat.count(p.getKarma());
			case ACCOUNT_AGE:
				return p.getFirstLogin() <= 0 ? null : "§7Account §f" + StatFormat.age(p.getFirstLogin(), now);
			case LAST_LOGIN:
				return p.getLastLogin() <= 0 ? null : "§7Seen §f" + StatFormat.age(p.getLastLogin(), now) + " ago";
			case SOCIALS:
				return p.getSocials().isEmpty() ? null
						: "§8" + String.join(" ", p.getSocials().keySet().stream()
								.map(s -> s.toLowerCase(Locale.ROOT)).sorted().collect(Collectors.toList()));
			case CHEATS:
				return null;
			default:
				throw new IllegalStateException("Unexpected field: " + this);
		}
	}
}
