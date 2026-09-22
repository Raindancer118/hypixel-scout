package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.Accent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/**
 * The one place that knows what this mod looks like.
 *
 * <p>Cards with softened corners, a hairline border and a shadow under them; a header a shade
 * lighter with a rule in the accent colour; rows that stripe and highlight the same way everywhere.
 * The HUD table, the look tooltip, the tab list and every screen draw through here, which is what
 * keeps them looking like one program.
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

	private static Supplier<Accent> accent = () -> Accent.GOLD;

	private ScoutTheme() {
	}

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

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** A card: shadow, softened body, hairline border. {@code opacity} is 0..100. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int opacity) {
		int alpha = Math.round(Math.clamp(opacity, 0, 100) * 2.55f);
		if (alpha == 0) {
			return;
		}

		rounded(g, x + 2, y + 3, width, height, (Math.min(alpha, 0x55) << 24));
		rounded(g, x, y, width, height, (alpha << 24) | PANEL_RGB);
		border(g, x, y, width, height, Math.min(0x2E, alpha / 3) << 24 | 0xFFFFFF);
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height) {
		panel(g, x, y, width, height, 94);
	}

	/** A rectangle whose corners are cut in by a pixel twice, which reads as round at GUI scale. */
	public static void rounded(GuiGraphicsExtractor g, int x, int y, int width, int height, int colour) {
		int right = x + width;
		int bottom = y + height;

		g.fill(x, y + 2, right, bottom - 2, colour);
		g.fill(x + 1, y + 1, right - 1, y + 2, colour);
		g.fill(x + 1, bottom - 2, right - 1, bottom - 1, colour);
		g.fill(x + 2, y, right - 2, y + 1, colour);
		g.fill(x + 2, bottom - 1, right - 2, bottom, colour);
	}

	private static void border(GuiGraphicsExtractor g, int x, int y, int width, int height, int colour) {
		int right = x + width;
		int bottom = y + height;

		g.fill(x + 2, y, right - 2, y + 1, colour);
		g.fill(x + 2, bottom - 1, right - 2, bottom, colour);
		g.fill(x, y + 2, x + 1, bottom - 2, colour);
		g.fill(right - 1, y + 2, right, bottom - 2, colour);
		g.fill(x + 1, y + 1, x + 2, y + 2, colour);
		g.fill(right - 2, y + 1, right - 1, y + 2, colour);
		g.fill(x + 1, bottom - 2, x + 2, bottom - 1, colour);
		g.fill(right - 2, bottom - 2, right - 1, bottom - 1, colour);
	}

	/** The strip across the top of a card, a shade lighter, with the accent rule under it. */
	public static void header(GuiGraphicsExtractor g, int x, int y, int width, int opacity) {
		int alpha = Math.round(Math.clamp(opacity, 0, 100) * 2.55f);
		int light = (alpha << 24) | (PANEL_LIGHT & 0xFFFFFF);

		g.fill(x + 2, y + 1, x + width - 2, y + 2, light);
		g.fill(x + 1, y + 2, x + width - 1, y + HEADER_HEIGHT, light);
		g.fill(x + 1, y + HEADER_HEIGHT, x + width - 1, y + HEADER_HEIGHT + 1, accent(0x90));
	}

	public static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
		g.fill(x, y, x + width, y + 1, DIVIDER);
	}

	/** A small rounded block of colour, for a team marker or a badge. */
	public static void pill(GuiGraphicsExtractor g, int x, int y, int width, int height, int colour) {
		g.fill(x, y + 1, x + width, y + height - 1, colour);
		g.fill(x + 1, y, x + width - 1, y + 1, colour);
		g.fill(x + 1, y + height - 1, x + width - 1, y + height, colour);
	}

	/** A pill with a word in it, sized to the word. Returns its width. */
	public static int badge(GuiGraphicsExtractor g, String text, int x, int y, int background, int colour) {
		int width = font().width(text) + 6;
		pill(g, x, y, width, 11, background);
		g.text(font(), text, x + 3, y + 2, colour, false);
		return width;
	}

	public static void text(GuiGraphicsExtractor g, String value, int x, int y, int colour) {
		g.text(font(), value, x, y, colour, true);
	}

	public static void textRight(GuiGraphicsExtractor g, String value, int rightEdge, int y, int colour) {
		g.text(font(), value, rightEdge - font().width(value), y, colour, true);
	}

	public static void textCentred(GuiGraphicsExtractor g, String value, int centre, int y, int colour) {
		g.text(font(), value, centre - font().width(value) / 2, y, colour, true);
	}

	public static int width(String value) {
		return font().width(value);
	}

	/** Cuts a formatted string to a width, ending it with an ellipsis if anything was cut. */
	public static String fit(String value, int width) {
		if (width(value) <= width) {
			return value;
		}

		String cut = font().plainSubstrByWidth(value, Math.max(0, width - width("…")));
		return cut + "…";
	}
}
