package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.LockWatch;
import de.raindancer118.hypixelscout.flight.ProjectileKind;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The warning above the crosshair while an arrow or fireball is about to hit the player: what, from
 * which side, and in how long.
 *
 * <p>Pulses red, and points the way the thing comes from — straight ahead is up, behind is down —
 * so the player knows where to look before they turn.
 *
 * <p>Before anything is thrown, the same place shows the lock: amber instead of red, who is aiming
 * and from which side. Where they aim is marked in the world by {@code mixin}'s own gizmo drawing.
 *
 * <p>Ported from 26.2's {@code ui.hud.IncomingElement}: fires on {@link RenderGameOverlayEvent.Post}
 * {@code ALL} rather than being chained after the crosshair in a Fabric HUD registry.
 */
public final class IncomingElement {
	/** Eight arrows around the compass, starting straight ahead and going clockwise. */
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final int OFFSET_ABOVE_CROSSHAIR = 34;

	private final Flights flights;
	private final Supplier<ScoutSettings> settings;

	public IncomingElement(Flights flights, Supplier<ScoutSettings> settings) {
		this.flights = flights;
		this.settings = settings;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		IncomingWatch.Warning warning = flights.warning();
		LockWatch.Lock lock = flights.lock();
		if ((warning == null && lock == null) || !settings.get().projectiles.alarm || client.thePlayer == null
				|| client.currentScreen != null) {
			return;
		}

		Vec eye = Flights.vec(client.thePlayer.getPositionEyes(1.0F));
		Vec look = Flights.vec(client.thePlayer.getLook(1.0F));
		String text = warning != null ? text(warning, warning.bearing(eye, look)) : text(lock, lock.bearing(eye, look));
		int colour = warning != null ? 0x8A1010 : 0x8A5A00;
		int rule = warning != null ? ScoutTheme.BAD : 0xFFFFB020;

		ScaledResolution resolution = new ScaledResolution(client);
		int width = ScoutTheme.width(client.fontRendererObj, text) + 16;
		int height = 18;
		int x = (resolution.getScaledWidth() - width) / 2;
		int y = resolution.getScaledHeight() / 2 - OFFSET_ABOVE_CROSSHAIR - height;

		// A slow pulse: bright enough to catch the eye, not a strobe.
		double phase = (System.currentTimeMillis() % 600) / 600.0;
		int alpha = (int) (0xA0 + 0x50 * Math.sin(phase * 2 * Math.PI));
		ScoutTheme.rounded(x, y, width, height, (alpha << 24) | colour);
		net.minecraft.client.gui.Gui.drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, rule);
		ScoutTheme.textCentred(client.fontRendererObj, text, x + width / 2, y + 5, ScoutTheme.TEXT);
	}

	/** {@code ⚠ FIREBALL ↙ 0.8 s}, in the player's language. */
	public static String text(IncomingWatch.Warning warning, double bearing) {
		String what = StatCollector.translateToLocal(warning.kind() == ProjectileKind.FIREBALL
				? "message.hypixelscout.incoming.fireball" : "message.hypixelscout.incoming.arrow");
		return "§e⚠ §f§l" + what + " §e" + arrow(bearing) + " §f" + String.format(Locale.ROOT, "%.1f s", warning.seconds());
	}

	/** {@code ⌖ TARGET LOCK ↗ Sundial}, in the player's language. */
	public static String text(LockWatch.Lock lock, double bearing) {
		return "§6⌖ §f§l" + StatCollector.translateToLocal("message.hypixelscout.incoming.lock") + " §e" + arrow(bearing)
				+ " §f" + lock.name();
	}

	/** The arrow for a bearing: 0 ahead, negative to the left, positive to the right. */
	public static String arrow(double bearing) {
		int sector = (int) Math.floorMod(Math.round(bearing / 45.0), 8);
		return ARROWS[sector];
	}
}
