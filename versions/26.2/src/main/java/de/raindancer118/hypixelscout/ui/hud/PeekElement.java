package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.ui.ProfileView;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hold the peek key, see the stats; let go, they are gone.
 *
 * <p>Drawn on the HUD rather than opened as a screen, so nothing is paused, the mouse stays with
 * the camera and the player can keep moving and fighting while looking. Aimed at somebody, it is
 * their full profile; aimed at nobody, it is the whole game's table.
 */
public final class PeekElement implements HudElement {
	/** What the overlay is showing, for the client game test. */
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (!held || client.gui.screen() != null || client.player == null) {
			showing = Showing.NOTHING;
			return;
		}

		ScoutSettings.Tooltip tooltip = settings.get().tooltip;
		double cosine = Math.cos(Math.toRadians(Math.max(MIN_ANGLE, tooltip.angle)));
		AbstractClientPlayer target = LookTarget.pick(cosine, tooltip.throughWalls,
				delta.getGameTimeDeltaPartialTick(false));

		// A light dim, enough to read against a bright map without hiding what is coming at you.
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0x38000000);

		if (target != null) {
			drawPlayer(graphics, target);
		} else if (roster.isInGame() && !roster.members().isEmpty()) {
			drawTable(graphics);
		} else {
			drawHint(graphics);
		}
	}

	private void drawPlayer(GuiGraphicsExtractor graphics, AbstractClientPlayer target) {
		String name = target.getScoreboardName();
		UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = target.getUUID();
		}

		stats.request(uuid, name);
		PlayerStats profile = stats.peek(uuid);
		showing = Showing.PLAYER;
		shownPlayer = name;

		float scale = (float) settings.get().cards.peekScale;
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		try {
			drawPlayer(graphics, name, uuid, profile, Math.round(graphics.guiWidth() / scale),
					Math.round(graphics.guiHeight() / scale));
		} finally {
			graphics.pose().popMatrix();
		}
	}

	private void drawPlayer(GuiGraphicsExtractor graphics, String name, UUID uuid, PlayerStats profile,
			int screenWidth, int screenHeight) {
		int width = Math.min(screenWidth - 16, 420);
		int left = (screenWidth - width) / 2;
		int bottom = screenHeight - 30;

		if (profile == null) {
			String failure = stats.failureFor(uuid);
			String line = failure != null && !stats.isPending(uuid) ? "§c" + failure
					: I18n.get("message.hypixelscout.profile.loading", name);
			int top = screenHeight / 2 - 16;
			ScoutTheme.panel(graphics, left, top, width, 24, 92);
			ScoutTheme.textCentred(graphics, line, left + width / 2, top + 8, ScoutTheme.TEXT);
			return;
		}

		// Header plus one row of cards where three fit side by side; centred as a block.
		int cardsHeight = profile.isNicked() ? 0 : (width >= 390 ? 84 + ProfileView.GAP : 0);
		int top = Math.max(8, (screenHeight - 52 - cardsHeight) / 2);

		int y = ProfileView.header(graphics, name, uuid, profile, left, top, width, 94);
		if (!profile.isNicked()) {
			y = ProfileView.cards(graphics, profile, left, y + ProfileView.GAP, width, bottom, 92);
			ProfileView.pace(graphics, profile, left, y + ProfileView.GAP, width, bottom, 92);
		}
	}

	private void drawTable(GuiGraphicsExtractor graphics) {
		showing = Showing.TABLE;
		shownPlayer = null;

		TableHud.Layout layout = table.measure();
		float scale = Math.min(1.0f, Math.min((graphics.guiWidth() - 16) / (float) layout.width(),
				(graphics.guiHeight() - 16) / (float) layout.height()));
		scale = Math.max(0.5f, scale);

		int x = Math.round((graphics.guiWidth() / scale - layout.width()) / 2);
		int y = Math.round((graphics.guiHeight() / scale - layout.height()) / 2);

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		table.draw(graphics, layout, x, y);
		graphics.pose().popMatrix();
	}

	private void drawHint(GuiGraphicsExtractor graphics) {
		showing = Showing.HINT;
		shownPlayer = null;

		String hint = I18n.get("message.hypixelscout.peek.hint");
		int width = ScoutTheme.width(hint) + 16;
		int left = (graphics.guiWidth() - width) / 2;
		int top = graphics.guiHeight() / 2 + 20;
		ScoutTheme.panel(graphics, left, top, width, 20, 90);
		ScoutTheme.textCentred(graphics, hint, graphics.guiWidth() / 2, top + 6, ScoutTheme.TEXT_DIM);
	}
}
