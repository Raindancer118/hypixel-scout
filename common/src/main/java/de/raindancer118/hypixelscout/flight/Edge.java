package de.raindancer118.hypixelscout.flight;

/**
 * Where on the screen's frame a marker for something off-screen goes: in the direction it is, as if
 * the screen were a compass seen from above — ahead at the top, behind at the bottom, left and right
 * at the sides.
 */
public final class Edge {
	private Edge() {
	}

	/**
	 * @param bearing degrees: 0 ahead, negative left, positive right, ±180 behind
	 * @return {x, y} on the frame {@code margin} inside the screen's border
	 */
	public static double[] place(double bearing, int width, int height, int margin) {
		double radians = Math.toRadians(bearing);
		double dx = Math.sin(radians);
		double dy = -Math.cos(radians);
		double halfWidth = width / 2.0 - margin;
		double halfHeight = height / 2.0 - margin;

		double scale = Math.min(Math.abs(dx) < 1e-9 ? Double.MAX_VALUE : halfWidth / Math.abs(dx),
				Math.abs(dy) < 1e-9 ? Double.MAX_VALUE : halfHeight / Math.abs(dy));
		return new double[] {round(width / 2.0 + dx * scale), round(height / 2.0 + dy * scale)};
	}

	/** Drops floating-point dust, so exact places stay exact. */
	private static double round(double value) {
		return Math.round(value * 1e6) / 1e6;
	}
}
