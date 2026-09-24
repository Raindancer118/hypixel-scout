package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.flight.Edge;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.game.Hazards;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The HUD half of {@link Hazards}: a TNT card above the incoming warning while a blast would reach
 * the player, NO SAFE LANDING under the crosshair while a fall goes into the void, the outside of the
 * bed being looked at under that, and small markers on the screen's edge for enemies in plain sight
 * outside the view.
 *
 * <p>Ported from 26.2's {@code ui.hud.HazardElement}: fires on {@link RenderGameOverlayEvent.Post}
 * {@code ALL}, registered after {@link IncomingElement} so its own card paints above it, exactly
 * mirroring 26.2's {@code attachElementAfter(id("incoming"), ...)}.
 */
public final class HazardElement {
	private static final int CARD_HEIGHT = 18;
	/** Above the incoming warning, which sits 34 above the crosshair. */
	private static final int TNT_ABOVE_CROSSHAIR = 34 + CARD_HEIGHT + 4;
	private static final int VOID_BELOW_CROSSHAIR = 14;
	private static final int BED_BELOW_CROSSHAIR = 14 + CARD_HEIGHT + 4;
	private static final int EDGE_MARGIN = 14;
	/** The hotbar, hearts and hunger at the bottom: markers stay above them. */
	private static final int HOTBAR_HEIGHT = 44;

	private final Hazards hazards;
	private final Supplier<ScoutSettings> settings;

	public HazardElement(Hazards hazards, Supplier<ScoutSettings> settings) {
		this.hazards = hazards;
		this.settings = settings;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		if (client.thePlayer == null || client.currentScreen != null) {
			return;
		}

		Vec eye = Flights.vec(client.thePlayer.getPositionEyes(1.0F));
		Vec look = Flights.vec(client.thePlayer.getLook(1.0F));
		ScaledResolution resolution = new ScaledResolution(client);
		int centreX = resolution.getScaledWidth() / 2;
		int centreY = resolution.getScaledHeight() / 2;

		Hazards.Tnt tnt = null;
		for (Hazards.Tnt candidate : hazards.tnt()) {
			if (candidate.knock() != null) {
				tnt = candidate;
				break;
			}
		}
		if (tnt != null) {
			String text = tntText(tnt, eye, look);
			int colour = tnt.knock().strength() >= 0.5 ? 0x8A1010 : 0x8A5A00;
			card(text, centreX, centreY - TNT_ABOVE_CROSSHAIR - CARD_HEIGHT, colour, true);
		}

		if (hazards.fall() != null && hazards.fall().intoTheVoid()) {
			card("§e⚠ §f§l" + StatCollector.translateToLocal("message.hypixelscout.hazard.no_landing"), centreX,
					centreY + VOID_BELOW_CROSSHAIR, 0x8A1010, true);
		}

		if (hazards.bed() != null) {
			card(bedText(hazards.bed()), centreX, centreY + BED_BELOW_CROSSHAIR, ScoutTheme.PANEL_RGB, false);
		}

		for (Hazards.Offscreen enemy : hazards.offscreen()) {
			edgeMarker(resolution, enemy);
		}
	}

	/** {@code ⚠ TNT 1.4 s · knockback ↗ 62%}. */
	public static String tntText(Hazards.Tnt tnt, Vec eye, Vec look) {
		Vec push = tnt.knock().push();
		String way = Math.hypot(push.x(), push.z()) < 0.05 * Math.max(1e-9, push.length())
				? "⇡" : IncomingElement.arrow(IncomingWatch.bearing(eye.add(push), eye, look));
		return "§e⚠ §f§lTNT §f" + String.format(Locale.ROOT, "%.1f s", tnt.seconds()) + " §7· §e"
				+ StatCollector.translateToLocal("message.hypixelscout.hazard.knockback") + " " + way + " §f"
				+ Math.round(tnt.knock().strength() * 100) + "%";
	}

	/** All loaded layers, outside to inside, including covered layers. */
	public static String bedText(Hazards.Bed bed) {
		StringBuilder layers = new StringBuilder();
		for (BedDefense.Layer layer : BedDefense.layers(bed.cells(), bed.report().seen())) {
			if (layers.length() > 0) {
				layers.append(" §8› ");
			}
			layers.append("§f").append(layer.material());
		}
		String detail = layers.length() == 0 ? defenceText(bed.report()) : layers.toString();
		if (bed.report().open() && layers.length() != 0) {
			detail = defenceText(bed.report()) + " §7· " + layers;
		}
		return "§f§l" + StatCollector.translateToLocalFormatted("message.hypixelscout.hazard.bed", bed.colour())
				+ " §7· " + detail;
	}

	/** {@code OPEN}, or {@code Wool 3 · End Stone 12} with the softest first — also on the Teams screen. */
	public static String defenceText(BedDefense.Report report) {
		if (report.open()) {
			return "§c§l" + StatCollector.translateToLocal("message.hypixelscout.hazard.bed_open");
		}
		StringBuilder text = new StringBuilder();
		for (BedDefense.Material material : report.outside()) {
			text.append(text.length() == 0 ? "§e" : " §7· §f").append(material.name()).append(" §7")
					.append(material.exposed());
		}
		return text.toString();
	}

	private static void card(String text, int centreX, int y, int rgb, boolean pulse) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		int width = ScoutTheme.width(font, text) + 16;
		int x = centreX - width / 2;
		int alpha = 0xD0;
		if (pulse) {
			double phase = (System.currentTimeMillis() % 600) / 600.0;
			alpha = (int) (0xA0 + 0x50 * Math.sin(phase * 2 * Math.PI));
		}
		ScoutTheme.rounded(x, y, width, CARD_HEIGHT, (alpha << 24) | rgb);
		ScoutTheme.textCentred(font, text, centreX, y + 5, ScoutTheme.TEXT);
	}

	/** The arrow for its direction in the team's colour, the distance under it. */
	private static void edgeMarker(ScaledResolution resolution, Hazards.Offscreen enemy) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		double[] at = Edge.place(enemy.bearing(), resolution.getScaledWidth(),
				resolution.getScaledHeight() - HOTBAR_HEIGHT, EDGE_MARGIN);
		int x = (int) Math.round(at[0]);
		int y = (int) Math.round(at[1]);
		String distance = Math.round(enemy.distance()) + "m";
		int width = Math.max(14, ScoutTheme.width(font, distance) + 6);

		ScoutTheme.rounded(x - width / 2, y - 11, width, 22, 0xB0000000 | ScoutTheme.PANEL_RGB);
		Gui.drawRect(x - width / 2 + 2, y + 10, x + width / 2 - 2, y + 11, 0xFF000000 | enemy.rgb());
		ScoutTheme.textCentred(font, IncomingElement.arrow(enemy.bearing()), x, y - 9, 0xFF000000 | enemy.rgb());
		ScoutTheme.textCentred(font, distance, x, y + 1, ScoutTheme.TEXT);
	}
}
