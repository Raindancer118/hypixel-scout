package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.CardLines;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Threats;
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

	private java.util.function.BooleanSupplier hidden = () -> false;

	/** Steps aside while the peek overlay shows the same player in full. */
	public LookTooltipElement hideWhile(java.util.function.BooleanSupplier condition) {
		hidden = condition;
		return this;
	}

	public LookTooltipElement(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		ScoutSettings.Tooltip tooltip = settings.get().tooltip;
		Minecraft client = Minecraft.getInstance();

		if (!tooltip.enabled || hidden.getAsBoolean() || !roster.isInGame() || client.gui.screen() != null
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
		// request only the first time. Not in the waiting lobby, where nobody is looked up.
		if (roster.hasStarted()) {
			stats.request(uuid, name);
		}

		PlayerStats playerStats = stats.peek(uuid);
		ScoutSettings.Card card = settings.get().cards.tooltip;
		List<String> lines = CardLines.lines(name, playerStats, stats.isPending(uuid), stats.failureFor(uuid),
				Threats.scale(), card.layout(), Suspects.cardExtras(name));

		draw(graphics, uuid, Teams.of(name), lines, tooltip.offsetY, card);
	}

	static void draw(GuiGraphicsExtractor graphics, UUID uuid, Teams.Team team, List<String> lines,
			int offsetY, ScoutSettings.Card card) {
		float scale = (float) card.scale;
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		// Below the crosshair, clear of it: the card must not sit where you are aiming.
		int screenWidth = Math.round(graphics.guiWidth() / scale);
		int middle = Math.round((graphics.guiHeight() / 2f + offsetY) / scale);
		card(graphics, uuid, team, lines, (screenWidth - width(lines, card.head)) / 2, middle, card.head);
		graphics.pose().popMatrix();
	}

	/** How wide {@link #card} draws these lines. */
	static int width(List<String> lines, boolean head) {
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, ScoutTheme.width(line));
		}
		return PADDING + (head ? HEAD + 6 : 2) + textWidth + PADDING;
	}

	/** A card with a head (or not), the team's colour and the lines; returns its height. */
	static int card(GuiGraphicsExtractor graphics, UUID uuid, Teams.Team team, List<String> lines, int left,
			int top, boolean head) {
		int width = width(lines, head);
		int height = Math.max(head ? HEAD : 0, lines.size() * LINE - 1) + PADDING * 2;

		ScoutTheme.panel(graphics, left, top, width, height, 88);
		ScoutTheme.pill(graphics, left + 1, top + 3, 2, height - 6, team.argb());
		if (head) {
			Heads.draw(graphics, uuid, left + PADDING, top + PADDING, HEAD);
		}

		int x = left + PADDING + (head ? HEAD + 6 : 2);
		int y = top + PADDING;
		for (String line : lines) {
			ScoutTheme.text(graphics, line, x, y, ScoutTheme.TEXT);
			y += LINE;
		}
		return height;
	}
}
