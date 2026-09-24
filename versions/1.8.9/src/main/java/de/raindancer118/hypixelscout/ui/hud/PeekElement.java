package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.ui.ProfileView;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hold the peek key, see the stats; let go, they are gone.
 *
 * <p>Drawn on the HUD rather than opened as a screen, so nothing is paused, the mouse stays with
 * the camera and the player can keep moving and fighting while looking. Aimed at somebody, it is
 * their full profile; aimed at nobody, it is the whole game's table.
 *
 * <p>Ported from 26.2's {@code ui.hud.PeekElement}: {@code delta.getGameTimeDeltaPartialTick} has no
 * 1.8.9 equivalent on {@code EntityPlayer}'s aim helpers (see {@code game.LookTarget}'s own class
 * comment), so the pick always reads this frame's raw position — the same trade-off the rest of
 * this branch's aim code already makes.
 */
public final class PeekElement {
	/** What the overlay is showing, for the client startup test. */
	public enum Showing {
		NOTHING, PLAYER, TABLE, HINT
	}

	/** A wider cone than the tooltip's: a quick glance should not need a precise aim. */
	private static final double MIN_ANGLE = 8.0;

	private final Roster roster;
	private final StatsService stats;
	private final TableHud table;
	private final Supplier<ScoutSettings> settings;

	private volatile boolean held;
	private volatile Showing showing = Showing.NOTHING;
	private volatile String shownPlayer;

	public PeekElement(Roster roster, StatsService stats, TableHud table, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.table = table;
		this.settings = settings;
	}

	public void setHeld(boolean held) {
		this.held = held;
		if (!held) {
			showing = Showing.NOTHING;
			shownPlayer = null;
		}
	}

	public boolean isHeld() {
		return held;
	}

	public Showing showing() {
		return showing;
	}

	public String shownPlayer() {
		return shownPlayer;
	}

	/** {@link EventPriority#LOWEST}: drawn dead last, on top of the table, the tooltip, proximity
	 * popups and the suspects card — which all step aside on their own via {@code hideWhile} anyway,
	 * but the dim overlay below still needs to paint over whatever else fired this pass. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		if (!held || client.currentScreen != null || client.thePlayer == null) {
			showing = Showing.NOTHING;
			return;
		}

		ScoutSettings.Tooltip tooltip = settings.get().tooltip;
		double cosine = Math.cos(Math.toRadians(Math.max(MIN_ANGLE, tooltip.angle)));
		EntityPlayer target = LookTarget.pick(cosine, tooltip.throughWalls);

		ScaledResolution resolution = new ScaledResolution(client);
		// A light dim, enough to read against a bright map without hiding what is coming at you.
		Gui.drawRect(0, 0, resolution.getScaledWidth(), resolution.getScaledHeight(), 0x38000000);

		if (target != null) {
			drawPlayer(resolution, target);
		} else if (roster.isInGame() && !roster.members().isEmpty()) {
			drawTable(resolution);
		} else {
			drawHint(resolution);
		}
	}

	private void drawPlayer(ScaledResolution resolution, EntityPlayer target) {
		String name = target.getName();
		UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = target.getGameProfile().getId();
		}

		stats.request(uuid, name);
		PlayerStats profile = stats.peek(uuid);
		showing = Showing.PLAYER;
		shownPlayer = name;

		float scale = (float) settings.get().cards.peekScale;
		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);
		try {
			drawPlayer(name, uuid, profile, Math.round(resolution.getScaledWidth() / scale),
					Math.round(resolution.getScaledHeight() / scale));
		} finally {
			GlStateManager.popMatrix();
		}
	}

	private void drawPlayer(String name, UUID uuid, PlayerStats profile, int screenWidth, int screenHeight) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		int width = Math.min(screenWidth - 16, 420);
		int left = (screenWidth - width) / 2;
		int bottom = screenHeight - 30;

		if (profile == null) {
			String failure = stats.failureFor(uuid);
			String line = failure != null && !stats.isPending(uuid) ? "§c" + failure
					: StatCollector.translateToLocalFormatted("message.hypixelscout.profile.loading", name);
			int top = screenHeight / 2 - 16;
			ScoutTheme.panel(left, top, width, 24, 92);
			ScoutTheme.textCentred(font, line, left + width / 2, top + 8, ScoutTheme.TEXT);
			return;
		}

		// Header plus one row of cards where three fit side by side; centred as a block.
		int cardsHeight = profile.isNicked() ? 0 : (width >= 390 ? 84 + ProfileView.GAP : 0);
		int top = Math.max(8, (screenHeight - 52 - cardsHeight) / 2);

		int y = ProfileView.header(font, name, uuid, profile, left, top, width, 94);
		if (!profile.isNicked()) {
			y = ProfileView.cards(font, profile, left, y + ProfileView.GAP, width, bottom, 92);
			ProfileView.pace(font, profile, left, y + ProfileView.GAP, width, bottom, 92);
		}
	}

	private void drawTable(ScaledResolution resolution) {
		showing = Showing.TABLE;
		shownPlayer = null;

		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		TableHud.Layout layout = table.measure(font);
		float scale = Math.min(1.0f, Math.min((resolution.getScaledWidth() - 16) / (float) layout.width(),
				(resolution.getScaledHeight() - 16) / (float) layout.height()));
		scale = Math.max(0.5f, scale);

		int x = Math.round((resolution.getScaledWidth() / scale - layout.width()) / 2);
		int y = Math.round((resolution.getScaledHeight() / scale - layout.height()) / 2);

		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);
		table.draw(font, layout, x, y);
		GlStateManager.popMatrix();
	}

	private void drawHint(ScaledResolution resolution) {
		showing = Showing.HINT;
		shownPlayer = null;

		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		String hint = StatCollector.translateToLocal("message.hypixelscout.peek.hint");
		int width = ScoutTheme.width(font, hint) + 16;
		int left = (resolution.getScaledWidth() - width) / 2;
		int top = resolution.getScaledHeight() / 2 + 20;
		ScoutTheme.panel(left, top, width, 20, 90);
		ScoutTheme.textCentred(font, hint, resolution.getScaledWidth() / 2, top + 6, ScoutTheme.TEXT_DIM);
	}
}
