package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.HudVisibility;
import de.raindancer118.hypixelscout.core.Roster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Hangs the table off the vanilla HUD, and decides when it is up.
 *
 * <p>Drawn every frame, so it does nothing but read the roster and the cache and lay out a handful
 * of rectangles; every lookup happens on the fetch threads.
 *
 * <p>Ported from 26.2's {@code ui.hud.TableHudElement}: {@code HudElement.extractRenderState} becomes
 * an {@code @SubscribeEvent} on {@link RenderGameOverlayEvent.Post} with {@code ElementType.ALL} —
 * 1.8.9 has no per-element HUD registry, so this draws once, after everything else vanilla drew,
 * exactly as the old {@code 1.8.9-support} branch's {@code StatsOverlay} did.
 */
public final class TableHudElement {
	private final TableHud table;
	private final Supplier<ScoutSettings> settings;
	private final Roster roster;

	private BooleanSupplier peekHeld = new BooleanSupplier() {
		@Override
		public boolean getAsBoolean() {
			return false;
		}
	};
	private boolean opened;
	private boolean keyHeld;

	public TableHudElement(TableHud table, Supplier<ScoutSettings> settings, Roster roster) {
		this.table = table;
		this.settings = settings;
		this.roster = roster;
	}

	/** So the table steps aside while the peek overlay is up. */
	public TableHudElement hideWhile(BooleanSupplier condition) {
		peekHeld = condition;
		return this;
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

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();

		// Not while the peek key is held: the peek draws its own copy, centred.
		if (!visible() || peekHeld.getAsBoolean()) {
			return;
		}

		// The debug screen and the tab list both live up here; three layers help nobody.
		if (client.gameSettings.showDebugInfo || client.gameSettings.keyBindPlayerList.isKeyDown()) {
			return;
		}

		draw(new ScaledResolution(client), table, settings.get());
	}

	/** Shared with the editor, so what is positioned there is exactly what appears here. */
	static TableHud.Layout draw(ScaledResolution resolution, TableHud table, ScoutSettings settings) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		float scale = (float) settings.table.scale;
		TableHud.Layout layout = table.measure(font);

		int screenWidth = Math.round(resolution.getScaledWidth() / scale);
		int screenHeight = Math.round(resolution.getScaledHeight() / scale);
		int x = settings.table.placement.x(screenWidth, layout.width());
		int y = settings.table.placement.y(screenHeight, layout.height());

		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0F);
		table.draw(font, layout, x, y);
		GlStateManager.popMatrix();

		return layout;
	}
}
