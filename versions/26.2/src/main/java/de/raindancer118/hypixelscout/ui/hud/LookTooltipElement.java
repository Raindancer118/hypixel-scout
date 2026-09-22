package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The stats of whoever is under the crosshair, in a small card below it.
 *
 * <p>Needs line of sight unless that was switched off: the card is for the player you are looking
 * at, not a way to find the ones behind a wall.
 */
public final class LookTooltipElement implements HudElement {
	private static final int PADDING = 5;
	private static final int LINE = 10;
	private static final int HEAD = 16;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	public LookTooltipElement(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		ScoutSettings.Tooltip tooltip = settings.get().tooltip;
		Minecraft client = Minecraft.getInstance();

		if (!tooltip.enabled || !roster.isInGame() || client.gui.screen() != null
				|| client.getDebugOverlay().showDebugScreen()) {
			return;
		}

		AbstractClientPlayer target = LookTarget.pick(tooltip.cosine(), tooltip.throughWalls,
				delta.getGameTimeDeltaPartialTick(false));
		if (target == null) {
			return;
		}

		String name = target.getScoreboardName();
		UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = target.getUUID();
		}

		// Asking here is what covers somebody who walked in late: one shared cache, so it costs a
		// request only the first time.
		stats.request(uuid, name);

		PlayerStats playerStats = stats.peek(uuid);
		List<String> lines = StatLines.detail(name, playerStats, stats.isPending(uuid),
				stats.failureFor(uuid));

		draw(graphics, uuid, Teams.of(name), lines, tooltip.offsetY);
	}

	static void draw(GuiGraphicsExtractor graphics, UUID uuid, Teams.Team team, List<String> lines,
			int offsetY) {
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, ScoutTheme.width(line));
		}

		int width = PADDING + HEAD + 6 + textWidth + PADDING;
		int height = Math.max(HEAD, lines.size() * LINE - 1) + PADDING * 2;
		int left = (graphics.guiWidth() - width) / 2;
		// Below the crosshair, clear of it: the card must not sit where you are aiming.
		int top = graphics.guiHeight() / 2 + offsetY;

		ScoutTheme.panel(graphics, left, top, width, height, 88);
		ScoutTheme.pill(graphics, left + 1, top + 3, 2, height - 6, team.argb());
		Heads.draw(graphics, uuid, left + PADDING, top + PADDING, HEAD);

		int x = left + PADDING + HEAD + 6;
		int y = top + PADDING;
		for (String line : lines) {
			ScoutTheme.text(graphics, line, x, y, ScoutTheme.TEXT);
			y += LINE;
		}
	}
}
