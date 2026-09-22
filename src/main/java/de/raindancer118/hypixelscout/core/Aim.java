package de.raindancer118.hypixelscout.core;

/**
 * How close to the middle of the screen something is.
 *
 * <p>The cosine of the angle between where the player is looking and where the target stands, which
 * is a dot product of two unit vectors and nothing more. Distance deliberately plays no part: a
 * player across the map and a player in front of you are picked by how centred they are, not by
 * which is nearer.
 */
public final class Aim {
	private Aim() {
	}

	public static double alignment(double eyeX, double eyeY, double eyeZ,
			double lookX, double lookY, double lookZ,
			double targetX, double targetY, double targetZ) {
		double dx = targetX - eyeX;
		double dy = targetY - eyeY;
		double dz = targetZ - eyeZ;

		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (distance < 1.0e-9) {
			// Standing inside the target: there is no direction to compare, and counting that as a
			// perfect hit would make the tooltip stick to whoever is closest.
			return -1.0;
		}

		double lookLength = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
		if (lookLength < 1.0e-9) {
			return -1.0;
		}

		return (dx * lookX + dy * lookY + dz * lookZ) / (distance * lookLength);
	}

	/** The comparison happens in cosines, so the configured angle is converted once. */
	public static double cosineOf(double degrees) {
		return Math.cos(Math.toRadians(degrees));
	}
}
