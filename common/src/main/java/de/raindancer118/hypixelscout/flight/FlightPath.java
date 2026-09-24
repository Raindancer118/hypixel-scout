package de.raindancer118.hypixelscout.flight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Where a projectile will be, tick by tick, until it hits a block or flies out of reach.
 *
 * <p>The first point is where it is now, each following one a tick later. Water, which slows both
 * kinds, is not modelled: nobody fights under water in Bedwars.
 */
public final class FlightPath {
	/** Further than this nobody aims, and further than render distance nobody sees. */
	public static final double MAX_REACH = 96.0;

	/** The world, as far as a flying projectile is concerned. */
	@FunctionalInterface
	public interface Obstacle {
		/** Where the straight line from {@code from} to {@code to} first hits a block, or {@code null}. */
		Vec clip(Vec from, Vec to);
	}

	private final List<Vec> points;
	private final boolean blocked;

	/**
	 * @param points  the positions, now first
	 * @param blocked whether the path ends because it runs into a block
	 */
	public FlightPath(List<Vec> points, boolean blocked) {
		List<Vec> copy = new ArrayList<>(points.size());
		for (Vec point : points) {
			copy.add(Objects.requireNonNull(point));
		}
		this.points = Collections.unmodifiableList(copy);
		this.blocked = blocked;
	}

	public List<Vec> points() {
		return points;
	}

	public boolean blocked() {
		return blocked;
	}

	/**
	 * @param position          where it is now
	 * @param velocity          blocks per tick, as it will move on the next tick
	 * @param accelerationPower a fireball's push along its direction; ignored for arrows
	 * @param maxTicks          how far ahead to look
	 */
	public static FlightPath predict(ProjectileKind kind, Vec position, Vec velocity, double accelerationPower,
			int maxTicks, Obstacle obstacle) {
		List<Vec> points = new ArrayList<>();
		points.add(position);

		if (velocity.length() < 1e-6) {
			return new FlightPath(points, false);
		}

		Vec at = position;
		Vec motion = velocity;
		// A fireball speeds up and a pearl falls before it moves, an arrow after: vanilla's order for each.
		if (kind != ProjectileKind.ARROW) {
			motion = kind.nextVelocity(motion, accelerationPower);
		}

		for (int tick = 0; tick < maxTicks; tick++) {
			Vec next = at.add(motion);
			Vec hit = obstacle.clip(at, next);
			if (hit != null) {
				points.add(hit);
				return new FlightPath(points, true);
			}

			points.add(next);
			at = next;
			if (at.distanceTo(position) > MAX_REACH) {
				break;
			}
			motion = kind.nextVelocity(motion, accelerationPower);
		}
		return new FlightPath(points, false);
	}

	/**
	 * How many ticks until the path enters the box, with the fraction of the tick it enters in; {@code
	 * 0} when it starts inside, negative when it never gets there.
	 */
	public double ticksUntil(Box box) {
		if (box.contains(points.get(0))) {
			return 0.0;
		}

		for (int i = 1; i < points.size(); i++) {
			double entry = box.entry(points.get(i - 1), points.get(i));
			if (entry >= 0) {
				return i - 1 + entry;
			}
		}
		return -1.0;
	}

	/**
	 * Where it is after {@code ticks}, fractions included, straight between the tick's two points —
	 * as {@link #ticksUntil} counts them. Clamped to the path's start and end.
	 */
	public Vec at(double ticks) {
		if (ticks <= 0) {
			return points.get(0);
		}
		if (ticks >= ticks()) {
			return end();
		}
		int whole = (int) Math.floor(ticks);
		Vec from = points.get(whole);
		return from.add(points.get(whole + 1).subtract(from).scale(ticks - whole));
	}

	/** Where the path ends: the block it hits, or the last point looked at. */
	public Vec end() {
		return points.get(points.size() - 1);
	}

	/** Ticks from now until the end. */
	public int ticks() {
		return points.size() - 1;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof FlightPath)) return false;
		FlightPath other = (FlightPath) obj;
		return blocked == other.blocked && points.equals(other.points);
	}

	@Override
	public int hashCode() {
		return Objects.hash(points, blocked);
	}

	@Override
	public String toString() {
		return "FlightPath[points=" + points + ", blocked=" + blocked + "]";
	}
}
