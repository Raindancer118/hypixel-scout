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

	/**
	 * How centred a standing player is, measured to the point of their body the view ray passes
	 * closest to — not to one fixed point on it. Aiming at the head of somebody close would
	 * otherwise count as aiming half a metre above them.
	 *
	 * @param targetBottom the feet, in world height
	 * @param targetTop    the top of the head
	 */
	public static double bodyAlignment(double eyeX, double eyeY, double eyeZ,
			double lookX, double lookY, double lookZ,
			double targetX, double targetBottom, double targetTop, double targetZ) {
		double aimY = aimHeight(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, targetX, targetBottom,
				targetTop, targetZ);
		return alignment(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, targetX, aimY, targetZ);
	}

	/** The height on the target's body that {@link #bodyAlignment} measures to. */
	public static double aimHeight(double eyeX, double eyeY, double eyeZ,
			double lookX, double lookY, double lookZ,
			double targetX, double targetBottom, double targetTop, double targetZ) {
		double horizontalLook = Math.sqrt(lookX * lookX + lookZ * lookZ);
		double dx = targetX - eyeX;
		double dz = targetZ - eyeZ;
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		// Where the ray is, height-wise, once it has come as far as the target horizontally; looking
		// straight up or down it never gets there, and the middle of the body stands in.
		double rayHeight = horizontalLook < 1.0e-9
				? (targetBottom + targetTop) / 2
				: eyeY + lookY * horizontalDistance / horizontalLook;
		return Math.max(targetBottom, Math.min(targetTop, rayHeight));
	}

	/** The comparison happens in cosines, so the configured angle is converted once. */
	public static double cosineOf(double degrees) {
		return Math.cos(Math.toRadians(degrees));
	}
}
