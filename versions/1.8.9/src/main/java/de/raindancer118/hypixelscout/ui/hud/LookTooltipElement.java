package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.CardLines;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.Threats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The stats of whoever is under the crosshair, in a small card below it.
 *
 * <p>Needs line of sight unless that was switched off: the card is for the player you are looking
 * at, not a way to find the ones behind a wall.
 *
 * <p>Ported from 26.2's {@code ui.hud.LookTooltipElement}: fires on {@link
 * RenderGameOverlayEvent.Post}'s {@code CROSSHAIRS} type, exactly where the old
 * {@code 1.8.9-support} branch's {@code LookTargetTooltip} drew, rather than a Fabric HUD element
 * chained "after the crosshair".
 */
public final class LookTooltipElement {
	private static final int PADDING = 5;
	private static final int LINE = 10;
	private static final int HEAD = 16;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	private BooleanSupplier hidden = new BooleanSupplier() {
		@Override
		public boolean getAsBoolean() {
			return false;
		}
	};

	public LookTooltipElement(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	/** Steps aside while the peek overlay shows the same player in full. */
	public LookTooltipElement hideWhile(BooleanSupplier condition) {
		hidden = condition;
		return this;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
			return;
		}

		ScoutSettings.Tooltip tooltip = settings.get().tooltip;
		Minecraft client = Minecraft.getMinecraft();

		if (!tooltip.enabled || hidden.getAsBoolean() || !roster.isInGame() || client.currentScreen != null
				|| client.gameSettings.showDebugInfo) {
			return;
		}

		EntityPlayer target = LookTarget.pick(tooltip.cosine(), tooltip.throughWalls);
		if (target == null) {
			return;
		}

		String name = target.getName();
		UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = target.getGameProfile().getId();
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

		draw(new ScaledResolution(client), uuid, Teams.of(name), lines, tooltip.offsetY, card);
	}

	static void draw(ScaledResolution resolution, UUID uuid, Teams.Team team, List<String> lines, int offsetY,
			ScoutSettings.Card card) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		float scale = (float) card.scale;
		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);
		// Below the crosshair, clear of it: the card must not sit where you are aiming.
		int screenWidth = Math.round(resolution.getScaledWidth() / scale);
		int middle = Math.round((resolution.getScaledHeight() / 2f + offsetY) / scale);
		card(font, uuid, team, lines, (screenWidth - width(font, lines, card.head)) / 2, middle, card.head);
		GlStateManager.popMatrix();
	}

	/** How wide {@link #card} draws these lines. */
	static int width(FontRenderer font, List<String> lines, boolean head) {
		int textWidth = 0;
		for (String line : lines) {
			textWidth = Math.max(textWidth, ScoutTheme.width(font, line));
		}
		return PADDING + (head ? HEAD + 6 : 2) + textWidth + PADDING;
	}

	/** A card with a head (or not), the team's colour and the lines; returns its height. */
	static int card(FontRenderer font, UUID uuid, Teams.Team team, List<String> lines, int left, int top,
			boolean head) {
		int width = width(font, lines, head);
		int height = Math.max(head ? HEAD : 0, lines.size() * LINE - 1) + PADDING * 2;

		ScoutTheme.panel(left, top, width, height, 88);
		ScoutTheme.pill(left + 1, top + 3, 2, height - 6, team.argb());
		if (head) {
			Heads.draw(uuid, left + PADDING, top + PADDING, HEAD);
		}

		int x = left + PADDING + (head ? HEAD + 6 : 2);
		int y = top + PADDING;
		for (String line : lines) {
			ScoutTheme.text(font, line, x, y, ScoutTheme.TEXT);
			y += LINE;
		}
		return height;
	}
}
