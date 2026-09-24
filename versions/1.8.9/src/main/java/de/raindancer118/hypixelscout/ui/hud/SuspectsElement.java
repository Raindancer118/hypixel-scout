package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.CheatSensor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The suspects card on the HUD, while a game is on and somebody is suspect enough to show. Out of
 * the way while its editor is open, the peek key is held, the debug screen or the tab list is up.
 *
 * <p>Ported from 26.2's {@code ui.hud.SuspectsElement}: a {@link RenderGameOverlayEvent.Post}
 * {@code ALL} subscriber rather than a Fabric HUD element chained just before the peek. Its editor
 * ({@code SuspectsEditorScreen}) is a later phase's screen work, not part of this port; the check
 * below is written against the class name so wiring it in later needs no change here.
 */
public final class SuspectsElement {
	private final CheatSensor cheats;
	private final Supplier<ScoutSettings> settings;
	private final Roster roster;
	private BooleanSupplier hidden = new BooleanSupplier() {
		@Override
		public boolean getAsBoolean() {
			return false;
		}
	};

	public SuspectsElement(CheatSensor cheats, Supplier<ScoutSettings> settings, Roster roster) {
		this.cheats = cheats;
		this.settings = settings;
		this.roster = roster;
	}

	public SuspectsElement hideWhile(BooleanSupplier condition) {
		hidden = condition;
		return this;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		ScoutSettings.Cheats config = settings.get().cheats;
		if (!config.enabled || !config.hud.enabled || !roster.isInGame() || hidden.getAsBoolean()
				|| isSuspectsEditorOpen(client) || client.gameSettings.showDebugInfo
				|| client.gameSettings.keyBindPlayerList.isKeyDown()) {
			return;
		}

		List<Suspicion.Suspect> shown = SuspectsHud.shown(cheats.suspects(), config.hud);
		if (!shown.isEmpty()) {
			draw(new ScaledResolution(client), shown, settings.get());
		}
	}

	/** The suspects editor is a later phase's screen; nothing of that name exists yet to collide with. */
	private static boolean isSuspectsEditorOpen(Minecraft client) {
		return client.currentScreen != null
				&& client.currentScreen.getClass().getSimpleName().equals("SuspectsEditorScreen");
	}

	/** Shared with the editor, so what is positioned there is exactly what appears here. */
	static SuspectsHud.Layout draw(ScaledResolution resolution, List<Suspicion.Suspect> shown, ScoutSettings settings) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		ScoutSettings.Hud hud = settings.cheats.hud;
		float scale = (float) hud.scale;
		SuspectsHud.Layout layout = SuspectsHud.measure(font, shown, hud);
		int screenWidth = Math.round(resolution.getScaledWidth() / scale);
		int screenHeight = Math.round(resolution.getScaledHeight() / scale);
		int x = hud.placement.x(screenWidth, layout.width());
		int y = hud.placement.y(screenHeight, layout.height());

		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);
		SuspectsHud.draw(font, layout, x, y, settings.table.opacity);
		GlStateManager.popMatrix();
		return layout;
	}
}
