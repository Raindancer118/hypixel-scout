package de.raindancer118.hypixelscout.flight;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explosions as vanilla works them out (ServerExplosion.hurtEntities): whoever stands within twice
 * the power is pushed away from the centre — measured to the feet for the reach, pointed at the eyes
 * for the direction — by {@code (1 − distance / (2 · power)) · exposure}, where exposure is the share
 * of rays from their hitbox to the centre that no block stops.
 *
 * <p>And where primed TNT will be when it goes off (PrimedTnt.tick): gravity 0.04, the move, drag
 * 0.98, until the fuse runs out; it explodes 0.0625 of its height above its feet. Servers may change
 * the power and the fuse; the fuse is read from the entity, the power is vanilla's 4 unless told.
 */
public final class Blast {
	/** Primed TNT's blast power, unless a server changes it. */
	public static final double TNT_POWER = 4.0;
	private static final double TNT_HEIGHT = 0.98;

	/** A push from a blast. */
	public static final class Knock {
		private final double strength;
		private final Vec push;

		public Knock(double strength, Vec push) {
			this.strength = strength;
			this.push = push;
		}

		public double strength() {
			return strength;
		}

		public Vec push() {
			return push;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Knock)) return false;
			Knock other = (Knock) obj;
			return Double.doubleToLongBits(strength) == Double.doubleToLongBits(other.strength)
					&& Objects.equals(push, other.push);
		}

		@Override
		public int hashCode() {
			return Objects.hash(strength, push);
		}

		@Override
		public String toString() {
			return "Knock[strength=" + strength + ", push=" + push + "]";
		}
	}

	/** Where TNT will fall, and the centre of its blast. */
	public static final class Tnt {
		private final List<Vec> path;
		private final Vec center;

		public Tnt(List<Vec> path, Vec center) {
			this.path = path;
			this.center = center;
		}

		public List<Vec> path() {
			return path;
		}

		public Vec center() {
			return center;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Tnt)) return false;
			Tnt other = (Tnt) obj;
			return Objects.equals(path, other.path) && Objects.equals(center, other.center);
		}

		@Override
		public int hashCode() {
			return Objects.hash(path, center);
		}

		@Override
		public String toString() {
			return "Tnt[path=" + path + ", center=" + center + "]";
		}
	}

	private Blast() {
	}

	/** How far a blast of this power reaches anybody at all. */
	public static double reach(double power) {
		return power * 2.0;
	}

	/**
	 * The share of the hitbox a blast at {@code center} sees, 0 to 1: rays from a grid over the box
	 * (ServerExplosion.getSeenPercent's spacing and offsets) that get there without hitting a block.
	 */
	public static double exposure(Box self, Vec center, FlightPath.Obstacle obstacle) {
		double xs = 1.0 / ((self.maxX() - self.minX()) * 2.0 + 1.0);
		double ys = 1.0 / ((self.maxY() - self.minY()) * 2.0 + 1.0);
		double zs = 1.0 / ((self.maxZ() - self.minZ()) * 2.0 + 1.0);
		double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
		double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;

		int hits = 0;
		int count = 0;
		for (double xx = 0.0; xx <= 1.0; xx += xs) {
			for (double yy = 0.0; yy <= 1.0; yy += ys) {
				for (double zz = 0.0; zz <= 1.0; zz += zs) {
					Vec from = new Vec(lerp(xx, self.minX(), self.maxX()) + xOffset, lerp(yy, self.minY(), self.maxY()),
							lerp(zz, self.minZ(), self.maxZ()) + zOffset);
					if (obstacle.clip(from, center) == null) {
						hits++;
					}
					count++;
				}
			}
		}
		return count == 0 ? 0.0 : (double) hits / count;
	}

	/** The push a blast gives somebody standing at {@code feet}; {@code null} when out of its reach. */
	public static Knock knockback(Vec center, double power, Vec feet, Vec eye, double exposure) {
		double distance = feet.distanceTo(center) / reach(power);
		if (distance > 1.0) {
			return null;
		}

		Vec away = eye.subtract(center);
		Vec direction = away.length() < 1e-9 ? Vec.ZERO : away.normalize();
		double strength = (1.0 - distance) * exposure;
		return new Knock(strength, direction.scale(strength));
	}

	/**
	 * @param fuse ticks left until it goes off
	 */
	public static Tnt tnt(Vec position, Vec velocity, int fuse, FlightPath.Obstacle obstacle) {
		List<Vec> path = new ArrayList<>();
		path.add(position);
		Vec at = position;
		Vec motion = velocity;
		boolean resting = false;

		for (int tick = 0; tick < Math.max(1, fuse); tick++) {
			if (resting) {
				// On the ground: vanilla bounces it by −0.5 and brakes the slide by 0.7 a tick.
				motion = new Vec(motion.x() * 0.7, 0, motion.z() * 0.7);
				Vec next = at.add(motion);
				Vec hit = obstacle.clip(at, next);
				at = hit != null ? hit : next;
			} else {
				motion = new Vec(motion.x(), motion.y() - 0.04, motion.z());
				Vec next = at.add(motion);
				Vec hit = obstacle.clip(at, next);
				if (hit != null) {
					at = hit;
					resting = motion.y() <= 0;
					motion = resting ? new Vec(motion.x(), 0, motion.z()) : Vec.ZERO;
				} else {
					at = next;
				}
			}
			path.add(at);
			motion = motion.scale(0.98);
		}
		return new Tnt(path, new Vec(at.x(), at.y() + TNT_HEIGHT * 0.0625, at.z()));
	}

	private static double lerp(double t, double from, double to) {
		return from + t * (to - from);
	}
}
