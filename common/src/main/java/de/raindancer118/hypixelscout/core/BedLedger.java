package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every bed's defence as last seen this round, by the team it belongs to.
 *
 * <p>Looking at a bed scans its loaded layers while the crosshair is on it; this remembers the last
 * look until the round is over — through deaths, respawns and walking away — so the Teams screen can
 * say what each enemy bed was wrapped in. A look is only as fresh as its age says: nobody sees a bed
 * being rebuilt from the other side of the map.
 */
public final class BedLedger {
	/**
	 * @param team   the Bedwars team the bed belongs to ({@code Red}, {@code Aqua}, …)
	 * @param head   where the bed's head is
	 * @param report its defence at the last look
	 * @param seenAt when that look was
	 * @param goneAt when the bed was found missing; {@code 0} while it stands
	 */
	public static final class Entry {
		private final String team;
		private final BedDefense.Cell head;
		private final BedDefense.Report report;
		private final long seenAt;
		private final long goneAt;
		private final List<BedDefense.Cell> bed;
		private final Map<BedDefense.Cell, BedDefense.Block> seen;

		public Entry(String team, BedDefense.Cell head, BedDefense.Report report, long seenAt, long goneAt,
				List<BedDefense.Cell> bed, Map<BedDefense.Cell, BedDefense.Block> seen) {
			this.team = team;
			this.head = head;
			this.report = report;
			this.seenAt = seenAt;
			this.goneAt = goneAt;
			List<BedDefense.Cell> bedCopy = new ArrayList<>(bed.size());
			for (BedDefense.Cell cell : bed) {
				bedCopy.add(Objects.requireNonNull(cell));
			}
			this.bed = Collections.unmodifiableList(bedCopy);
			Map<BedDefense.Cell, BedDefense.Block> seenCopy = new LinkedHashMap<>();
			for (Map.Entry<BedDefense.Cell, BedDefense.Block> e : seen.entrySet()) {
				seenCopy.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
			}
			this.seen = Collections.unmodifiableMap(seenCopy);
		}

		public String team() {
			return team;
		}

		public BedDefense.Cell head() {
			return head;
		}

		public BedDefense.Report report() {
			return report;
		}

		public long seenAt() {
			return seenAt;
		}

		public long goneAt() {
			return goneAt;
		}

		public List<BedDefense.Cell> bed() {
			return bed;
		}

		public Map<BedDefense.Cell, BedDefense.Block> seen() {
			return seen;
		}

		public boolean gone() {
			return goneAt != 0;
		}

		/** Every layer in the last snapshot of this bed's defence, outermost first. */
		public List<BedDefense.Layer> layers() {
			return BedDefense.layers(bed, seen);
		}

		public long ageMillis(long now) {
			return Math.max(0, now - seenAt);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Entry)) return false;
			Entry other = (Entry) obj;
			return seenAt == other.seenAt && goneAt == other.goneAt && Objects.equals(team, other.team)
					&& Objects.equals(head, other.head) && Objects.equals(report, other.report)
					&& Objects.equals(bed, other.bed) && Objects.equals(seen, other.seen);
		}

		@Override
		public int hashCode() {
			return Objects.hash(team, head, report, seenAt, goneAt, bed, seen);
		}

		@Override
		public String toString() {
			return "Entry[team=" + team + ", head=" + head + ", report=" + report + ", seenAt=" + seenAt
					+ ", goneAt=" + goneAt + ", bed=" + bed + ", seen=" + seen + "]";
		}
	}

	private final Clock clock;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public BedLedger(Clock clock) {
		this.clock = clock;
	}

	/** A look at the bed of this dye colour ({@code red}, {@code light_blue}, …), known only by its head. */
	public synchronized void record(String dye, BedDefense.Cell head, BedDefense.Report report) {
		record(dye, Collections.singletonList(head), report);
	}

	/** A complete defence scan replaces the previous snapshot, including removed blocks. */
	public synchronized void record(String dye, List<BedDefense.Cell> bed, BedDefense.Report report) {
		String team = teamOf(dye);
		entries.put(team, new Entry(team, bed.get(0), report, clock.millis(), 0, bed, report.seen()));
	}

	/**
	 * A block placed around a bed while the player could see it go up: it joins what is known of that
	 * bed's defence, and starts a record for a bed nobody has looked at yet.
	 */
	public synchronized void watched(List<BedDefense.Cell> bed, String dye, BedDefense.Cell cell, BedDefense.Block block) {
		String team = teamOf(dye);
		Entry before = entries.get(team);
		if (before != null && before.gone()) {
			return;
		}
		Map<BedDefense.Cell, BedDefense.Block> seen = new HashMap<>();
		if (before != null && before.bed().equals(bed)) {
			seen.putAll(before.seen());
		}
		seen.put(cell, block);
		BedDefense.Report report = before == null
				? new BedDefense.Report(false, Collections.<BedDefense.Material>emptyList(), null)
				: before.report();
		long seenAt = before == null ? clock.millis() : before.seenAt();
		entries.put(team, new Entry(team, bed.get(0), report, seenAt, 0, bed, seen));
	}

	/** The bed is no longer where it was: broken. Its last defence stays on record. */
	public synchronized void markGone(String team) {
		Entry entry = entries.get(team);
		if (entry != null && !entry.gone()) {
			entries.put(team, new Entry(entry.team(), entry.head(), entry.report(), entry.seenAt(), clock.millis(),
					entry.bed(), entry.seen()));
		}
	}

	public synchronized Optional<Entry> of(String team) {
		return Optional.ofNullable(entries.get(team));
	}

	/** Every bed seen this round, in the order they were first seen. */
	public synchronized List<Entry> entries() {
		return Collections.unmodifiableList(new ArrayList<>(entries.values()));
	}

	/** The beds seen this round that were still standing at the last check. */
	public synchronized List<Entry> standing() {
		List<Entry> standing = new ArrayList<>();
		for (Entry entry : entries.values()) {
			if (!entry.gone()) {
				standing.add(entry);
			}
		}
		return standing;
	}

	public synchronized void clear() {
		entries.clear();
	}

	/**
	 * The team a bed of this dye colour belongs to, by the names Bedwars gives its teams — a lime bed
	 * is Green's, a light blue or cyan one Aqua's.
	 */
	public static String teamOf(String dye) {
		String key = dye.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		switch (key) {
			case "red":
				return "Red";
			case "blue":
				return "Blue";
			case "lime":
			case "green":
				return "Green";
			case "yellow":
				return "Yellow";
			case "light_blue":
			case "cyan":
				return "Aqua";
			case "pink":
			case "magenta":
			case "purple":
				return "Pink";
			case "gray":
			case "light_gray":
				return "Gray";
			case "white":
				return "White";
			case "black":
				return "Black";
			default:
				return capitalise(key);
		}
	}

	private static String capitalise(String key) {
		String spaced = key.replace('_', ' ');
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}
}
