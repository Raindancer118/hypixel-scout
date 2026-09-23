package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.ProjectileKind;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The warning above the crosshair while an arrow or fireball is about to hit the player: what, from
 * which side, and in how long.
 *
 * <p>Pulses red, and points the way the thing comes from — straight ahead is up, behind is down —
 * so the player knows where to look before they turn.
 */
public final class IncomingElement implements HudElement {
	/** Eight arrows around the compass, starting straight ahead and going clockwise. */
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final int OFFSET_ABOVE_CROSSHAIR = 34;

	private final Flights flights;
	private final Supplier<ScoutSettings> settings;

	public IncomingElement(Flights flights, Supplier<ScoutSettings> settings) {
		this.flights = flights;
		this.settings = settings;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		IncomingWatch.Warning warning = flights.warning();
		if (warning == null || !settings.get().projectiles.alarm || client.player == null
				|| client.gui.screen() != null) {
			return;
		}

		double bearing = warning.bearing(Flights.vec(client.player.getEyePosition()),
				Flights.vec(client.player.getViewVector(1.0f)));
		String text = text(warning, bearing);

		int width = ScoutTheme.width(text) + 16;
		int height = 18;
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() / 2 - OFFSET_ABOVE_CROSSHAIR - height;

		// A slow pulse: bright enough to catch the eye, not a strobe.
		double phase = (System.currentTimeMillis() % 600) / 600.0;
		int alpha = (int) (0xA0 + 0x50 * Math.sin(phase * 2 * Math.PI));
		ScoutTheme.rounded(graphics, x, y, width, height, (alpha << 24) | 0x8A1010);
		graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, ScoutTheme.BAD);
		ScoutTheme.textCentred(graphics, text, x + width / 2, y + 5, ScoutTheme.TEXT);
	}

	/** {@code ⚠ FIREBALL ↙ 0.8 s}, in the player's language. */
	public static String text(IncomingWatch.Warning warning, double bearing) {
		String what = I18n.get(warning.kind() == ProjectileKind.FIREBALL
				? "message.hypixelscout.incoming.fireball" : "message.hypixelscout.incoming.arrow");
		return "§e⚠ §f§l" + what + " §e" + arrow(bearing) + " §f" + String.format(Locale.ROOT, "%.1f s", warning.seconds());
	}

	/** The arrow for a bearing: 0 ahead, negative to the left, positive to the right. */
	public static String arrow(double bearing) {
		int sector = (int) Math.floorMod(Math.round(bearing / 45.0), 8);
		return ARROWS[sector];
	}
}
