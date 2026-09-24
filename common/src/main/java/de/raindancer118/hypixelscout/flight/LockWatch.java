package de.raindancer118.hypixelscout.flight;

import java.util.List;
import java.util.Optional;

/**
 * Decides whether somebody has the player locked: holds a fire charge and looks so that a fireball
 * thrown this instant would hit them, or blow up close enough to throw them.
 *
 * <p>Nothing is thrown yet, so this is the warning before the missile-inbound one — the radar lock
 * before the launch. The fireball is followed as {@link IncomingWatch} follows a real one: straight
 * from the eyes along the look, through the blocks the client knows, for as long as a warning about
 * a real one would look ahead.
 *
 * <p>Head turns reach the client in steps of about 1.4°, so the player's hitbox is widened by {@link
 * #AIM_SLACK} for the test: across thirty blocks a step is already most of a block. Somebody in plain
 * view within {@link IncomingWatch#POINT_BLANK} is left out, like a fireball thrown there: the player
 * sees the fire charge, and a tone through every close fight would only drown the real warnings.
 */
public final class LockWatch {
	/** How far beside the player a lock still counts, for the coarse head angles the client gets. */
	public static final double AIM_SLACK = 0.5;

	/**
	 * Somebody holding a fire charge.
	 *
	 * @param eye  where their eyes are, which is where a fireball leaves
	 * @param path where a fireball thrown now would fly
	 */
	public record Aimer(int id, String name, Vec eye, FlightPath path) {
	}

	/**
	 * The player is locked.
	 *
	 * @param from  where the one aiming stands, at eye height
	 * @param point where the fireball would meet the player, or the block it would blow up on
	 * @param ticks how long a fireball thrown now would take to get there
	 * @param blast whether it is the blast that would reach the player, not the fireball itself
	 */
	public record Lock(int id, String name, Vec from, Vec point, double ticks, boolean blast) {
		/** Where they stand as seen by the player, in degrees; see {@link IncomingWatch.Warning#bearing}. */
		public double bearing(Vec eye, Vec look) {
			return IncomingWatch.bearing(from, eye, look);
		}
	}

	/**
	 * @param self    the player's hitbox
	 * @param eye     where their eyes are
	 * @param look    where they look, at length one
	 * @param viewCos the cosine of half their field of view
	 * @return the lock that would hit first, if anybody has one
	 */
	public Optional<Lock> update(List<Aimer> aimers, Box self, Vec eye, Vec look, double viewCos) {
		Box target = self.inflate(AIM_SLACK);
		Lock soonest = null;

		for (Aimer aimer : aimers) {
			if (IncomingWatch.inTheFace(aimer.eye(), eye, look, viewCos)) {
				continue;
			}

			double ticks = IncomingWatch.ticksToHit(ProjectileKind.FIREBALL, aimer.path(), target);
			if (ticks < 0 || ticks > IncomingWatch.HORIZON_TICKS || (soonest != null && ticks >= soonest.ticks())) {
				continue;
			}

			boolean blast = aimer.path().ticksUntil(target.inflate(ProjectileKind.FIREBALL.reach())) < 0;
			Vec point = blast ? aimer.path().end() : spotOn(aimer.path(), self);
			soonest = new Lock(aimer.id(), aimer.name(), aimer.eye(), point, ticks, blast);
		}
		return Optional.ofNullable(soonest);
	}

	/**
	 * Where a fireball on this path would meet the player: where it enters their hitbox, or — when it
	 * only grazes them within the slack — where it passes closest to their middle.
	 */
	private static Vec spotOn(FlightPath path, Box self) {
		double entry = path.ticksUntil(self);
		if (entry >= 0) {
			return path.at(entry);
		}

		Vec middle = new Vec((self.minX() + self.maxX()) / 2, (self.minY() + self.maxY()) / 2,
				(self.minZ() + self.maxZ()) / 2);
		List<Vec> points = path.points();
		Vec closest = points.getFirst();
		for (int i = 1; i < points.size(); i++) {
			Vec from = points.get(i - 1);
			Vec along = points.get(i).subtract(from);
			double lengthSquared = along.dot(along);
			double t = lengthSquared < 1e-12 ? 0 : Math.clamp(middle.subtract(from).dot(along) / lengthSquared, 0.0, 1.0);
			Vec candidate = from.add(along.scale(t));
			if (candidate.distanceTo(middle) < closest.distanceTo(middle)) {
				closest = candidate;
			}
		}
		return closest;
	}
}
