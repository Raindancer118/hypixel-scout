package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.CheatSensor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The suspects card on the HUD, while a game is on and somebody is suspect enough to show. Out of
 * the way while its editor is open, the peek key is held, the debug screen or the tab list is up.
 */
public final class SuspectsElement implements HudElement {
	private final CheatSensor cheats;
	private final Supplier<ScoutSettings> settings;
	private final Roster roster;
	private BooleanSupplier hidden = () -> false;

	public SuspectsElement(CheatSensor cheats, Supplier<ScoutSettings> settings, Roster roster) {
		this.cheats = cheats;
		this.settings = settings;
		this.roster = roster;
	}

	public SuspectsElement hideWhile(BooleanSupplier condition) {
		hidden = condition;
		return this;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		ScoutSettings.Cheats config = settings.get().cheats;
		if (!config.enabled || !config.hud.enabled || !roster.isInGame() || hidden.getAsBoolean()
				|| client.gui.screen() instanceof SuspectsEditorScreen
				|| client.getDebugOverlay().showDebugScreen() || client.options.keyPlayerList.isDown()) {
			return;
		}
		List<Suspicion.Suspect> shown = SuspectsHud.shown(cheats.suspects(), config.hud);
		if (!shown.isEmpty()) {
			draw(graphics, shown, settings.get());
		}
	}

	/** Shared with the editor, so what is positioned there is exactly what appears here. */
	static SuspectsHud.Layout draw(GuiGraphicsExtractor graphics, List<Suspicion.Suspect> shown, ScoutSettings settings) {
		ScoutSettings.Hud hud = settings.cheats.hud;
		float scale = (float) hud.scale;
		SuspectsHud.Layout layout = SuspectsHud.measure(shown, hud);
		int screenWidth = Math.round(graphics.guiWidth() / scale);
		int screenHeight = Math.round(graphics.guiHeight() / scale);
		int x = hud.placement.x(screenWidth, layout.width());
		int y = hud.placement.y(screenHeight, layout.height());

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		SuspectsHud.draw(graphics, layout, x, y, settings.table.opacity);
		graphics.pose().popMatrix();
		return layout;
	}
}
