package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The players looked up by name lately, newest first, so the lookup screen can offer them again.
 *
 * <p>Case-insensitive, because {@code technoblade} and {@code Technoblade} are one account, and
 * bounded, because nobody scrolls through last month's searches.
 */
public final class LookupHistory {
	private final int limit;
	private final List<String> names = new ArrayList<>();

	public LookupHistory(int limit) {
		this(limit, Collections.<String>emptyList());
	}

	public LookupHistory(int limit, Collection<String> initial) {
		this.limit = Math.max(1, limit);

		if (initial != null) {
			// Oldest first, so the list comes out in the order it was stored.
			List<String> reversed = new ArrayList<>(initial);
			Collections.reverse(reversed);
			reversed.forEach(this::add);
		}
	}

	public synchronized void add(String name) {
		if (name == null || name.trim().isEmpty()) {
			return;
		}

		String trimmed = name.trim();
		names.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
		names.add(0, trimmed);

		while (names.size() > limit) {
			names.remove(names.size() - 1);
		}
	}

	public synchronized List<String> names() {
		return Collections.unmodifiableList(new ArrayList<>(names));
	}
}
