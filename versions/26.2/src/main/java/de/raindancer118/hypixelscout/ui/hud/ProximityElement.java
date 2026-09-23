package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProximityWatch;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.game.ProximityAlerts;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Threats;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The enemies who just came close, one small card each at the top of the screen: who, threat,
 * FKDR, winstreak. Gone after a few seconds by themselves.
 *
 * <p>Somebody below the chosen level gets no card once their numbers are in; nicks and players not
 * looked up yet always do, since nobody can say they are harmless.
 */
public final class ProximityElement implements HudElement {
	/** Below the boss bar, where Hypixel shows nothing during a Bedwars game. */
	private static final int TOP = 22;
	private static final int GAP = 3;

	private final ProximityAlerts alerts;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;
	private BooleanSupplier hidden = () -> false;

	public ProximityElement(ProximityAlerts alerts, StatsService stats, Supplier<ScoutSettings> settings) {
		this.alerts = alerts;
		this.stats = stats;
		this.settings = settings;
	}

	/** Steps aside while the peek overlay covers the screen. */
	public ProximityElement hideWhile(BooleanSupplier condition) {
		hidden = condition;
		return this;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (!settings.get().proximity.enabled || hidden.getAsBoolean() || client.gui.screen() != null
				|| client.getDebugOverlay().showDebugScreen()) {
			return;
		}

		int top = TOP;
		for (ProximityWatch.Popup popup : shown()) {
			List<String> lines = StatLines.brief(popup.name(), stats.peek(popup.uuid()),
					stats.isPending(popup.uuid()), stats.failureFor(popup.uuid()), Threats.scale());
			int width = LookTooltipElement.width(lines);
			top += LookTooltipElement.card(graphics, popup.uuid(), Teams.of(popup.name()), lines,
					(graphics.guiWidth() - width) / 2, top) + GAP;
		}
	}

	/** The popups that pass the level filter, for drawing and for the client game test. */
	public List<ProximityWatch.Popup> shown() {
		Threat from = settings.get().proximity.from;
		return alerts.showing().stream().filter(popup -> {
			PlayerStats playerStats = stats.peek(popup.uuid());
			Threat threat = Threats.of(playerStats);
			return !threat.isRated() || threat.compareTo(from) >= 0;
		}).toList();
	}
}
