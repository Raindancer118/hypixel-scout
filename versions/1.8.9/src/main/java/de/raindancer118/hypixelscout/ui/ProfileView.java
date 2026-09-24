package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatFocus;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.game.Teams;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A player's profile as cards: the header with face and facts, combat/games/beds, and the rates.
 *
 * <p>Shared by the profile screen and the peek overlay (both later phases) — the drawing itself is
 * built now so either one only has to call it.
 *
 * <p>Ported from 26.2's {@code ui.ProfileView}: {@code GuiGraphicsExtractor g} becomes an explicit
 * {@link FontRenderer} parameter (1.8.9 has no implicit "current graphics context"),
 * {@code I18n.get(key, args...)} becomes {@link StatCollector#translateToLocalFormatted}, and every
 * {@code List.of}/record/{@code var} is written the Java 8 way.
 */
public final class ProfileView {
	public static final int GAP = 8;

	private static Supplier<ScoutSettings.Profile> parts = new Supplier<ScoutSettings.Profile>() {
		@Override
		public ScoutSettings.Profile get() {
			return new ScoutSettings.Profile();
		}
	};

	private ProfileView() {
	}

	/** Which parts are shown, from the settings; installed by the mod. */
	public static void use(Supplier<ScoutSettings.Profile> source) {
		parts = source;
	}

	/** The head, the name as Hypixel prints it, and the account facts. Returns its bottom edge. */
	public static int header(FontRenderer font, String name, UUID uuid, PlayerStats profile, int left,
			int top, int width, int opacity) {
		int height = 52;

		ScoutTheme.panel(left, top, width, height, opacity);
		Teams.Team team = Teams.of(name);
		if (team != Teams.NONE) {
			ScoutTheme.pill(left + 1, top + 4, 2, height - 8, team.argb());
		}

		Heads.draw(uuid, left + 8, top + 8, 36);

		int textX = left + 52;
		ScoutTheme.text(font, Suspects.mark(name) + StatLines.name(name, profile), textX, top + 9, ScoutTheme.TEXT);

		ScoutSettings.Profile shown = parts.get();
		if (!profile.isNicked()) {
			long now = System.currentTimeMillis();
			if (shown.facts) {
				ScoutTheme.text(font, "§7" + StatCollector.translateToLocalFormatted("message.hypixelscout.profile.facts",
						"§f" + (int) profile.getNetworkLevel() + "§7",
						"§f" + StatFormat.count(profile.getKarma()) + "§7",
						"§f" + StatFormat.age(profile.getFirstLogin(), now) + "§7"), textX, top + 22, ScoutTheme.TEXT);
			}

			if (shown.lastLogin && profile.getLastLogin() > 0) {
				ScoutTheme.text(font, "§8" + StatCollector.translateToLocalFormatted("message.hypixelscout.profile.last_login",
						StatFormat.age(profile.getLastLogin(), now)), textX, top + 34, ScoutTheme.TEXT);
			}

			ThreatScale scale = Threats.scale();
			ThreatScale.Rating rating = scale.rate(profile);
			int right = left + width - 8;
			if (!shown.threat) {
				return top + height;
			}
			if (scale.focus() == ThreatFocus.BOTH) {
				// Both dangers side by side, the fight on the left, as the cards below are laid out.
				right = threatBadge(font, "message.hypixelscout.profile.threat.beds", rating.beds(), right, top + 8) - 4;
				threatBadge(font, "message.hypixelscout.profile.threat.combat", rating.combat(), right, top + 8);
			} else {
				threatBadge(font, scale.focus() == ThreatFocus.BEDS ? "message.hypixelscout.profile.threat.beds"
						: "message.hypixelscout.profile.threat.combat", rating.overall(), right, top + 8);
			}
		}

		return top + height;
	}

	/** One threat badge ending at {@code right}; answers where it starts. */
	private static int threatBadge(FontRenderer font, String key, Threat threat, int right, int y) {
		String badge = StatCollector.translateToLocal(key) + " " + threat.colour() + threat.label();
		int x = right - ScoutTheme.width(font, badge) - 6;
		ScoutTheme.badge(font, badge, x, y, 0x60000000, ScoutTheme.TEXT_DIM);
		return x;
	}

	private static final class Stat {
		private final String label;
		private final String value;

		Stat(String label, String value) {
			this.label = label;
			this.value = value;
		}
	}

	/** Three cards side by side where they fit, one under another where they do not. */
	public static int cards(FontRenderer font, PlayerStats p, int left, int top, int width, int bottom, int opacity) {
		List<List<Stat>> allCards = Arrays.asList(
				Arrays.asList(new Stat("final_kills", "§a" + StatFormat.count(p.getFinalKills())),
						new Stat("final_deaths", "§c" + StatFormat.count(p.getFinalDeaths())),
						new Stat("fkdr", StatFormat.ratioColour(p.getFkdr()) + StatFormat.ratio(p.getFkdr())),
						new Stat("kills", "§f" + StatFormat.count(p.getKills())),
						new Stat("kdr", "§f" + StatFormat.ratio(ProfileMetrics.kdr(p)))),
				Arrays.asList(new Stat("wins", "§a" + StatFormat.count(p.getWins())),
						new Stat("losses", "§c" + StatFormat.count(p.getLosses())),
						new Stat("wlr", StatFormat.ratioColour(p.getWlr()) + StatFormat.ratio(p.getWlr())),
						new Stat("win_rate", "§f" + StatFormat.ratio(ProfileMetrics.winRate(p)) + "%"),
						new Stat("winstreak", "§f" + StatFormat.winstreak(p.getWinstreak()))),
				Arrays.asList(new Stat("beds_broken", "§a" + StatFormat.count(p.getBedsBroken())),
						new Stat("beds_lost", "§c" + StatFormat.count(p.getBedsLost())),
						new Stat("bblr", "§f" + StatFormat.ratio(ProfileMetrics.bedRatio(p))),
						new Stat("games", "§f" + StatFormat.count(ProfileMetrics.gamesPlayed(p))),
						new Stat("stars", StatFormat.star(p.getStars()))));
		String[] allTitles = {"combat", "games", "beds"};
		ScoutSettings.Profile shown = parts.get();
		boolean[] wanted = {shown.combat, shown.games, shown.beds};
		List<List<Stat>> cards = new ArrayList<List<Stat>>();
		List<String> keptTitles = new ArrayList<String>();
		for (int i = 0; i < allCards.size(); i++) {
			if (wanted[i]) {
				cards.add(allCards.get(i));
				keptTitles.add(allTitles[i]);
			}
		}
		if (cards.isEmpty()) {
			return top - GAP;
		}
		String[] titles = keptTitles.toArray(new String[0]);

		int columns = width >= 390 ? cards.size() : 1;
		int cardWidth = (width - GAP * (columns - 1)) / columns;
		int cardHeight = ScoutTheme.HEADER_HEIGHT + 6 + 5 * 11 + 3;

		for (int i = 0; i < cards.size(); i++) {
			int x = left + (i % columns) * (cardWidth + GAP);
			int y = top + (i / columns) * (cardHeight + GAP);
			if (y + cardHeight > bottom) {
				break;
			}

			ScoutTheme.panel(x, y, cardWidth, cardHeight, opacity);
			ScoutTheme.header(x, y, cardWidth, opacity);
			ScoutTheme.text(font, ScoutTheme.accentCode() + "§l"
							+ StatCollector.translateToLocal("message.hypixelscout.profile.card." + titles[i]),
					x + 7, y + 6, ScoutTheme.TEXT);

			int rowY = y + ScoutTheme.HEADER_HEIGHT + 6;
			for (Stat stat : cards.get(i)) {
				ScoutTheme.text(font, "§7" + StatCollector.translateToLocal("message.hypixelscout.profile.stat." + stat.label),
						x + 7, rowY, ScoutTheme.TEXT);
				ScoutTheme.textRight(font, stat.value, x + cardWidth - 7, rowY, ScoutTheme.TEXT);
				rowY += 11;
			}
		}

		int rows = (cards.size() + columns - 1) / columns;
		return top + rows * cardHeight + (rows - 1) * GAP;
	}

	/** Rates rather than totals: how much happens per game and per star, and where they link to. */
	public static void pace(FontRenderer font, PlayerStats p, int left, int top, int width, int bottom, int opacity) {
		ScoutSettings.Profile shown = parts.get();
		Map<String, String> socials = shown.socials ? p.getSocials() : Collections.<String, String>emptyMap();
		if (top + 30 > bottom || (!shown.pace && socials.isEmpty())) {
			return;
		}

		int height = (shown.pace ? 20 : 9) + (socials.isEmpty() ? 0 : 11);

		ScoutTheme.panel(left, top, width, height, opacity);
		if (shown.pace) {
			String pace = "§7" + StatCollector.translateToLocalFormatted("message.hypixelscout.profile.pace",
					"§f" + StatFormat.ratio(ProfileMetrics.finalsPerGame(p)) + "§7",
					"§f" + StatFormat.ratio(ProfileMetrics.finalsPerStar(p)) + "§7",
					"§f" + StatFormat.ratio(ProfileMetrics.killsPerGame(p)) + "§7",
					"§f" + StatFormat.ratio(ProfileMetrics.bedsPerGame(p)) + "§7");
			ScoutTheme.textCentred(font, ScoutTheme.fit(font, pace, width - 14), left + width / 2, top + 6, ScoutTheme.TEXT);
		}

		if (!socials.isEmpty()) {
			StringBuilder line = new StringBuilder("§8" + StatCollector.translateToLocal("message.hypixelscout.profile.linked"));
			for (Map.Entry<String, String> social : socials.entrySet()) {
				line.append(" §7").append(social.getKey().toLowerCase(Locale.ROOT)).append(" §8").append(shorten(social.getValue()));
			}
			ScoutTheme.textCentred(font, ScoutTheme.fit(font, line.toString(), width - 14), left + width / 2,
					top + (shown.pace ? 18 : 5), ScoutTheme.TEXT);
		}
	}

	/** A link is worth showing as a handle, not as a hundred characters of URL. */
	private static String shorten(String value) {
		String text = value.replaceFirst("^https?://(www\\.)?", "");
		return text.length() > 24 ? text.substring(0, 23) + "…" : text;
	}
}
