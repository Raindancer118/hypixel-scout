package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.HudVisibility;
import de.raindancer118.hypixelscout.core.Roster;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * Hangs the table off the vanilla HUD, and decides when it is up.
 *
 * <p>Drawn every frame, so it does nothing but read the roster and the cache and lay out a handful
 * of rectangles; every lookup happens on the fetch threads.
 */
public final class TableHudElement implements HudElement {
	private final TableHud table;
	private final Supplier<ScoutSettings> settings;
	private final Roster roster;

	private boolean opened;
	private boolean keyHeld;

	public TableHudElement(TableHud table, Supplier<ScoutSettings> settings, Roster roster) {
		this.table = table;
		this.settings = settings;
		this.roster = roster;
	}

	/** The key that opens and closes it. Returns whether it is open now. */
	public boolean toggle() {
		opened = !opened;
		return opened;
	}

	public boolean isOpen() {
		return opened;
	}

	/** Leaving a game closes it, so the next game does not start with the last one's table up. */
	public void close() {
		opened = false;
	}

	/** Read from the key each tick, for the mode that shows it only while held. */
	public void setKeyHeld(boolean held) {
		keyHeld = held;
	}

	public boolean visible() {
		return HudVisibility.visible(settings.get().table.mode, roster.isInGame(), opened, keyHeld,
				roster.millisSinceStart());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		// The editor draws its own copy; two tables at once would only confuse.
		if (client.gui.screen() instanceof TableEditorScreen || !visible()) {
			return;
		}

		// The debug screen and the tab list both live up here; three layers help nobody.
		if (client.getDebugOverlay().showDebugScreen() || client.options.keyPlayerList.isDown()) {
			return;
		}

		draw(graphics, table, settings.get());
	}

	/** Shared with the editor, so what is positioned there is exactly what appears here. */
	static TableHud.Layout draw(GuiGraphicsExtractor graphics, TableHud table, ScoutSettings settings) {
		float scale = (float) settings.table.scale;
		TableHud.Layout layout = table.measure();

		int screenWidth = Math.round(graphics.guiWidth() / scale);
		int screenHeight = Math.round(graphics.guiHeight() / scale);
		int x = settings.table.placement.x(screenWidth, layout.width());
		int y = settings.table.placement.y(screenHeight, layout.height());

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		table.draw(graphics, layout, x, y);
		graphics.pose().popMatrix();

		return layout;
	}
}
