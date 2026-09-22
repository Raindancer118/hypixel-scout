package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The table of everybody in the game.
 *
 * <p>Laid out as a table and not as a list of sentences: the star and name on the left, each
 * number in its own right-aligned column, a coloured bar down the side for the team. Columns are
 * measured from the widest entry in them, so the figures line up under each other and can be read
 * down rather than hunted for in a paragraph.
 *
 * <p>It draws in a game and nowhere else. In a lobby there are sixty people standing around and
 * none of them is about to break your bed.
 */
public final class StatsOverlay extends Gui {
	private static final int ROW_HEIGHT = 11;
	private static final int PADDING = 5;
	private static final int TEAM_BAR = 2;
	private static final int GAP = 10;

	private static final int PANEL = 0xC0101010;
	private static final int HEADER_RULE = 0x40FFFFFF;
	private static final int STRIPE = 0x0DFFFFFF;

	private final RosterTracker roster;
	private final StatsService stats;
	private final ScoutConfig config;

	public StatsOverlay(RosterTracker roster, StatsService stats, ScoutConfig config) {
		this.roster = roster;
		this.stats = stats;
		this.config = config;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		// One element type only, or the table would be drawn once for every part of the HUD.
		if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
			return;
		}

		if (!config.isTableEnabled() || !roster.isInBedwars()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		// The debug screen and the tab list both live in this corner; three layers of text on top
		// of each other help nobody.
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
		int nameWidth = mc.fontRendererObj.getStringWidth("Player");
		int fkdrWidth = mc.fontRendererObj.getStringWidth("FKDR");
		int wlrWidth = mc.fontRendererObj.getStringWidth("WLR");
		int streakWidth = mc.fontRendererObj.getStringWidth("WS");

		for (Row row : rows) {
			nameWidth = Math.max(nameWidth, mc.fontRendererObj.getStringWidth(row.name));
			fkdrWidth = Math.max(fkdrWidth, mc.fontRendererObj.getStringWidth(row.fkdr));
			wlrWidth = Math.max(wlrWidth, mc.fontRendererObj.getStringWidth(row.wlr));
			streakWidth = Math.max(streakWidth, mc.fontRendererObj.getStringWidth(row.streak));
		}

		int content = TEAM_BAR + 4 + nameWidth + GAP + fkdrWidth + GAP + wlrWidth + GAP
				+ streakWidth;
		String title = title();
		content = Math.max(content, mc.fontRendererObj.getStringWidth(title));

		int panelWidth = content + PADDING * 2;
		int panelHeight = PADDING * 2 + ROW_HEIGHT * (rows.size() + 2);

		ScaledResolution resolution = new ScaledResolution(mc);
		int left = Math.min(config.getTableX(),
				Math.max(0, resolution.getScaledWidth() - panelWidth));
		int top = Math.min(config.getTableY(),
				Math.max(0, resolution.getScaledHeight() - panelHeight));

		if (config.isTableBackground()) {
			drawRect(left, top, left + panelWidth, top + panelHeight, PANEL);
		}

		int x = left + PADDING;
		int y = top + PADDING;
		int right = x + content;

		mc.fontRendererObj.drawStringWithShadow(title, x, y, 0xFFFFFF);
		y += ROW_HEIGHT;

		// The column heads, then a rule under them: without it the first player reads as part of
		// the title.
		int fkdrRight = right - streakWidth - GAP - wlrWidth - GAP;
		int wlrRight = right - streakWidth - GAP;

		mc.fontRendererObj.drawStringWithShadow("§8Player", x + TEAM_BAR + 4, y, 0xFFFFFF);
		drawRightAligned(mc, "§8FKDR", fkdrRight, y);
		drawRightAligned(mc, "§8WLR", wlrRight, y);
		drawRightAligned(mc, "§8WS", right, y);
		y += ROW_HEIGHT;
		drawRect(x, y - 2, right, y - 1, HEADER_RULE);

		boolean stripe = false;
		for (Row row : rows) {
			if (stripe) {
				drawRect(x, y - 1, right, y + ROW_HEIGHT - 2, STRIPE);
			}
			stripe = !stripe;

			drawRect(x, y - 1, x + TEAM_BAR, y + ROW_HEIGHT - 2, row.teamColour);
			mc.fontRendererObj.drawStringWithShadow(row.name, x + TEAM_BAR + 4, y, 0xFFFFFF);
			drawRightAligned(mc, row.fkdr, fkdrRight, y);
			drawRightAligned(mc, row.wlr, wlrRight, y);
			drawRightAligned(mc, row.streak, right, y);

			y += ROW_HEIGHT;
		}
	}

	private void drawRightAligned(Minecraft mc, String text, int rightEdge, int y) {
		mc.fontRendererObj.drawStringWithShadow(text,
				rightEdge - mc.fontRendererObj.getStringWidth(text), y, 0xFFFFFF);
	}

	/** The title line: the mod, the map being played, and how many players are on it. */
	private String title() {
		StringBuilder title = new StringBuilder("§6§lSCOUT");

		String mode = roster.getMode();
		String map = roster.getMap();
		if (map != null) {
			title.append(" §8· §7").append(map);
		}
		if (mode != null) {
			title.append(" §8(").append(shortMode(mode)).append(')');
		}

		return title.toString();
	}

	/** {@code BEDWARS_FOUR_FOUR} is not a thing to read mid-game; {@code 4v4v4v4} is. */
	private static String shortMode(String mode) {
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
			return Collections.singletonList(new Row(0xFFFF5555,
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

		if (playerStats == null) {
			String marker = stats.failureFor(member.getUuid()) == null ? "§8…"
					: "§c!";
			return new Row(teamColour, "§7" + member.getName(), marker, "", "");
		}

		if (playerStats.isNicked()) {
			return new Row(teamColour, "§d" + member.getName(), "§dNICK", "", "");
		}

		String name = StatFormat.star(playerStats.getStars()) + " "
				+ StatsLines.rankColour(playerStats.getRank()) + member.getName();

		return new Row(teamColour, name,
				StatFormat.ratioColour(playerStats.getFkdr())
						+ StatFormat.ratio(playerStats.getFkdr()),
				config.isShowWlr() ? StatFormat.ratioColour(playerStats.getWlr())
						+ StatFormat.ratio(playerStats.getWlr()) : "",
				config.isShowWinstreak() ? "§f"
						+ StatFormat.winstreak(playerStats.getWinstreak()) : "");
	}

	/** One line of the table, already coloured, with each column kept apart. */
	private static final class Row {
		private final int teamColour;
		private final String name;
		private final String fkdr;
		private final String wlr;
		private final String streak;

		private Row(int teamColour, String name, String fkdr, String wlr, String streak) {
			this.teamColour = teamColour;
			this.name = name;
			this.fkdr = fkdr;
			this.wlr = wlr;
			this.streak = streak;
		}
	}
}
