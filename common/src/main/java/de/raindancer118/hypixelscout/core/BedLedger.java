package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Every bed's defence as last seen this round, by the team it belongs to.
 *
 * <p>Looking at a bed shows its outside only while the crosshair is on it; this remembers the last
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
	public record Entry(String team, BedDefense.Cell head, BedDefense.Report report, long seenAt, long goneAt) {
		public boolean gone() {
			return goneAt != 0;
		}

		public long ageMillis(long now) {
			return Math.max(0, now - seenAt);
		}
	}

	private final Clock clock;
	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public BedLedger(Clock clock) {
		this.clock = clock;
	}

	/** A look at the bed of this dye colour ({@code red}, {@code light_blue}, …). */
	public synchronized void record(String dye, BedDefense.Cell head, BedDefense.Report report) {
		String team = teamOf(dye);
		entries.put(team, new Entry(team, head, report, clock.millis(), 0));
	}

	/** The bed is no longer where it was: broken. Its last defence stays on record. */
	public synchronized void markGone(String team) {
		Entry entry = entries.get(team);
		if (entry != null && !entry.gone()) {
			entries.put(team, new Entry(entry.team(), entry.head(), entry.report(), entry.seenAt(), clock.millis()));
		}
	}

	public synchronized Optional<Entry> of(String team) {
		return Optional.ofNullable(entries.get(team));
	}

	/** Every bed seen this round, in the order they were first seen. */
	public synchronized List<Entry> entries() {
		return List.copyOf(entries.values());
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
		return switch (key) {
			case "red" -> "Red";
			case "blue" -> "Blue";
			case "lime", "green" -> "Green";
			case "yellow" -> "Yellow";
			case "light_blue", "cyan" -> "Aqua";
			case "pink", "magenta", "purple" -> "Pink";
			case "gray", "light_gray" -> "Gray";
			case "white" -> "White";
			case "black" -> "Black";
			default -> capitalise(key);
		};
	}

	private static String capitalise(String key) {
		String spaced = key.replace('_', ' ');
		return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}
}
