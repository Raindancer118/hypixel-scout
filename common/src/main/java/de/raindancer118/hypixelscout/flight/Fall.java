package de.raindancer118.hypixelscout.flight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Where the player comes down, if anywhere: vanilla's air movement (LivingEntity.travel with nobody
 * pressing a key — move, then {@code y = (y − 0.08) · 0.98} and {@code x, z · 0.91}) run forward
 * through the blocks the client knows.
 *
 * <p>Collisions the way the player's hitbox has them: the fall stops on the first block under any
 * corner of the feet or their middle; a wall only stops the sideways motion. The keys the player
 * presses on the way down are not known ahead, so this is where they land if they let go — which is
 * exactly the question when knocked off a bridge.
 */
public final class Fall {
	private static final double GRAVITY = 0.08;
	private static final double VERTICAL_DRAG = 0.98;
	private static final double AIR_FRICTION = 0.91;

	private final List<Vec> points;
	private final Vec landing;
	private final boolean intoTheVoid;
	private final int ticks;

	/**
	 * @param points     the feet, tick by tick, now first
	 * @param landing    where the feet come down; {@code null} for nowhere
	 * @param intoTheVoid whether the fall goes below the world's bottom
	 */
	public Fall(List<Vec> points, Vec landing, boolean intoTheVoid, int ticks) {
		List<Vec> copy = new ArrayList<>(points.size());
		for (Vec point : points) {
			copy.add(Objects.requireNonNull(point));
		}
		this.points = Collections.unmodifiableList(copy);
		this.landing = landing;
		this.intoTheVoid = intoTheVoid;
		this.ticks = ticks;
	}

	public List<Vec> points() {
		return points;
	}

	public Vec landing() {
		return landing;
	}

	public boolean intoTheVoid() {
		return intoTheVoid;
	}

	public int ticks() {
		return ticks;
	}

	/**
	 * @param halfWidth half the hitbox's width: 0.3 for a player
	 * @param voidY     the bottom of the world; below it there is nothing to land on
	 */
	public static Fall predict(Vec feet, Vec velocity, double halfWidth, FlightPath.Obstacle obstacle, double voidY,
			int maxTicks) {
		List<Vec> points = new ArrayList<>();
		points.add(feet);
		Vec at = feet;
		Vec motion = velocity;

		for (int tick = 1; tick <= maxTicks; tick++) {
			// Sideways first, at shin and chest height; a wall takes the sideways speed away.
			Vec sideways = new Vec(at.x() + motion.x(), at.y(), at.z() + motion.z());
			if (motion.x() != 0 || motion.z() != 0) {
				if (blockedSideways(at, sideways, halfWidth, obstacle)) {
					motion = new Vec(0, motion.y(), 0);
					sideways = at;
				}
			}

			// Then down (or up), by the lowest block under any part of the feet.
			Vec next = new Vec(sideways.x(), sideways.y() + motion.y(), sideways.z());
			if (motion.y() < 0) {
				Double floor = floorBetween(sideways, next, halfWidth, obstacle);
				if (floor != null) {
					Vec landing = new Vec(next.x(), floor, next.z());
					points.add(landing);
					return new Fall(points, landing, false, tick);
				}
			}

			at = next;
			points.add(at);
			if (at.y() < voidY) {
				return new Fall(points, null, true, tick);
			}
			motion = new Vec(motion.x() * AIR_FRICTION, (motion.y() - GRAVITY) * VERTICAL_DRAG, motion.z() * AIR_FRICTION);
		}
		return new Fall(points, null, false, maxTicks);
	}

	private static boolean blockedSideways(Vec from, Vec to, double halfWidth, FlightPath.Obstacle obstacle) {
		Vec step = to.subtract(from);
		Vec lead = new Vec(Math.signum(step.x()) * halfWidth, 0, Math.signum(step.z()) * halfWidth);
		for (double height : new double[] {0.05, 1.0, 1.75}) {
			Vec start = new Vec(from.x() + lead.x(), from.y() + height, from.z() + lead.z());
			if (obstacle.clip(start, start.add(step)) != null) {
				return true;
			}
		}
		return false;
	}

	/** The highest floor under the feet's corners and middle on the way down, or {@code null}. */
	private static Double floorBetween(Vec from, Vec to, double halfWidth, FlightPath.Obstacle obstacle) {
		Double best = null;
		double inset = halfWidth - 1e-3;
		double[][] offsets = {{0, 0}, {-inset, -inset}, {-inset, inset}, {inset, -inset}, {inset, inset}};
		for (double[] offset : offsets) {
			Vec start = new Vec(from.x() + offset[0], from.y(), from.z() + offset[1]);
			Vec end = new Vec(to.x() + offset[0], to.y(), to.z() + offset[1]);
			Vec hit = obstacle.clip(start, end);
			if (hit != null && (best == null || hit.y() > best)) {
				best = hit.y();
			}
		}
		return best;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Fall)) return false;
		Fall other = (Fall) obj;
		return intoTheVoid == other.intoTheVoid && ticks == other.ticks
				&& points.equals(other.points) && Objects.equals(landing, other.landing);
	}

	@Override
	public int hashCode() {
		return Objects.hash(points, landing, intoTheVoid, ticks);
	}

	@Override
	public String toString() {
		return "Fall[points=" + points + ", landing=" + landing + ", intoTheVoid=" + intoTheVoid
				+ ", ticks=" + ticks + "]";
	}
}
