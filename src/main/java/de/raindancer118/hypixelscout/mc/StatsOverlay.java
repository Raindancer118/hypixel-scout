package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.HudVisibility;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.mc.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The in-game table: a panel you open and close, not text printed onto the screen.
 *
 * <p>Drawn with the same panel, header and row treatment as the screens, so opening the lobby list
 * does not feel like leaving the mod. Faces on the left, a bar in the team's colour, each figure
 * right-aligned in a measured column under its heading.
 *
 * <p>It is closed until asked for. Which key, and whether it closes itself again, is
 * {@link HudMode}.
 */
public final class StatsOverlay extends Gui {
	private static final int PADDING = 6;
	private static final int HEAD = 8;
	private static final int BAR = 2;
	private static final int GAP = 12;

	private final RosterTracker roster;
	private final StatsService stats;
	private final ScoutConfig config;

	private boolean opened;
	private boolean keyHeld;

	public StatsOverlay(RosterTracker roster, StatsService stats, ScoutConfig config) {
		this.roster = roster;
		this.stats = stats;
		this.config = config;
	}

	/** The key that opens and closes it. Returns what the table is now, for the chat line. */
	public boolean toggle() {
		opened = !opened;
		return opened;
	}

	public boolean isOpen() {
		return opened;
	}

	/** Held for the modes that ask for it; set from the key handler each tick. */
	public void setKeyHeld(boolean held) {
		this.keyHeld = held;
	}

	/** Leaving a game closes it, so the next one does not start with the last one's panel up. */
	public void close() {
		opened = false;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		// One element type only, or the panel would be drawn once for every part of the HUD.
		if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
			return;
		}

		if (!HudVisibility.visible(config.getTableMode(), roster.isInBedwars(), opened, keyHeld,
				roster.millisSinceStart())) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		// The debug screen and the tab list both live in this corner; three layers on top of each
		// other help nobody.
		if (mc.gameSettings.showDebugInfo || mc.gameSettings.keyBindPlayerList.isKeyDown()) {
			return;
		}

		List<Row> rows = rows();
		if (rows.isEmpty()) {
			return;
		}

		draw(mc, rows);
	}

	private void draw(Minecraft mc, List<Row> rows) {
		int nameWidth = ScoutTheme.width("Player");
		int fkdrWidth = ScoutTheme.width("FKDR");
		int wlrWidth = ScoutTheme.width("WLR");
		int streakWidth = ScoutTheme.width("WS");

		for (Row row : rows) {
			nameWidth = Math.max(nameWidth, ScoutTheme.width(row.name));
			fkdrWidth = Math.max(fkdrWidth, ScoutTheme.width(row.fkdr));
			wlrWidth = Math.max(wlrWidth, ScoutTheme.width(row.wlr));
			streakWidth = Math.max(streakWidth, ScoutTheme.width(row.streak));
		}

		int content = BAR + 3 + HEAD + 4 + nameWidth + GAP + fkdrWidth + GAP + wlrWidth + GAP
				+ streakWidth;
		content = Math.max(content, ScoutTheme.width(title()) + 30);

		int panelWidth = content + PADDING * 2;
		int panelHeight = ScoutTheme.HEADER_HEIGHT + PADDING + 11
				+ rows.size() * ScoutTheme.ROW_HEIGHT + PADDING;

		ScaledResolution resolution = new ScaledResolution(mc);
		int left = Math.min(config.getTableX(),
				Math.max(0, resolution.getScaledWidth() - panelWidth));
		int top = Math.min(config.getTableY(),
				Math.max(0, resolution.getScaledHeight() - panelHeight));

		ScoutTheme.panel(left, top, panelWidth, panelHeight);
		ScoutTheme.header(left, top, panelWidth);

		int x = left + PADDING;
		int right = x + content;

		ScoutTheme.text(title(), x, top + 7, ScoutTheme.TEXT);
		ScoutTheme.textRight(rows.size() + " §8players", right, top + 7, ScoutTheme.TEXT_DIM);

		int y = top + ScoutTheme.HEADER_HEIGHT + PADDING;
		int fkdrRight = right - streakWidth - GAP - wlrWidth - GAP;
		int wlrRight = right - streakWidth - GAP;

		ScoutTheme.text("Player", x + BAR + 3 + HEAD + 4, y, ScoutTheme.TEXT_FAINT);
		ScoutTheme.textRight("FKDR", fkdrRight, y, ScoutTheme.TEXT_FAINT);
		ScoutTheme.textRight("WLR", wlrRight, y, ScoutTheme.TEXT_FAINT);
		ScoutTheme.textRight("WS", right, y, ScoutTheme.TEXT_FAINT);
		y += 10;
		ScoutTheme.divider(x, y, content);
		y += 2;

		boolean stripe = false;
		for (Row row : rows) {
			if (stripe) {
				ScoutTheme.fill(x, y, content, ScoutTheme.ROW_HEIGHT, ScoutTheme.STRIPE);
			}
			stripe = !stripe;

			ScoutTheme.pill(x, y + 2, BAR, ScoutTheme.ROW_HEIGHT - 4, row.teamColour);
			ScoutTheme.head(row.uuid, x + BAR + 3, y + 4, HEAD);

			int textY = y + 4;
			ScoutTheme.text(row.name, x + BAR + 3 + HEAD + 4, textY, ScoutTheme.TEXT);
			ScoutTheme.textRight(row.fkdr, fkdrRight, textY, ScoutTheme.TEXT);
			ScoutTheme.textRight(row.wlr, wlrRight, textY, ScoutTheme.TEXT);
			ScoutTheme.textRight(row.streak, right, textY, ScoutTheme.TEXT);

			y += ScoutTheme.ROW_HEIGHT;
		}
	}

	/** The title line: the mod, and the map being played. */
	private String title() {
		StringBuilder title = new StringBuilder("§6§lSCOUT");

		String map = roster.getMap();
		String mode = roster.getMode();
		if (map != null) {
			title.append(" §8· §f").append(map);
		}
		if (mode != null) {
			title.append(" §8").append(shortMode(mode));
		}

		return title.toString();
	}

	/** {@code BEDWARS_FOUR_FOUR} is not a thing to read mid-game; {@code 4v4v4v4} is. */
	static String shortMode(String mode) {
		String name = mode.toUpperCase(Locale.ROOT).replace("BEDWARS_", "");

		if (name.startsWith("EIGHT_ONE")) {
			return "solo";
		}
		if (name.startsWith("EIGHT_TWO")) {
			return "doubles";
		}
		if (name.startsWith("FOUR_THREE")) {
			return "3v3v3v3";
		}
		if (name.startsWith("FOUR_FOUR")) {
			return "4v4v4v4";
		}
		if (name.startsWith("TWO_FOUR")) {
			return "4v4";
		}

		return name.toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private List<Row> rows() {
		if (!HypixelScout.instance.getClient().hasApiKey()) {
			// One line rather than one failed row per player: with no key nothing can be looked
			// up, and sixteen identical errors say it no better than one sentence does.
			return Collections.singletonList(new Row(null, ScoutTheme.BAD,
					"§cNo API key §8— §7/scout key <key>", "", "", ""));
		}

		List<RosterTracker.Member> members = new ArrayList<RosterTracker.Member>();
		for (RosterTracker.Member member : roster.members()) {
			if (config.isHideOwnTeam() && Teams.isOwnTeam(member.getName())) {
				continue;
			}
			members.add(member);
		}

		Collections.sort(members, RosterSorting.comparator(stats, config.getTableSort()));

		List<Row> rows = new ArrayList<Row>(members.size());
		for (RosterTracker.Member member : members) {
			if (rows.size() >= config.getTableMaxRows()) {
				break;
			}

			rows.add(row(member));
		}

		return rows;
	}

	private Row row(RosterTracker.Member member) {
		int teamColour = Teams.colourOf(Teams.teamOf(member.getName()));
		PlayerStats playerStats = stats.peek(member.getUuid());
		UUID uuid = member.getUuid();

		if (playerStats == null) {
			String marker = stats.failureFor(uuid) == null ? "§8…" : "§c!";
			return new Row(uuid, teamColour, "§7" + member.getName(), marker, "", "");
		}

		if (playerStats.isNicked()) {
			return new Row(uuid, teamColour, "§d" + member.getName(), "§dNICK", "", "");
		}

		String name = StatFormat.star(playerStats.getStars()) + " "
				+ StatsLines.rankColour(playerStats.getRank()) + member.getName();

		return new Row(uuid, teamColour, name,
				StatFormat.ratioColour(playerStats.getFkdr())
						+ StatFormat.ratio(playerStats.getFkdr()),
				config.isShowWlr() ? StatFormat.ratioColour(playerStats.getWlr())
						+ StatFormat.ratio(playerStats.getWlr()) : "",
				config.isShowWinstreak() ? "§f"
						+ StatFormat.winstreak(playerStats.getWinstreak()) : "");
	}

	/** One line of the table, already coloured, with each column kept apart. */
	private static final class Row {
		private final UUID uuid;
		private final int teamColour;
		private final String name;
		private final String fkdr;
		private final String wlr;
		private final String streak;

		private Row(UUID uuid, int teamColour, String name, String fkdr, String wlr,
				String streak) {
			this.uuid = uuid;
			this.teamColour = teamColour;
			this.name = name;
			this.fkdr = fkdr;
			this.wlr = wlr;
			this.streak = streak;
		}
	}
}
