package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.mc.ui.ScoutTheme;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Everybody in the current game, one row each, and a click opens the full profile.
 *
 * <p>A panel with a header, faces, team colours and measured columns — the same treatment as the
 * in-game table, because they show the same thing and switching between them should not feel like
 * switching programs.
 *
 * <p>A Bedwars game holds sixteen players, so the list never needs to scroll and does not.
 *
 * <p>With no API key set there is nothing to list, so the screen offers somewhere to paste one
 * instead of an empty panel.
 */
public final class LobbyScreen extends GuiScreen {
	private static final int WIDTH = 360;
	private static final int PADDING = 8;
	private static final int HEAD = 10;
	private static final int BAR = 3;
	private static final int GAP = 14;

	private final StatsService stats;
	private final MojangClient mojang;
	private final RosterTracker roster;
	private final ScoutConfig config;

	private final List<RosterTracker.Member> shown = new ArrayList<RosterTracker.Member>();

	private SortMode sort;
	private GuiTextField keyField;
	private int panelLeft;
	private int panelTop;
	private int listTop;

	public LobbyScreen(StatsService stats, MojangClient mojang, RosterTracker roster,
			ScoutConfig config) {
		this.stats = stats;
		this.mojang = mojang;
		this.roster = roster;
		this.config = config;
		this.sort = config.getTableSort();
	}

	@Override
	public void initGui() {
		shown.clear();
		shown.addAll(roster.members());

		boolean needsKey = !HypixelScout.instance.getClient().hasApiKey();
		int bodyHeight = needsKey ? 46
				: PADDING + 11 + Math.max(1, shown.size()) * ScoutTheme.ROW_HEIGHT + PADDING;
		int panelHeight = ScoutTheme.HEADER_HEIGHT + bodyHeight + 26;

		panelLeft = (width - WIDTH) / 2;
		panelTop = Math.max(20, (height - panelHeight) / 2);
		listTop = panelTop + ScoutTheme.HEADER_HEIGHT + PADDING;

		buttonList.clear();
		buttonList.add(new GuiButton(1, panelLeft + WIDTH - 96, panelTop + 3, 60, 16,
				sort.name()));
		buttonList.add(new GuiButton(2, panelLeft + WIDTH - 34, panelTop + 3, 30, 16, "✕"));
		buttonList.add(new GuiButton(5, panelLeft + PADDING, panelTop + panelHeight - 22, 80, 18,
				"Settings"));

		if (needsKey) {
			keyField = new GuiTextField(3, fontRendererObj, panelLeft + PADDING, listTop + 14,
					WIDTH - PADDING * 2 - 76, 18);
			keyField.setMaxStringLength(40);
			keyField.setFocused(true);

			buttonList.add(new GuiButton(4, panelLeft + WIDTH - PADDING - 70, listTop + 14, 70, 18,
					"Save key"));
		} else {
			keyField = null;
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id == 1) {
			// Cycles rather than opening a menu: four modes are quicker to click through than to
			// pick from.
			SortMode[] modes = SortMode.values();
			sort = modes[(sort.ordinal() + 1) % modes.length];
			config.setTableSort(sort);
			button.displayString = sort.name();
			return;
		}

		if (button.id == 2) {
			mc.displayGuiScreen(null);
			return;
		}

		if (button.id == 4) {
			saveKey();
			return;
		}

		if (button.id == 5) {
			// Handing this screen over as the parent: Done on the settings returns to the list.
			mc.displayGuiScreen(new ScoutGuiFactory.ScoutConfigScreen(this));
		}
	}

	private void saveKey() {
		if (keyField == null) {
			return;
		}

		String key = keyField.getText().trim();
		try {
			UUID.fromString(key);
		} catch (IllegalArgumentException e) {
			// Hypixel keys are UUIDs; catching it here saves a round trip and a puzzled player.
			return;
		}

		config.setApiKey(key);
		HypixelScout.instance.getClient().setApiKey(key);
		stats.invalidate();
		roster.scanTabList();

		initGui();
	}

	@Override
	protected void keyTyped(char typed, int key) throws IOException {
		if (keyField != null) {
			if (key == 28 || key == 156) {
				saveKey();
				return;
			}
			if (keyField.textboxKeyTyped(typed, key)) {
				return;
			}
		}

		super.keyTyped(typed, key);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (keyField != null) {
			keyField.mouseClicked(mouseX, mouseY, button);
		}

		super.mouseClicked(mouseX, mouseY, button);

		if (button != 0 || keyField != null) {
			return;
		}

		int row = rowAt(mouseX, mouseY);
		if (row >= 0 && row < shown.size()) {
			RosterTracker.Member member = shown.get(row);
			mc.displayGuiScreen(new ProfileScreen(stats, mojang, member.getName(),
					member.getUuid(), this));
		}
	}

	/** Which row the cursor is over, or -1 outside the list. */
	private int rowAt(int mouseX, int mouseY) {
		int rowsTop = listTop + 13;
		if (mouseX < panelLeft || mouseX > panelLeft + WIDTH || mouseY < rowsTop) {
			return -1;
		}

		return (mouseY - rowsTop) / ScoutTheme.ROW_HEIGHT;
	}

	@Override
	public void updateScreen() {
		if (keyField != null) {
			keyField.updateCursorCounter();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();

		shown.clear();
		shown.addAll(roster.members());
		Collections.sort(shown, RosterSorting.comparator(stats, sort));

		boolean needsKey = keyField != null;
		int bodyHeight = needsKey ? 46
				: PADDING + 11 + Math.max(1, shown.size()) * ScoutTheme.ROW_HEIGHT + PADDING;
		int panelHeight = ScoutTheme.HEADER_HEIGHT + bodyHeight + 26;

		ScoutTheme.panel(panelLeft, panelTop, WIDTH, panelHeight);
		ScoutTheme.header(panelLeft, panelTop, WIDTH);

		String title = "§6§lSCOUT";
		String map = roster.getMap();
		if (map != null) {
			title += " §8· §f" + map + " §8"
					+ StatsOverlay.shortMode(String.valueOf(roster.getMode()));
		}
		ScoutTheme.text(title, panelLeft + PADDING, panelTop + 7, ScoutTheme.TEXT);

		if (needsKey) {
			ScoutTheme.text("§cNo API key yet.", panelLeft + PADDING, listTop,
					ScoutTheme.TEXT);
			ScoutTheme.text("§7Paste one from developer.hypixel.net:",
					panelLeft + PADDING + ScoutTheme.width("No API key yet. "), listTop,
					ScoutTheme.TEXT_DIM);
			keyField.drawTextBox();
			super.drawScreen(mouseX, mouseY, partialTicks);
			return;
		}

		drawList(mouseX, mouseY);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawList(int mouseX, int mouseY) {
		int x = panelLeft + PADDING;
		int content = WIDTH - PADDING * 2;
		int right = x + content;

		int fkdrRight = right - 30 - GAP - 44 - GAP;
		int wlrRight = right - 30 - GAP;

		ScoutTheme.text("Player", x + BAR + 4 + HEAD + 4, listTop, ScoutTheme.TEXT_FAINT);
		ScoutTheme.textRight("FKDR", fkdrRight, listTop, ScoutTheme.TEXT_FAINT);
		ScoutTheme.textRight("WLR", wlrRight, listTop, ScoutTheme.TEXT_FAINT);
		ScoutTheme.textRight("WS", right, listTop, ScoutTheme.TEXT_FAINT);
		ScoutTheme.divider(x, listTop + 10, content);

		if (shown.isEmpty()) {
			ScoutTheme.text(roster.isInBedwars() ? "§7Nobody here yet."
					: "§7Join a Bedwars game and this fills itself.",
					x, listTop + 17, ScoutTheme.TEXT_DIM);
			return;
		}

		int hovered = rowAt(mouseX, mouseY);
		int y = listTop + 13;

		for (int index = 0; index < shown.size(); index++) {
			RosterTracker.Member member = shown.get(index);
			boolean hover = index == hovered;

			if (hover) {
				ScoutTheme.fill(x, y, content, ScoutTheme.ROW_HEIGHT, ScoutTheme.HOVER);
			} else if (index % 2 == 1) {
				ScoutTheme.fill(x, y, content, ScoutTheme.ROW_HEIGHT, ScoutTheme.STRIPE);
			}

			ScoutTheme.pill(x, y + 2, BAR, ScoutTheme.ROW_HEIGHT - 4,
					Teams.colourOf(Teams.teamOf(member.getName())));
			ScoutTheme.head(member.getUuid(), x + BAR + 4, y + 3, HEAD);

			PlayerStats playerStats = stats.peek(member.getUuid());
			int textY = y + 4;

			ScoutTheme.text(nameOf(member, playerStats), x + BAR + 4 + HEAD + 4, textY,
					ScoutTheme.TEXT);
			ScoutTheme.textRight(column(playerStats, 0, member.getUuid()), fkdrRight, textY,
					ScoutTheme.TEXT);
			ScoutTheme.textRight(column(playerStats, 1, member.getUuid()), wlrRight, textY,
					ScoutTheme.TEXT);
			ScoutTheme.textRight(column(playerStats, 2, member.getUuid()), right, textY,
					ScoutTheme.TEXT);

			y += ScoutTheme.ROW_HEIGHT;
		}
	}

	private String nameOf(RosterTracker.Member member, PlayerStats playerStats) {
		if (playerStats == null) {
			return "§7" + member.getName();
		}
		if (playerStats.isNicked()) {
			return "§d" + member.getName();
		}

		return StatFormat.star(playerStats.getStars()) + " "
				+ StatsLines.rankColour(playerStats.getRank()) + member.getName();
	}

	/** Column 0 is the FKDR, 1 the W/L, 2 the winstreak; a nick fills the first and leaves the rest. */
	private String column(PlayerStats stats, int index, UUID uuid) {
		if (stats == null) {
			return index == 0 ? (this.stats.isPending(uuid) ? "§8…" : "§c!") : "";
		}
		if (stats.isNicked()) {
			return index == 0 ? "§dNICK" : "";
		}

		if (index == 0) {
			return StatFormat.ratioColour(stats.getFkdr()) + StatFormat.ratio(stats.getFkdr());
		}
		if (index == 1) {
			return StatFormat.ratioColour(stats.getWlr()) + StatFormat.ratio(stats.getWlr());
		}

		return "§f" + StatFormat.winstreak(stats.getWinstreak());
	}

	@Override
	public boolean doesGuiPauseGame() {
		// Opening the list must not stop the game around you: you are usually still in a match.
		return false;
	}
}
