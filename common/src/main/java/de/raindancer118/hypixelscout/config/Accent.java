package de.raindancer118.hypixelscout.config;

/**
 * The one colour the mod's surfaces are tinted with: the rule under a header, the selected row, the
 * mod's name in chat. A short list rather than a free hex field — every entry is readable on the
 * dark panels, which a colour picker cannot promise.
 */
public enum Accent {
	GOLD(0xFFFFAA00, '6'),
	AQUA(0xFF55FFFF, 'b'),
	GREEN(0xFF55FF55, 'a'),
	RED(0xFFFF5555, 'c'),
	PINK(0xFFFF55FF, 'd'),
	BLUE(0xFF5599FF, '9'),
	WHITE(0xFFFFFFFF, 'f');

	private final int argb;
	private final char legacyCode;

	Accent(int argb, char legacyCode) {
		this.argb = argb;
		this.legacyCode = legacyCode;
	}

	public int argb() {
		return argb;
	}

	/** The same colour with its alpha replaced, for rules and highlights that must not shout. */
	public int withAlpha(int alpha) {
		return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
	}

	/** The chat formatting code closest to it, for text built from section-sign strings. */
	public char legacyCode() {
		return legacyCode;
	}
}
