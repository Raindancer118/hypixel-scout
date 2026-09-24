package de.raindancer118.hypixelscout.flight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which flying projectile is worth a warning: the first one that will hit the player.
 *
 * <p>Two kinds are never warned about. The player's own. And one first seen within three blocks of
 * the player's eyes and inside their view — thrown in their face, which they see coming anyway and
 * could not dodge with a warning either. That judgement is made once, when a projectile is first
 * seen: one that flew in from afar is not excused later by having come close.
 *
 * <p>A fireball that hits a block right beside the player still counts: the blast throws them as
 * surely as a direct hit would.
 */
public final class IncomingWatch {
	/** Closer than this, and in view, a projectile is somebody throwing it in the player's face. */
	public static final double POINT_BLANK = 3.0;
	/** How far ahead a hit is worth warning about: three seconds. */
	public static final double HORIZON_TICKS = 60.0;

	/**
	 * One projectile in the air this tick.
	 *
	 * @param id       the entity id, the same for as long as it flies
	 * @param position where it is
	 * @param path     where it will fly
	 * @param mine     whether the player shot or threw it
	 */
	public static final class Seen {
		private final int id;
		private final ProjectileKind kind;
		private final Vec position;
		private final FlightPath path;
		private final boolean mine;

		public Seen(int id, ProjectileKind kind, Vec position, FlightPath path, boolean mine) {
			this.id = id;
			this.kind = kind;
			this.position = position;
			this.path = path;
			this.mine = mine;
		}

		public int id() {
			return id;
		}

		public ProjectileKind kind() {
			return kind;
		}

		public Vec position() {
			return position;
		}

		public FlightPath path() {
			return path;
		}

		public boolean mine() {
			return mine;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Seen)) return false;
			Seen other = (Seen) obj;
			return id == other.id && mine == other.mine && kind == other.kind
					&& Objects.equals(position, other.position) && Objects.equals(path, other.path);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, kind, position, path, mine);
		}

		@Override
		public String toString() {
			return "Seen[id=" + id + ", kind=" + kind + ", position=" + position + ", path=" + path
					+ ", mine=" + mine + "]";
		}
	}

	/**
	 * The projectile to warn about.
	 *
	 * @param ticks    ticks until it hits
	 * @param position where it is now
	 * @param fresh    whether this is the first tick it is warned about, for a sound
	 */
	public static final class Warning {
		private final int id;
		private final ProjectileKind kind;
		private final double ticks;
		private final Vec position;
		private final boolean fresh;

		public Warning(int id, ProjectileKind kind, double ticks, Vec position, boolean fresh) {
			this.id = id;
			this.kind = kind;
			this.ticks = ticks;
			this.position = position;
			this.fresh = fresh;
		}

		public int id() {
			return id;
		}

		public ProjectileKind kind() {
			return kind;
		}

		public double ticks() {
			return ticks;
		}

		public Vec position() {
			return position;
		}

		public boolean fresh() {
			return fresh;
		}

		/**
		 * Where it comes from as seen by the player, in degrees: 0 straight ahead, negative to the
		 * left, positive to the right, ±180 behind. Height is left out.
		 */
		public double bearing(Vec eye, Vec look) {
			return IncomingWatch.bearing(position, eye, look);
		}

		public double seconds() {
			return ticks / 20.0;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Warning)) return false;
			Warning other = (Warning) obj;
			return id == other.id && fresh == other.fresh && kind == other.kind
					&& Double.doubleToLongBits(ticks) == Double.doubleToLongBits(other.ticks)
					&& Objects.equals(position, other.position);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, kind, ticks, position, fresh);
		}

		@Override
		public String toString() {
			return "Warning[id=" + id + ", kind=" + kind + ", ticks=" + ticks + ", position=" + position
					+ ", fresh=" + fresh + "]";
		}
	}

	/** Every projectile seen, and whether it was excused when first seen. */
	private final Map<Integer, Boolean> excused = new HashMap<>();
	private final Set<Integer> warned = new HashSet<>();

	/**
	 * @param self    the player's hitbox
	 * @param eye     where their eyes are
	 * @param look    where they look, at length one
	 * @param viewCos the cosine of half their field of view
	 */
	public Optional<Warning> update(List<Seen> seen, Box self, Vec eye, Vec look, double viewCos) {
		Set<Integer> present = new HashSet<>();
		Seen soonest = null;
		double soonestTicks = Double.MAX_VALUE;

		for (Seen projectile : seen) {
			present.add(projectile.id());
			if (projectile.kind() == ProjectileKind.PEARL) {
				// A pearl is where somebody is going, not something that hurts.
				continue;
			}
			boolean excuse = excused.computeIfAbsent(projectile.id(),
					id -> projectile.mine() || inTheFace(projectile.position(), eye, look, viewCos));
			if (excuse || projectile.mine()) {
				continue;
			}

			double ticks = ticksToHit(projectile, self);
			if (ticks >= 0 && ticks <= HORIZON_TICKS && ticks < soonestTicks) {
				soonest = projectile;
				soonestTicks = ticks;
			}
		}

		excused.keySet().retainAll(present);
		warned.retainAll(present);

		if (soonest == null) {
			return Optional.empty();
		}

		boolean fresh = warned.add(soonest.id());
		return Optional.of(new Warning(soonest.id(), soonest.kind(), soonestTicks, soonest.position(), fresh));
	}

	/** Forgets everything, for a new game. */
	public void reset() {
		excused.clear();
		warned.clear();
	}

	/**
	 * Where {@code position} is as seen from {@code eye} looking along {@code look}, in degrees: 0
	 * straight ahead, negative to the left, positive to the right, ±180 behind. Height is left out.
	 */
	public static double bearing(Vec position, Vec eye, Vec look) {
		Vec towards = position.subtract(eye);
		double cross = look.x() * towards.z() - look.z() * towards.x();
		double dot = look.x() * towards.x() + look.z() * towards.z();
		return Math.toDegrees(Math.atan2(cross, dot));
	}

	private static double ticksToHit(Seen projectile, Box self) {
		return ticksToHit(projectile.kind(), projectile.path(), self);
	}

	/** Ticks until the path hits the box or blows up close enough to it; negative for neither. */
	static double ticksToHit(ProjectileKind kind, FlightPath path, Box self) {
		double direct = path.ticksUntil(self.inflate(kind.reach()));
		if (direct >= 0) {
			return direct;
		}
		return blastHits(kind, path, self) ? path.ticks() : -1.0;
	}

	/** Whether the path ends on a block close enough to the box for the blast to reach it. */
	static boolean blastHits(ProjectileKind kind, FlightPath path, Box self) {
		return path.blocked() && kind.blast() > 0 && self.distanceTo(path.end()) <= kind.blast();
	}

	static boolean inTheFace(Vec position, Vec eye, Vec look, double viewCos) {
		Vec towards = position.subtract(eye);
		double distance = towards.length();
		if (distance > POINT_BLANK) {
			return false;
		}
		return distance < 1e-6 || towards.scale(1.0 / distance).dot(look) >= viewCos;
	}
}
