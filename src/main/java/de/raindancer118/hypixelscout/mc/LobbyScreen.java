package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Everybody in the current game, one line each, and a click opens the full profile.
 *
 * <p>The short line is what you want between rounds; the profile behind it is what you want when
 * one of them turns out to be worth a closer look. Rows are laid out here rather than with a
 * {@code GuiSlot}: a Bedwars game holds sixteen players, which fits on any screen without
 * scrolling, and a plain list keeps the click handling readable.
 *
 * <p>With no API key set there is nothing to show, so the screen offers somewhere to paste one
 * instead of an empty list.
 */
public final class LobbyScreen extends GuiScreen {
	private static final int PANEL = 0xC0101018;
	private static final int ROW_HEIGHT = 14;
	private static final int WIDTH = 340;
	private static final int TOP = 40;

	private final StatsService stats;
	private final MojangClient mojang;
	private final RosterTracker roster;
	private final ScoutConfig config;

	private final List<RosterTracker.Member> shown = new ArrayList<RosterTracker.Member>();

	private SortMode sort;
	private GuiTextField keyField;

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
		int left = (width - WIDTH) / 2;

		buttonList.clear();
		buttonList.add(new GuiButton(1, left + WIDTH - 90, 14, 90, 18, "Sort: " + sort.name()));
		buttonList.add(new GuiButton(2, left, height - 28, 70, 18, "Close"));

		if (!HypixelScout.instance.getClient().hasApiKey()) {
			keyField = new GuiTextField(3, fontRendererObj, left, TOP + 24, WIDTH - 80, 18);
			keyField.setMaxStringLength(40);
			keyField.setFocused(true);

			buttonList.add(new GuiButton(4, left + WIDTH - 74, TOP + 24, 74, 18, "Save key"));
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id == 1) {
			// Cycles rather than opening a menu: four modes are quicker to click through than to
			// pick from.
			SortMode[] modes = SortMode.values();
			sort = modes[(sort.ordinal() + 1) % modes.length];
			button.displayString = "Sort: " + sort.name();
			return;
		}

		if (button.id == 2) {
			mc.displayGuiScreen(null);
			return;
		}

		if (button.id == 4) {
			saveKey();
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

		keyField = null;
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

		if (button != 0) {
			return;
		}

		int row = rowAt(mouseY);
		if (row >= 0 && row < shown.size()) {
			RosterTracker.Member member = shown.get(row);
			mc.displayGuiScreen(new ProfileScreen(stats, mojang, member.getName(),
					member.getUuid(), this));
		}
	}

	private int rowAt(int mouseY) {
		int listTop = listTop();
		if (mouseY < listTop) {
			return -1;
		}

		return (mouseY - listTop) / ROW_HEIGHT;
	}

	private int listTop() {
		return keyField == null ? TOP + 12 : TOP + 52;
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

		int left = (width - WIDTH) / 2;

		shown.clear();
		shown.addAll(roster.members());
		Collections.sort(shown, RosterSorting.comparator(stats, sort));

		int listTop = listTop();
		drawRect(left - 4, TOP - 6, left + WIDTH + 4,
				listTop + Math.max(1, shown.size()) * ROW_HEIGHT + 4, PANEL);

		fontRendererObj.drawStringWithShadow("§6Hypixel Scout §8· §7"
				+ shown.size() + " players in this game", left, 20, 0xFFFFFF);

		if (keyField != null) {
			fontRendererObj.drawStringWithShadow(
					"§cNo API key yet. §7Paste one from developer.hypixel.net:",
					left, TOP + 10, 0xFFFFFF);
			keyField.drawTextBox();
		}

		if (shown.isEmpty()) {
			fontRendererObj.drawStringWithShadow(
					roster.isInBedwars() ? "§7Nobody here yet."
							: "§7Join a Bedwars game and this fills itself.",
					left, listTop + 2, 0xFFFFFF);
		}

		int hovered = rowAt(mouseY);
		for (int index = 0; index < shown.size(); index++) {
			drawRow(left, listTop + index * ROW_HEIGHT, shown.get(index),
					index == hovered && mouseX >= left - 4 && mouseX <= left + WIDTH + 4);
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawRow(int x, int y, RosterTracker.Member member, boolean hovered) {
		if (hovered) {
			drawRect(x - 2, y - 1, x + WIDTH + 2, y + ROW_HEIGHT - 2, 0x30FFFFFF);
		}

		drawHead(member.getUuid(), x, y);

		PlayerStats playerStats = stats.peek(member.getUuid());
		String team = Teams.teamOf(member.getName());
		String name = (team == null ? "" : "§8" + team.charAt(0) + " ")
				+ nameOf(member, playerStats);

		fontRendererObj.drawStringWithShadow(name, x + 12, y + 1, 0xFFFFFF);

		String summary = summaryOf(playerStats, member.getUuid());
		fontRendererObj.drawStringWithShadow(summary,
				x + WIDTH - fontRendererObj.getStringWidth(summary), y + 1, 0xFFFFFF);
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

	private String summaryOf(PlayerStats playerStats, UUID uuid) {
		if (playerStats == null) {
			return stats.isPending(uuid) ? "§8looking up…" : "§8—";
		}
		if (playerStats.isNicked()) {
			return "§dnicked";
		}

		return StatFormat.ratioColour(playerStats.getFkdr())
				+ StatFormat.ratio(playerStats.getFkdr()) + "§7 fkdr   "
				+ StatFormat.ratioColour(playerStats.getWlr())
				+ StatFormat.ratio(playerStats.getWlr()) + "§7 wlr   §f"
				+ StatFormat.winstreak(playerStats.getWinstreak()) + "§7 ws";
	}

	private void drawHead(UUID uuid, int x, int y) {
		Minecraft minecraft = Minecraft.getMinecraft();
		NetworkPlayerInfo info = minecraft.thePlayer == null
				|| minecraft.thePlayer.sendQueue == null
				? null : minecraft.thePlayer.sendQueue.getPlayerInfo(uuid);

		if (info == null) {
			return;
		}

		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		minecraft.getTextureManager().bindTexture(info.getLocationSkin());
		drawScaledCustomSizeModalRect(x, y, 8.0f, 8.0f, 8, 8, 9, 9, 64.0f, 64.0f);
		drawScaledCustomSizeModalRect(x, y, 40.0f, 8.0f, 8, 8, 9, 9, 64.0f, 64.0f);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
