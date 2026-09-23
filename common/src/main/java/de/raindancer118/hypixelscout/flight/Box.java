package de.raindancer118.hypixelscout.flight;

/** An axis-aligned box: a hitbox, grown by whatever a projectile needs to touch it. */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
	public Box inflate(double amount) {
		return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
	}

	public boolean contains(Vec point) {
		return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY
				&& point.z() >= minZ && point.z() <= maxZ;
	}

	/** How far a point is from the box; zero inside it. */
	public double distanceTo(Vec point) {
		double dx = Math.max(Math.max(minX - point.x(), 0), point.x() - maxX);
		double dy = Math.max(Math.max(minY - point.y(), 0), point.y() - maxY);
		double dz = Math.max(Math.max(minZ - point.z(), 0), point.z() - maxZ);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * Where on the segment from {@code from} to {@code to} it first enters the box, as a fraction of
	 * its length; {@code 0} when it starts inside, negative when it never touches it.
	 */
	public double entry(Vec from, Vec to) {
		if (contains(from)) {
			return 0.0;
		}

		double[] start = {from.x(), from.y(), from.z()};
		double[] delta = {to.x() - from.x(), to.y() - from.y(), to.z() - from.z()};
		double[] min = {minX, minY, minZ};
		double[] max = {maxX, maxY, maxZ};

		// The slab method: the segment is inside once it is between both faces on every axis.
		double enter = 0.0;
		double leave = 1.0;
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(delta[axis]) < 1e-12) {
				if (start[axis] < min[axis] || start[axis] > max[axis]) {
					return -1.0;
				}
				continue;
			}

			double a = (min[axis] - start[axis]) / delta[axis];
			double b = (max[axis] - start[axis]) / delta[axis];
			enter = Math.max(enter, Math.min(a, b));
			leave = Math.min(leave, Math.max(a, b));
			if (enter > leave) {
				return -1.0;
			}
		}
		return enter;
	}
}
