package de.raindancer118.hypixelscout.mc.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

import java.util.UUID;

/**
 * The one place that knows what this mod looks like.
 *
 * <p>Minecraft 1.8.9 has no rounded rectangles and no shadows, so both are built out of flat rects:
 * a panel is drawn as a stack of rows that step in by a pixel at the top and bottom, which reads as
 * a soft corner at GUI scale, and a darker rect behind it stands in for a drop shadow. Doing it
 * here rather than in each screen is what keeps the screens looking like one program.
 */
public final class ScoutTheme extends Gui {
	/** Nobody needs an instance; the drawing methods are static and the class extends Gui. */
	private static final ScoutTheme DRAW = new ScoutTheme();

	public static final int PANEL = 0xF0141419;
	public static final int PANEL_LIGHT = 0xFF1C1C24;
	public static final int BORDER = 0x30FFFFFF;
	public static final int SHADOW = 0x50000000;
	public static final int DIVIDER = 0x1AFFFFFF;
	public static final int HOVER = 0x22FFFFFF;
	public static final int STRIPE = 0x0CFFFFFF;

	public static final int ACCENT = 0xFFFFAA00;
	public static final int GOOD = 0xFF6ADF8A;
	public static final int BAD = 0xFFFF5555;

	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFF9A9AA8;
	public static final int TEXT_FAINT = 0xFF63636F;

	public static final int ROW_HEIGHT = 16;
	public static final int HEADER_HEIGHT = 22;

	private ScoutTheme() {
	}

	/** A panel with softened corners, a hairline border and a shadow under it. */
	public static void panel(int x, int y, int width, int height) {
		shadow(x, y, width, height);
		rounded(x, y, width, height, PANEL);
		border(x, y, width, height);
	}

	public static void rounded(int x, int y, int width, int height, int colour) {
		int right = x + width;
		int bottom = y + height;

		DRAW.drawRect(x, y + 2, right, bottom - 2, colour);
		DRAW.drawRect(x + 1, y + 1, right - 1, y + 2, colour);
		DRAW.drawRect(x + 1, bottom - 2, right - 1, bottom - 1, colour);
		DRAW.drawRect(x + 2, y, right - 2, y + 1, colour);
		DRAW.drawRect(x + 2, bottom - 1, right - 2, bottom, colour);
	}

	private static void shadow(int x, int y, int width, int height) {
		// One offset copy rather than a gradient: 1.8.9 has no blur, and a soft edge faked with
		// several rects costs more than it is worth at this size.
		DRAW.drawRect(x + 2, y + 3, x + width + 2, y + height + 3, SHADOW);
	}

	private static void border(int x, int y, int width, int height) {
		int right = x + width;
		int bottom = y + height;

		DRAW.drawRect(x + 2, y, right - 2, y + 1, BORDER);
		DRAW.drawRect(x + 2, bottom - 1, right - 2, bottom, BORDER);
		DRAW.drawRect(x, y + 2, x + 1, bottom - 2, BORDER);
		DRAW.drawRect(right - 1, y + 2, right, bottom - 2, BORDER);
	}

	/** The bar across the top of a panel, a shade lighter, with a rule under it. */
	public static void header(int x, int y, int width) {
		DRAW.drawRect(x + 1, y + 1, x + width - 1, y + HEADER_HEIGHT, PANEL_LIGHT);
		DRAW.drawRect(x + 2, y, x + width - 2, y + 1, PANEL_LIGHT);
		DRAW.drawRect(x + 1, y + HEADER_HEIGHT, x + width - 1, y + HEADER_HEIGHT + 1, ACCENT
				& 0x60FFFFFF);
	}

	public static void divider(int x, int y, int width) {
		DRAW.drawRect(x, y, x + width, y + 1, DIVIDER);
	}

	public static void fill(int x, int y, int width, int height, int colour) {
		DRAW.drawRect(x, y, x + width, y + height, colour);
	}

	/** A small rounded block of colour, for a team marker or a badge. */
	public static void pill(int x, int y, int width, int height, int colour) {
		DRAW.drawRect(x, y + 1, x + width, y + height - 1, colour);
		DRAW.drawRect(x + 1, y, x + width - 1, y + 1, colour);
		DRAW.drawRect(x + 1, y + height - 1, x + width - 1, y + height, colour);
	}

	public static void text(String value, int x, int y, int colour) {
		Minecraft.getMinecraft().fontRendererObj.drawString(value, x, y, colour, true);
	}

	public static void textRight(String value, int rightEdge, int y, int colour) {
		Minecraft mc = Minecraft.getMinecraft();
		mc.fontRendererObj.drawString(value, rightEdge - width(value), y, colour, true);
	}

	public static int width(String value) {
		return Minecraft.getMinecraft().fontRendererObj.getStringWidth(value);
	}

	/**
	 * A player's face from their skin — the 8×8 patch at (8,8) of the texture with the hat layer
	 * over it. Draws nothing for a player whose skin the client has never seen.
	 */
	public static void head(UUID uuid, int x, int y, int size) {
		Minecraft mc = Minecraft.getMinecraft();
		if (uuid == null || mc.thePlayer == null || mc.thePlayer.sendQueue == null) {
			return;
		}

		NetworkPlayerInfo info = mc.thePlayer.sendQueue.getPlayerInfo(uuid);
		if (info == null) {
			return;
		}

		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		mc.getTextureManager().bindTexture(info.getLocationSkin());
		Gui.drawScaledCustomSizeModalRect(x, y, 8.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f);
		Gui.drawScaledCustomSizeModalRect(x, y, 40.0f, 8.0f, 8, 8, size, size, 64.0f, 64.0f);
	}
}
