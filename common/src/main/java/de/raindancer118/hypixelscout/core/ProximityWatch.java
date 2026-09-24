package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Notices enemies walking into a radius around the player, for a short popup with their stats.
 *
 * <p>Each player pops up once when they come in. Somebody pacing along the edge would otherwise
 * pop up every other step, so they only count as having left once they are a few blocks beyond
 * the radius — or out of sight altogether — and even then not again within a cooldown.
 *
 * <p>Only reads positions the game already shows: whoever the server sent within render distance.
 */
public final class ProximityWatch {
	/** How far beyond the radius somebody has to go before coming back counts as coming in again. */
	public static final double REARM_MARGIN = 3.0;
	/** The least time between two popups for the same player. */
	public static final long COOLDOWN_MILLIS = 15_000;
	/** Popups shown at once; more at a time are noise in the middle of a fight. */
	public static final int MAX_SHOWN = 3;

	/** An enemy in the world this tick, and how far they are. */
	public static final class Sighting {
		private final UUID uuid;
		private final String name;
		private final double distance;

		public Sighting(UUID uuid, String name, double distance) {
			this.uuid = uuid;
			this.name = name;
			this.distance = distance;
		}

		public UUID uuid() {
			return uuid;
		}

		public String name() {
			return name;
		}

		public double distance() {
			return distance;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Sighting)) return false;
			Sighting other = (Sighting) obj;
			return Double.doubleToLongBits(distance) == Double.doubleToLongBits(other.distance)
					&& Objects.equals(uuid, other.uuid) && Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uuid, name, distance);
		}

		@Override
		public String toString() {
			return "Sighting[uuid=" + uuid + ", name=" + name + ", distance=" + distance + "]";
		}
	}

	/** One popup, showing until {@code until}. */
	public static final class Popup {
		private final UUID uuid;
		private final String name;
		private final long until;
		private final long since;

		public Popup(UUID uuid, String name, long until, long since) {
			this.uuid = uuid;
			this.name = name;
			this.until = until;
			this.since = since;
		}

		public UUID uuid() {
			return uuid;
		}

		public String name() {
			return name;
		}

		public long until() {
			return until;
		}

		public long since() {
			return since;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Popup)) return false;
			Popup other = (Popup) obj;
			return until == other.until && since == other.since
					&& Objects.equals(uuid, other.uuid) && Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uuid, name, until, since);
		}

		@Override
		public String toString() {
			return "Popup[uuid=" + uuid + ", name=" + name + ", until=" + until + ", since=" + since + "]";
		}
	}

	private final Clock clock;
	/** Who popped up and is not yet back to "armed", with when it happened. */
	private final Map<UUID, Long> triggered = new HashMap<>();
	/** Who left properly since popping up, and may pop up again after the cooldown. */
	private final Set<UUID> left = new HashSet<>();
	private final List<Popup> popups = new ArrayList<>();

	public ProximityWatch(Clock clock) {
		this.clock = clock;
	}

	/**
	 * @param sightings  every enemy in the world right now
	 * @param radius     how close counts as in range, in blocks
	 * @param showMillis how long a popup stays up
	 */
	public synchronized void observe(Collection<Sighting> sightings, double radius, long showMillis) {
		long now = clock.millis();
		Set<UUID> present = new HashSet<>();

		for (Sighting sighting : sightings) {
			present.add(sighting.uuid());
			Long last = triggered.get(sighting.uuid());

			if (sighting.distance() > radius + REARM_MARGIN) {
				if (last != null) {
					left.add(sighting.uuid());
				}
				continue;
			}

			if (sighting.distance() > radius) {
				continue;
			}

			boolean armed = last == null || (left.contains(sighting.uuid()) && now - last >= COOLDOWN_MILLIS);
			if (armed) {
				triggered.put(sighting.uuid(), now);
				left.remove(sighting.uuid());
				popups.removeIf(popup -> popup.uuid().equals(sighting.uuid()));
				popups.add(new Popup(sighting.uuid(), sighting.name(), now + showMillis, now));
			}
		}

		// Out of sight counts as gone: died, went behind the render distance, or left the game.
		for (UUID uuid : triggered.keySet()) {
			if (!present.contains(uuid)) {
				left.add(uuid);
			}
		}
	}

	/** The popups still up, newest first. */
	public synchronized List<Popup> showing() {
		long now = clock.millis();
		popups.removeIf(popup -> popup.until() < now);

		List<Popup> shown = new ArrayList<>();
		for (int i = popups.size() - 1; i >= 0 && shown.size() < MAX_SHOWN; i--) {
			shown.add(popups.get(i));
		}
		return shown;
	}

	/** Forgets everybody, for a new game. */
	public synchronized void reset() {
		triggered.clear();
		left.clear();
		popups.clear();
	}
}
