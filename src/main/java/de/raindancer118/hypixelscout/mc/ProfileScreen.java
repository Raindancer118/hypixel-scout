package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.HypixelApiException;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProfileMetrics;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The full profile of one player: everything the API gives, laid out to be read rather than
 * glanced at.
 *
 * <p>Openable for anybody, not just the people in your game — the search box resolves a name
 * through Mojang first. That lookup and the stats fetch both happen off the render thread; this
 * screen only ever reads what has arrived.
 */
public final class ProfileScreen extends GuiScreen {
	private static final int PANEL = 0xC0101018;
	private static final int PANEL_EDGE = 0x60FFFFFF;
	private static final int WIDTH = 320;
	private static final int HEIGHT = 200;

	private final StatsService stats;
	private final MojangClient mojang;
	/** Where escape goes back to: the lobby list if that is what opened this, else the game. */
	private final GuiScreen parent;

	private GuiTextField search;

	private volatile String name;
	private volatile UUID uuid;
	private volatile String error;
	private volatile boolean searching;

	public ProfileScreen(StatsService stats, MojangClient mojang, String name, UUID uuid) {
		this(stats, mojang, name, uuid, null);
	}

	public ProfileScreen(StatsService stats, MojangClient mojang, String name, UUID uuid,
			GuiScreen parent) {
		this.stats = stats;
		this.mojang = mojang;
		this.parent = parent;
		this.name = name;
		this.uuid = uuid;

		if (name != null && uuid != null) {
			stats.request(uuid, name);
		}
	}

	@Override
	public void initGui() {
		int left = (width - WIDTH) / 2;
		int top = (height - HEIGHT) / 2;

		search = new GuiTextField(0, fontRendererObj, left + 8, top + 8, WIDTH - 90, 14);
		search.setMaxStringLength(16);
		search.setText(name == null ? "" : name);
		search.setFocused(name == null);

		buttonList.clear();
		buttonList.add(new GuiButton(1, left + WIDTH - 78, top + 6, 70, 18, "Look up"));

		if (parent != null) {
			buttonList.add(new GuiButton(2, left + 8, top + HEIGHT - 24, 60, 18, "Back"));
		}
	}

	@Override
	public void updateScreen() {
		search.updateCursorCounter();
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.id == 1) {
			lookUp(search.getText().trim());
			return;
		}

		if (button.id == 2) {
			mc.displayGuiScreen(parent);
		}
	}

	@Override
	protected void keyTyped(char typed, int key) throws IOException {
		if (key == 28 || key == 156) {
			lookUp(search.getText().trim());
			return;
		}

		// Escape returns to the list rather than all the way out, so a wrong click costs nothing.
		if (key == 1 && parent != null) {
			mc.displayGuiScreen(parent);
			return;
		}

		if (search.textboxKeyTyped(typed, key)) {
			return;
		}

		super.keyTyped(typed, key);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		search.mouseClicked(mouseX, mouseY, button);
		super.mouseClicked(mouseX, mouseY, button);
	}

	/**
	 * Resolves a name and asks for its stats, on a thread of its own: the Mojang call is a network
	 * round trip and would freeze the screen if it happened here.
	 */
	private void lookUp(final String wanted) {
		if (wanted.isEmpty() || searching) {
			return;
		}

		searching = true;
		error = null;
		name = wanted;
		uuid = null;

		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					UUID resolved = mojang.uuidOf(wanted);
					if (resolved == null) {
						error = "No Minecraft account is called " + wanted;
						return;
					}

					uuid = resolved;
					stats.request(resolved, wanted);
				} catch (HypixelApiException e) {
					error = e.getMessage();
				} finally {
					searching = false;
				}
			}
		}, "HypixelScout-profile");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();

		int left = (width - WIDTH) / 2;
		int top = (height - HEIGHT) / 2;

		drawRect(left, top, left + WIDTH, top + HEIGHT, PANEL);
		drawHorizontalLine(left, left + WIDTH - 1, top, PANEL_EDGE);
		drawHorizontalLine(left, left + WIDTH - 1, top + HEIGHT - 1, PANEL_EDGE);

		search.drawTextBox();
		drawBody(left + 8, top + 32);

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void drawBody(int x, int y) {
		if (error != null) {
			fontRendererObj.drawStringWithShadow("§c" + error, x, y, 0xFFFFFF);
			return;
		}

		if (name == null) {
			fontRendererObj.drawStringWithShadow("§7Type a name and press enter.", x, y,
					0xFFFFFF);
			return;
		}

		PlayerStats profile = uuid == null ? null : stats.peek(uuid);
		if (profile == null) {
			fontRendererObj.drawStringWithShadow(
					searching || uuid == null || stats.isPending(uuid)
							? "§7Looking up " + name + "…"
							: "§c" + String.valueOf(stats.failureFor(uuid)),
					x, y, 0xFFFFFF);
			return;
		}

		if (profile.isNicked()) {
			fontRendererObj.drawStringWithShadow("§d" + name + " is nicked.", x, y, 0xFFFFFF);
			fontRendererObj.drawStringWithShadow(
					"§7Hypixel has no profile under this name.", x, y + 12, 0xFFFFFF);
			return;
		}

		drawHead(x, y, profile);

		fontRendererObj.drawStringWithShadow(StatFormat.star(profile.getStars()) + " "
				+ StatsLines.rankColour(profile.getRank()) + profile.getName(), x + 28, y,
				0xFFFFFF);
		fontRendererObj.drawStringWithShadow("§7Network level §f"
				+ (int) profile.getNetworkLevel() + "§7   Karma §f"
				+ StatFormat.count(profile.getKarma()), x + 28, y + 11, 0xFFFFFF);
		fontRendererObj.drawStringWithShadow("§7Account age §f"
				+ StatFormat.age(profile.getFirstLogin(), System.currentTimeMillis()), x + 28,
				y + 22, 0xFFFFFF);

		int column = x;
		int row = y + 44;

		// Two columns of pairs: what they did on the left, what was done to them on the right.
		pair(column, row, "Wins", StatFormat.count(profile.getWins()), "§a");
		pair(column, row + 11, "Final kills", StatFormat.count(profile.getFinalKills()), "§a");
		pair(column, row + 22, "Kills", StatFormat.count(profile.getKills()), "§a");
		pair(column, row + 33, "Beds broken", StatFormat.count(profile.getBedsBroken()), "§a");
		pair(column, row + 44, "Games", StatFormat.count(ProfileMetrics.gamesPlayed(profile)),
				"§f");

		column = x + 150;
		pair(column, row, "Losses", StatFormat.count(profile.getLosses()), "§c");
		pair(column, row + 11, "Final deaths", StatFormat.count(profile.getFinalDeaths()),
				"§c");
		pair(column, row + 22, "Deaths", StatFormat.count(profile.getDeaths()), "§c");
		pair(column, row + 33, "Beds lost", StatFormat.count(profile.getBedsLost()), "§c");
		pair(column, row + 44, "Win rate",
				StatFormat.ratio(ProfileMetrics.winRate(profile)) + "%", "§f");

		int derived = row + 62;
		fontRendererObj.drawStringWithShadow("§7FKDR §f"
				+ StatFormat.ratio(profile.getFkdr()) + "   §7WLR §f"
				+ StatFormat.ratio(profile.getWlr()) + "   §7KDR §f"
				+ StatFormat.ratio(ProfileMetrics.kdr(profile)) + "   §7Beds §f"
				+ StatFormat.ratio(ProfileMetrics.bedRatio(profile)), x, derived, 0xFFFFFF);
		fontRendererObj.drawStringWithShadow("§7F/Game §f"
				+ StatFormat.ratio(ProfileMetrics.finalsPerGame(profile)) + "   §7F/Star §f"
				+ StatFormat.ratio(ProfileMetrics.finalsPerStar(profile)) + "   §7K/Game §f"
				+ StatFormat.ratio(ProfileMetrics.killsPerGame(profile)) + "   §7Streak §f"
				+ StatFormat.winstreak(profile.getWinstreak()), x, derived + 11, 0xFFFFFF);

		drawSocials(x, derived + 26, profile);
	}

	private void drawSocials(int x, int y, PlayerStats profile) {
		Map<String, String> socials = profile.getSocials();
		if (socials.isEmpty()) {
			return;
		}

		StringBuilder line = new StringBuilder("§8Linked:");
		for (Map.Entry<String, String> social : socials.entrySet()) {
			line.append(" §7").append(social.getKey().toLowerCase(Locale.ROOT))
					.append(" §8").append(shorten(social.getValue()));
		}

		fontRendererObj.drawStringWithShadow(
				fontRendererObj.trimStringToWidth(line.toString(), WIDTH - 16), x, y, 0xFFFFFF);
	}

	/** A link is worth showing as a handle, not as a hundred characters of URL. */
	private static String shorten(String value) {
		String text = value.replaceFirst("^https?://(www\\.)?", "");
		return text.length() > 24 ? text.substring(0, 23) + "…" : text;
	}

	private void pair(int x, int y, String label, String value, String colour) {
		fontRendererObj.drawStringWithShadow("§7" + label, x, y, 0xFFFFFF);
		fontRendererObj.drawStringWithShadow(colour + value, x + 92, y, 0xFFFFFF);
	}

	/**
	 * The player's face, if the client happens to have their skin — which it does for anybody in
	 * the game. For a stranger looked up by name there is nothing loaded, and a box is drawn.
	 */
	private void drawHead(int x, int y, PlayerStats profile) {
		Minecraft mc = Minecraft.getMinecraft();
		NetworkPlayerInfo info = mc.thePlayer == null || mc.thePlayer.sendQueue == null
				? null : mc.thePlayer.sendQueue.getPlayerInfo(profile.getUuid());

		if (info == null) {
			drawRect(x, y, x + 24, y + 24, 0x40FFFFFF);
			return;
		}

		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		mc.getTextureManager().bindTexture(info.getLocationSkin());
		drawScaledCustomSizeModalRect(x, y, 8.0f, 8.0f, 8, 8, 24, 24, 64.0f, 64.0f);
		drawScaledCustomSizeModalRect(x, y, 40.0f, 8.0f, 8, 8, 24, 24, 64.0f, 64.0f);
	}

	@Override
	public boolean doesGuiPauseGame() {
		// Opening a profile must not stop the game around you: you are usually still in a match.
		return false;
	}
}
