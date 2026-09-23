package de.raindancer118.hypixelscout.flight;

/** A point or a direction in the world, without anything of Minecraft's so it can be tested plainly. */
public record Vec(double x, double y, double z) {
	public static final Vec ZERO = new Vec(0, 0, 0);

	public Vec add(Vec other) {
		return new Vec(x + other.x, y + other.y, z + other.z);
	}

	public Vec subtract(Vec other) {
		return new Vec(x - other.x, y - other.y, z - other.z);
	}

	public Vec scale(double factor) {
		return new Vec(x * factor, y * factor, z * factor);
	}

	public double dot(Vec other) {
		return x * other.x + y * other.y + z * other.z;
	}

	public double length() {
		return Math.sqrt(dot(this));
	}

	public double distanceTo(Vec other) {
		return subtract(other).length();
	}

	/** The same direction at length one; the zero vector stays zero. */
	public Vec normalize() {
		double length = length();
		return length < 1e-9 ? ZERO : scale(1.0 / length);
	}
}
