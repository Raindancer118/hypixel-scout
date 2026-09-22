package de.raindancer118.hypixelscout.config;

/**
 * Which point of the screen the table is measured from.
 *
 * <p>Nine of them, a grid of thirds. The table remembers the corner or edge it was dropped nearest,
 * so a table in the bottom-right stays in the bottom-right when the window grows.
 */
public enum TableAnchor {
	TOP_LEFT(0, 0), TOP_CENTER(0.5, 0), TOP_RIGHT(1, 0),
	MIDDLE_LEFT(0, 0.5), CENTER(0.5, 0.5), MIDDLE_RIGHT(1, 0.5),
	BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5, 1), BOTTOM_RIGHT(1, 1);

	private final double xFactor;
	private final double yFactor;

	TableAnchor(double xFactor, double yFactor) {
		this.xFactor = xFactor;
		this.yFactor = yFactor;
	}

	public double xFactor() {
		return xFactor;
	}

	public double yFactor() {
		return yFactor;
	}

	/** The anchor of whichever third of the screen a point falls into. */
	public static TableAnchor nearest(int screenWidth, int screenHeight, int x, int y) {
		int column = third(x, screenWidth);
		int row = third(y, screenHeight);

		return values()[row * 3 + column];
	}

	private static int third(int value, int size) {
		if (size <= 0) {
			return 0;
		}

		return Math.clamp((long) Math.floor(3.0 * value / size), 0, 2);
	}
}
