package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.CardLines;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.ProximityWatch;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.game.ProximityAlerts;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.Threats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The enemies who just came close, one small card each at the top of the screen: who, threat,
 * FKDR, winstreak. Gone after a few seconds by themselves.
 *
 * <p>Somebody below the chosen level gets no card once their numbers are in; nicks and players not
 * looked up yet always do, since nobody can say they are harmless.
 *
 * <p>Ported from 26.2's {@code ui.hud.ProximityElement}: a Fabric HUD element attached before the
 * peek becomes a {@link RenderGameOverlayEvent.Post} {@code ALL} subscriber, registered ahead of
 * {@link PeekElement} so the peek's own dim overlay paints over it.
 */
public final class ProximityElement {
	/** Below the boss bar, where Hypixel shows nothing during a Bedwars game. */
	private static final int TOP = 22;
	private static final int GAP = 3;

	private final ProximityAlerts alerts;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;
	private BooleanSupplier hidden = new BooleanSupplier() {
		@Override
		public boolean getAsBoolean() {
			return false;
		}
	};

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

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		if (!settings.get().proximity.enabled || hidden.getAsBoolean() || client.currentScreen != null
				|| client.gameSettings.showDebugInfo) {
			return;
		}

		FontRenderer font = client.fontRendererObj;
		ScoutSettings.Card card = settings.get().cards.popup;
		float scale = (float) card.scale;
		ScaledResolution resolution = new ScaledResolution(client);

		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);
		int screenWidth = Math.round(resolution.getScaledWidth() / scale);
		int top = Math.round(TOP / scale);
		for (ProximityWatch.Popup popup : shown()) {
			List<String> lines = CardLines.lines(popup.name(), stats.peek(popup.uuid()), stats.isPending(popup.uuid()),
					stats.failureFor(popup.uuid()), Threats.scale(), card.layout(), Suspects.cardExtras(popup.name()));
			int width = LookTooltipElement.width(font, lines, card.head);
			top += LookTooltipElement.card(font, popup.uuid(), Teams.of(popup.name()), lines,
					(screenWidth - width) / 2, top, card.head) + GAP;
		}
		GlStateManager.popMatrix();
	}

	/** The popups that pass the level filter, for drawing and for the client startup test. */
	public List<ProximityWatch.Popup> shown() {
		Threat from = settings.get().proximity.from;
		List<ProximityWatch.Popup> result = new ArrayList<ProximityWatch.Popup>();
		for (ProximityWatch.Popup popup : alerts.showing()) {
			PlayerStats playerStats = stats.peek(popup.uuid());
			Threat threat = Threats.of(playerStats);
			if (!threat.isRated() || threat.compareTo(from) >= 0) {
				result.add(popup);
			}
		}
		return result;
	}
}
