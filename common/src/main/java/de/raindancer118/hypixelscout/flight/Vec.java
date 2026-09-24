package de.raindancer118.hypixelscout.flight;

import java.util.Objects;

/** A point or a direction in the world, without anything of Minecraft's so it can be tested plainly. */
public final class Vec {
	private final double x;
	private final double y;
	private final double z;

	public Vec(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

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

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Vec)) return false;
		Vec other = (Vec) obj;
		return Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
				&& Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y)
				&& Double.doubleToLongBits(z) == Double.doubleToLongBits(other.z);
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public String toString() {
		return "Vec[x=" + x + ", y=" + y + ", z=" + z + "]";
	}
}
