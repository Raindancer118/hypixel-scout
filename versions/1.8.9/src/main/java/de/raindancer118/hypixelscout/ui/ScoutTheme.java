package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.Accent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.function.Supplier;

/**
 * The one place that knows what this mod looks like.
 *
 * <p>Cards with softened corners, a hairline border and a shadow under them; a header a shade
 * lighter with a rule in the accent colour; rows that stripe and highlight the same way everywhere.
 * The HUD table, the look tooltip, the tab list and every screen draw through here, which is what
 * keeps them looking like one program.
 *
 * <p>Ported from the 26.2 Fabric module's {@code ui.ScoutTheme}, which draws through a
 * {@code GuiGraphics}-style object. 1.8.9 has no such object — every draw call here is one of
 * {@link Gui#drawRect}'s static overloads plus, for text, an explicit {@link FontRenderer}
 * parameter, since there is no implicit "current graphics context" to pull one from. The corner-cut
 * and border technique is unchanged from the working {@code 1.8.9-support} branch's own
 * {@code ScoutTheme} (see {@code git show 1.8.9-support:.../mc/ui/ScoutTheme.java}); the actual
 * palette and layout (panel colour, header height, accent rule) are re-derived from the current
 * 26.2 design, which has moved on since that branch.
 */
public final class ScoutTheme {
	public static final int PANEL_RGB = 0x141419;
	public static final int PANEL_LIGHT = 0xFF1E1E27;
	public static final int BORDER = 0x2EFFFFFF;
	public static final int SHADOW = 0x55000000;
	public static final int DIVIDER = 0x1AFFFFFF;
	public static final int HOVER = 0x24FFFFFF;
	public static final int STRIPE = 0x0BFFFFFF;

	public static final int GOOD = 0xFF6ADF8A;
	public static final int BAD = 0xFFFF5555;
	public static final int WARN = 0xFFFFCC55;

	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFA4A4B2;
	public static final int TEXT_FAINT = 0xFF6A6A78;

	public static final int HEADER_HEIGHT = 20;

	private static Supplier<Accent> accent = new Supplier<Accent>() {
		@Override
		public Accent get() {
			return Accent.GOLD;
		}
	};

	private ScoutTheme() {
	}

	/** Installed by the mod once the settings are loaded; a later phase wires the real source. */
	public static void useAccent(Supplier<Accent> source) {
		accent = source;
	}

	public static int accent() {
		return accent.get().argb();
	}

	public static int accent(int alpha) {
		return accent.get().withAlpha(alpha);
	}

	/** Section-sign code of the accent, for text built from formatted strings. */
	public static String accentCode() {
		return "§" + accent.get().legacyCode();
	}

	/** The client's own font renderer, for callers that have not been handed one. */
	public static FontRenderer font() {
		return Minecraft.getMinecraft().fontRendererObj;
	}

	/** A card: shadow, softened body, hairline border. {@code opacity} is 0..100. */
	public static void panel(int x, int y, int width, int height, int opacity) {
		int clamped = Math.max(0, Math.min(100, opacity));
		int alpha = Math.round(clamped * 2.55f);
		if (alpha == 0) {
			return;
		}

		rounded(x + 2, y + 3, width, height, Math.min(alpha, 0x55) << 24);
		rounded(x, y, width, height, (alpha << 24) | PANEL_RGB);
		border(x, y, width, height, (Math.min(0x2E, alpha / 3) << 24) | 0xFFFFFF);
	}

	public static void panel(int x, int y, int width, int height) {
		panel(x, y, width, height, 94);
	}

	/** A rectangle whose corners are cut in by a pixel twice, which reads as round at GUI scale. */
	public static void rounded(int x, int y, int width, int height, int colour) {
		int right = x + width;
		int bottom = y + height;

		Gui.drawRect(x, y + 2, right, bottom - 2, colour);
		Gui.drawRect(x + 1, y + 1, right - 1, y + 2, colour);
		Gui.drawRect(x + 1, bottom - 2, right - 1, bottom - 1, colour);
		Gui.drawRect(x + 2, y, right - 2, y + 1, colour);
		Gui.drawRect(x + 2, bottom - 1, right - 2, bottom, colour);
	}

	private static void border(int x, int y, int width, int height, int colour) {
		int right = x + width;
		int bottom = y + height;

		Gui.drawRect(x + 2, y, right - 2, y + 1, colour);
		Gui.drawRect(x + 2, bottom - 1, right - 2, bottom, colour);
		Gui.drawRect(x, y + 2, x + 1, bottom - 2, colour);
		Gui.drawRect(right - 1, y + 2, right, bottom - 2, colour);
		Gui.drawRect(x + 1, y + 1, x + 2, y + 2, colour);
		Gui.drawRect(right - 2, y + 1, right - 1, y + 2, colour);
		Gui.drawRect(x + 1, bottom - 2, x + 2, bottom - 1, colour);
		Gui.drawRect(right - 2, bottom - 2, right - 1, bottom - 1, colour);
	}

	/** The strip across the top of a card, a shade lighter, with the accent rule under it. */
	public static void header(int x, int y, int width, int opacity) {
		int clamped = Math.max(0, Math.min(100, opacity));
		int alpha = Math.round(clamped * 2.55f);
		int light = (alpha << 24) | (PANEL_LIGHT & 0xFFFFFF);

		Gui.drawRect(x + 2, y + 1, x + width - 2, y + 2, light);
		Gui.drawRect(x + 1, y + 2, x + width - 1, y + HEADER_HEIGHT, light);
		Gui.drawRect(x + 1, y + HEADER_HEIGHT, x + width - 1, y + HEADER_HEIGHT + 1, accent(0x90));
	}

	public static void divider(int x, int y, int width) {
		Gui.drawRect(x, y, x + width, y + 1, DIVIDER);
	}

	/** A small rounded block of colour, for a team marker or a badge. */
	public static void pill(int x, int y, int width, int height, int colour) {
		Gui.drawRect(x, y + 1, x + width, y + height - 1, colour);
		Gui.drawRect(x + 1, y, x + width - 1, y + 1, colour);
		Gui.drawRect(x + 1, y + height - 1, x + width - 1, y + height, colour);
	}

	/** A pill with a word in it, sized to the word. Returns its width. */
	public static int badge(FontRenderer font, String text, int x, int y, int background, int colour) {
		int width = font.getStringWidth(text) + 6;
		pill(x, y, width, 11, background);
		text(font, text, x + 3, y + 2, colour);
		return width;
	}

	public static void text(FontRenderer font, String value, int x, int y, int colour) {
		font.drawStringWithShadow(value, x, y, colour);
	}

	public static void textRight(FontRenderer font, String value, int rightEdge, int y, int colour) {
		text(font, value, rightEdge - font.getStringWidth(value), y, colour);
	}

	public static void textCentred(FontRenderer font, String value, int centre, int y, int colour) {
		text(font, value, centre - font.getStringWidth(value) / 2, y, colour);
	}

	public static int width(FontRenderer font, String value) {
		return font.getStringWidth(value);
	}

	/** Cuts a formatted string to a width, ending it with an ellipsis if anything was cut. */
	public static String fit(FontRenderer font, String value, int width) {
		if (font.getStringWidth(value) <= width) {
			return value;
		}

		String cut = font.trimStringToWidth(value, Math.max(0, width - font.getStringWidth("…")));
		return cut + "…";
	}
}
