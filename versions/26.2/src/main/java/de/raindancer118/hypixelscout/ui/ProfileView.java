package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatFocus;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.game.Teams;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A player's profile as cards: the header with face and facts, combat/games/beds, and the rates.
 *
 * <p>Shared by the profile screen and the peek overlay, so holding the peek key shows exactly what
 * opening the profile would.
 */
public final class ProfileView {
	public static final int GAP = 8;

	private ProfileView() {
	}

	/** The head, the name as Hypixel prints it, and the account facts. Returns its bottom edge. */
	public static int header(GuiGraphicsExtractor g, String name, UUID uuid, PlayerStats profile, int left,
			int top, int width, int opacity) {
		int height = 52;

		ScoutTheme.panel(g, left, top, width, height, opacity);
		Teams.Team team = Teams.of(name);
		if (team != Teams.NONE) {
			ScoutTheme.pill(g, left + 1, top + 4, 2, height - 8, team.argb());
		}

		Heads.draw(g, uuid, left + 8, top + 8, 36);

		int textX = left + 52;
		ScoutTheme.text(g, StatLines.name(name, profile), textX, top + 9, ScoutTheme.TEXT);

		if (!profile.isNicked()) {
			long now = System.currentTimeMillis();
			ScoutTheme.text(g, "§7" + I18n.get("message.hypixelscout.profile.facts",
					"§f" + (int) profile.getNetworkLevel() + "§7",
					"§f" + StatFormat.count(profile.getKarma()) + "§7",
					"§f" + StatFormat.age(profile.getFirstLogin(), now) + "§7"), textX, top + 22, ScoutTheme.TEXT);

			if (profile.getLastLogin() > 0) {
				ScoutTheme.text(g, "§8" + I18n.get("message.hypixelscout.profile.last_login",
						StatFormat.age(profile.getLastLogin(), now)), textX, top + 34, ScoutTheme.TEXT);
			}

			ThreatScale scale = Threats.scale();
			ThreatScale.Rating rating = scale.rate(profile);
			int right = left + width - 8;
			if (scale.focus() == ThreatFocus.BOTH) {
				// Both dangers side by side, the fight on the left, as the cards below are laid out.
				right = threatBadge(g, "message.hypixelscout.profile.threat.beds", rating.beds(), right, top + 8) - 4;
				threatBadge(g, "message.hypixelscout.profile.threat.combat", rating.combat(), right, top + 8);
			} else {
				threatBadge(g, scale.focus() == ThreatFocus.BEDS ? "message.hypixelscout.profile.threat.beds"
						: "message.hypixelscout.profile.threat.combat", rating.overall(), right, top + 8);
			}
		}

		return top + height;
	}

	/** One threat badge ending at {@code right}; answers where it starts. */
	private static int threatBadge(GuiGraphicsExtractor g, String key, Threat threat, int right, int y) {
		String badge = I18n.get(key) + " " + threat.colour() + threat.label();
		int x = right - ScoutTheme.width(badge) - 6;
		ScoutTheme.badge(g, badge, x, y, 0x60000000, ScoutTheme.TEXT_DIM);
		return x;
	}

	private record Stat(String label, String value) {
	}

	/** Three cards side by side where they fit, one under another where they do not. */
	public static int cards(GuiGraphicsExtractor g, PlayerStats p, int left, int top, int width, int bottom,
			int opacity) {
		List<List<Stat>> cards = List.of(
				List.of(new Stat("final_kills", "§a" + StatFormat.count(p.getFinalKills())),
						new Stat("final_deaths", "§c" + StatFormat.count(p.getFinalDeaths())),
						new Stat("fkdr", StatFormat.ratioColour(p.getFkdr()) + StatFormat.ratio(p.getFkdr())),
						new Stat("kills", "§f" + StatFormat.count(p.getKills())),
						new Stat("kdr", "§f" + StatFormat.ratio(ProfileMetrics.kdr(p)))),
				List.of(new Stat("wins", "§a" + StatFormat.count(p.getWins())),
						new Stat("losses", "§c" + StatFormat.count(p.getLosses())),
						new Stat("wlr", StatFormat.ratioColour(p.getWlr()) + StatFormat.ratio(p.getWlr())),
						new Stat("win_rate", "§f" + StatFormat.ratio(ProfileMetrics.winRate(p)) + "%"),
						new Stat("winstreak", "§f" + StatFormat.winstreak(p.getWinstreak()))),
				List.of(new Stat("beds_broken", "§a" + StatFormat.count(p.getBedsBroken())),
						new Stat("beds_lost", "§c" + StatFormat.count(p.getBedsLost())),
						new Stat("bblr", "§f" + StatFormat.ratio(ProfileMetrics.bedRatio(p))),
						new Stat("games", "§f" + StatFormat.count(ProfileMetrics.gamesPlayed(p))),
						new Stat("stars", StatFormat.star(p.getStars()))));
		String[] titles = {"combat", "games", "beds"};

		int columns = width >= 390 ? 3 : 1;
		int cardWidth = (width - GAP * (columns - 1)) / columns;
		int cardHeight = ScoutTheme.HEADER_HEIGHT + 6 + 5 * 11 + 3;

		for (int i = 0; i < cards.size(); i++) {
			int x = left + (i % columns) * (cardWidth + GAP);
			int y = top + (i / columns) * (cardHeight + GAP);
			if (y + cardHeight > bottom) {
				break;
			}

			ScoutTheme.panel(g, x, y, cardWidth, cardHeight, opacity);
			ScoutTheme.header(g, x, y, cardWidth, opacity);
			ScoutTheme.text(g, ScoutTheme.accentCode() + "§l" + I18n.get("message.hypixelscout.profile.card." + titles[i]),
					x + 7, y + 6, ScoutTheme.TEXT);

			int rowY = y + ScoutTheme.HEADER_HEIGHT + 6;
			for (Stat stat : cards.get(i)) {
				ScoutTheme.text(g, "§7" + I18n.get("message.hypixelscout.profile.stat." + stat.label()), x + 7, rowY,
						ScoutTheme.TEXT);
				ScoutTheme.textRight(g, stat.value(), x + cardWidth - 7, rowY, ScoutTheme.TEXT);
				rowY += 11;
			}
		}

		int rows = (cards.size() + columns - 1) / columns;
		return top + rows * cardHeight + (rows - 1) * GAP;
	}

	/** Rates rather than totals: how much happens per game and per star, and where they link to. */
	public static void pace(GuiGraphicsExtractor g, PlayerStats p, int left, int top, int width, int bottom,
			int opacity) {
		if (top + 30 > bottom) {
			return;
		}

		Map<String, String> socials = p.getSocials();
		int height = socials.isEmpty() ? 20 : 31;

		ScoutTheme.panel(g, left, top, width, height, opacity);
		String pace = "§7" + I18n.get("message.hypixelscout.profile.pace",
				"§f" + StatFormat.ratio(ProfileMetrics.finalsPerGame(p)) + "§7",
				"§f" + StatFormat.ratio(ProfileMetrics.finalsPerStar(p)) + "§7",
				"§f" + StatFormat.ratio(ProfileMetrics.killsPerGame(p)) + "§7",
				"§f" + StatFormat.ratio(ProfileMetrics.bedsPerGame(p)) + "§7");
		ScoutTheme.textCentred(g, ScoutTheme.fit(pace, width - 14), left + width / 2, top + 6, ScoutTheme.TEXT);

		if (!socials.isEmpty()) {
			StringBuilder line = new StringBuilder("§8" + I18n.get("message.hypixelscout.profile.linked"));
			socials.forEach((service, link) -> line.append(" §7").append(service.toLowerCase(Locale.ROOT))
					.append(" §8").append(shorten(link)));
			ScoutTheme.textCentred(g, ScoutTheme.fit(line.toString(), width - 14), left + width / 2, top + 18,
					ScoutTheme.TEXT);
		}
	}

	/** A link is worth showing as a handle, not as a hundred characters of URL. */
	private static String shorten(String value) {
		String text = value.replaceFirst("^https?://(www\\.)?", "");
		return text.length() > 24 ? text.substring(0, 23) + "…" : text;
	}

}
