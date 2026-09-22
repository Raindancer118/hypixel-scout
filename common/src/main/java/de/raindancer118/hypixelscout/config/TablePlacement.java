package de.raindancer118.hypixelscout.config;

/**
 * Where the table sits, stored so it survives the window changing size.
 *
 * <p>The offsets are fractions of the screen measured from a {@link TableAnchor}, not pixels from
 * the top-left corner: a pixel offset puts a bottom-right table in the middle of the screen the
 * moment the player goes fullscreen, and a table that ends up off-screen cannot be dragged back.
 */
public record TablePlacement(TableAnchor anchor, double offsetX, double offsetY) {
	/** A few pixels in from the top-left corner on an ordinary window. */
	public static final TablePlacement DEFAULT = new TablePlacement(TableAnchor.TOP_LEFT, 0.006, 0.01);

	public TablePlacement {
		anchor = anchor == null ? TableAnchor.TOP_LEFT : anchor;
		offsetX = Double.isFinite(offsetX) ? offsetX : 0;
		offsetY = Double.isFinite(offsetY) ? offsetY : 0;
	}

	public int x(int screenWidth, int tableWidth) {
		return resolve(screenWidth, tableWidth, anchor.xFactor(), offsetX);
	}

	public int y(int screenHeight, int tableHeight) {
		return resolve(screenHeight, tableHeight, anchor.yFactor(), offsetY);
	}

	private static int resolve(int screenSize, int size, double anchorFactor, double offset) {
		if (screenSize <= 0) {
			return 0;
		}

		double position = anchorFactor * screenSize + offset * screenSize - anchorFactor * size;
		int limit = screenSize - size;

		// Wider than the screen would give a negative limit; pin it rather than flip the clamp.
		return limit <= 0 ? 0 : Math.clamp(Math.round(position), 0, limit);
	}

	/**
	 * The placement of a table dropped with its top-left corner at {@code x, y}, adopting the anchor
	 * of the third of the screen its centre landed in. Resolves back to exactly that pixel.
	 */
	public static TablePlacement fromTopLeft(int screenWidth, int screenHeight, int tableWidth,
			int tableHeight, int x, int y) {
		TableAnchor anchor = TableAnchor.nearest(screenWidth, screenHeight,
				x + tableWidth / 2, y + tableHeight / 2);

		return new TablePlacement(anchor,
				offsetFor(screenWidth, tableWidth, anchor.xFactor(), x),
				offsetFor(screenHeight, tableHeight, anchor.yFactor(), y));
	}

	private static double offsetFor(int screenSize, int size, double anchorFactor, int position) {
		if (screenSize <= 0) {
			return 0;
		}

		return (position + anchorFactor * size - anchorFactor * screenSize) / screenSize;
	}

	/** Pulls a value onto the nearest guide within {@code threshold} pixels, or leaves it alone. */
	public static int snap(int value, int[] targets, int threshold) {
		int best = value;
		int bestDistance = threshold + 1;

		for (int target : targets) {
			int distance = Math.abs(target - value);

			if (distance <= threshold && distance < bestDistance) {
				bestDistance = distance;
				best = target;
			}
		}

		return best;
	}
}
