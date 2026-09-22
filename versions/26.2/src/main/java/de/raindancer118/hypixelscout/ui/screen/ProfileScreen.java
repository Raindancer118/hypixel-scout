package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.HypixelApiException;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The full profile of one player: everything the API gives, laid out to be read rather than
 * glanced at.
 *
 * <p>Works for anybody, not only the people in your game — a name without a UUID is resolved through
 * Mojang first. Both that and the stats fetch happen off the render thread; this screen only reads
 * what has arrived, and fills itself in when it does.
 */
public final class ProfileScreen extends Screen {
	private static final int MAX_WIDTH = 440;
	private static final int CARD_GAP = 8;

	private final HypixelScout mod;
	private final StatsService stats;
	private final Screen parent;

	private volatile String name;
	private volatile UUID uuid;
	private volatile String error;
	private volatile boolean resolving;

	private EditBox search;

	public ProfileScreen(HypixelScout mod, String name, UUID uuid, Screen parent) {
		super(Component.translatable("message.hypixelscout.profile.title"));
		this.mod = mod;
		this.stats = mod.stats();
		this.parent = parent;
		this.name = name;
		this.uuid = uuid;

		if (name != null && uuid != null) {
			stats.request(uuid, name);
		} else if (name != null) {
			resolve(name);
		}
	}

	private int contentWidth() {
		return Math.min(width - 24, MAX_WIDTH);
	}

	private int left() {
		return (width - contentWidth()) / 2;
	}

	@Override
	protected void init() {
		int left = left();
		int top = 10;

		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
				.bounds(left, top, 50, 20).build());

		search = new EditBox(font, left + 56, top, contentWidth() - 56 - 84, 20,
				Component.translatable("message.hypixelscout.lookup.hint"));
		search.setHint(Component.translatable("message.hypixelscout.lookup.hint"));
		search.setMaxLength(16);
		addRenderableWidget(search);

		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.lookup.go"),
						button -> lookUp(search.getValue()))
				.bounds(left + contentWidth() - 80, top, 80, 20).build());

		int bottom = height - 28;
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.refresh"), button -> {
					if (uuid != null) {
						stats.forget(uuid);
						stats.request(uuid, name);
					}
				})
				.bounds(width / 2 - 154, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.profile.plancke"),
						button -> {
							if (name != null) {
								ConfirmLinkScreen.confirmLinkNow(this, "https://plancke.io/hypixel/player/stats/"
										+ name + "#BedWars");
							}
						})
				.bounds(width / 2 - 50, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> minecraft.gui.setScreen(null))
				.bounds(width / 2 + 54, bottom, 100, 20).build());
	}

	private void lookUp(String typed) {
		String wanted = typed == null ? "" : typed.trim();
		if (!wanted.matches("\\w{1,16}") || resolving) {
			return;
		}

		mod.lookups().add(wanted);
		mod.saveSettings();
		search.setValue("");

		UUID inGame = mod.roster().uuidOf(wanted);
		name = wanted;
		error = null;
		uuid = inGame;

		if (inGame != null) {
			stats.request(inGame, wanted);
		} else {
			resolve(wanted);
		}
	}

	/** Name to UUID through Mojang, on the worker: it is a network round trip. */
	private void resolve(String wanted) {
		resolving = true;
		mod.worker().execute(() -> {
			try {
				UUID resolved = mod.mojang().uuidOf(wanted);
				if (!wanted.equals(name)) {
					return;
				}

				if (resolved == null) {
					error = I18n.get("message.hypixelscout.profile.no_account", wanted);
					return;
				}

				uuid = resolved;
				stats.request(resolved, wanted);
			} catch (HypixelApiException e) {
				error = e.getMessage();
			} finally {
				resolving = false;
			}
		});
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (search != null && search.isFocused()
				&& (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			lookUp(search.getValue());
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		int left = left();
		int top = 40;
		int middle = height / 2 - 10;

		if (name == null) {
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.profile.empty"), width / 2, middle,
					ScoutTheme.TEXT_DIM);
			return;
		}

		if (error != null) {
			ScoutTheme.textCentred(g, "§c" + error, width / 2, middle, ScoutTheme.TEXT);
			return;
		}

		PlayerStats profile = uuid == null ? null : stats.peek(uuid);
		if (profile == null) {
			String failure = uuid == null ? null : stats.failureFor(uuid);
			ScoutTheme.textCentred(g, failure != null && !stats.isPending(uuid) ? "§c" + failure
					: I18n.get("message.hypixelscout.profile.loading", name), width / 2, middle, ScoutTheme.TEXT);
			return;
		}

		if (profile.isNicked()) {
			drawHeader(g, profile, left, top);
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.profile.nicked"), width / 2, top + 64,
					ScoutTheme.TEXT_DIM);
			return;
		}

		int y = drawHeader(g, profile, left, top) + CARD_GAP;
		y = drawCards(g, profile, left, y) + CARD_GAP;
		drawPace(g, profile, left, y);
	}

	/** The head, the name as Hypixel prints it, and the account facts. Returns its bottom edge. */
	private int drawHeader(GuiGraphicsExtractor g, PlayerStats profile, int left, int top) {
		int width = contentWidth();
		int height = 52;

		ScoutTheme.panel(g, left, top, width, height, 88);
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

			Threat threat = Threat.of(profile);
			String badge = I18n.get("message.hypixelscout.profile.threat") + " " + threat.colour() + threat.label();
			ScoutTheme.badge(g, badge, left + width - 8 - ScoutTheme.width(badge) - 6, top + 8, 0x60000000,
					ScoutTheme.TEXT_DIM);
		}

		return top + height;
	}

	private record Stat(String label, String value) {
	}

	/** Three cards side by side where they fit, one under another where they do not. */
	private int drawCards(GuiGraphicsExtractor g, PlayerStats p, int left, int top) {
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

		int columns = contentWidth() >= 390 ? 3 : 1;
		int cardWidth = (contentWidth() - CARD_GAP * (columns - 1)) / columns;
		int cardHeight = ScoutTheme.HEADER_HEIGHT + 6 + 5 * 11 + 3;

		for (int i = 0; i < cards.size(); i++) {
			int x = left + (i % columns) * (cardWidth + CARD_GAP);
			int y = top + (i / columns) * (cardHeight + CARD_GAP);
			if (y + cardHeight > height - 34) {
				break;
			}

			ScoutTheme.panel(g, x, y, cardWidth, cardHeight, 80);
			ScoutTheme.header(g, x, y, cardWidth, 80);
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
		return top + rows * cardHeight + (rows - 1) * CARD_GAP;
	}

	/** Rates rather than totals: how much happens per game and per star, and where they link to. */
	private void drawPace(GuiGraphicsExtractor g, PlayerStats p, int left, int top) {
		if (top + 30 > height - 34) {
			return;
		}

		int width = contentWidth();
		Map<String, String> socials = p.getSocials();
		int height = socials.isEmpty() ? 20 : 31;

		ScoutTheme.panel(g, left, top, width, height, 80);
		String pace = "§7" + I18n.get("message.hypixelscout.profile.pace",
				"§f" + StatFormat.ratio(ProfileMetrics.finalsPerGame(p)) + "§7",
				"§f" + StatFormat.ratio(ProfileMetrics.finalsPerStar(p)) + "§7",
				"§f" + StatFormat.ratio(ProfileMetrics.killsPerGame(p)) + "§7",
				"§f" + StatFormat.ratio(ProfileMetrics.bedRatio(p) == 0 ? 0 : (double) p.getBedsBroken()
						/ Math.max(1, ProfileMetrics.gamesPlayed(p))) + "§7");
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

	/** For the client game test: the player this screen is about. */
	public UUID uuid() {
		return uuid;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
