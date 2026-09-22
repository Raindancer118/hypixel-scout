package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collection;
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
		this(limit, List.of());
	}

	public LookupHistory(int limit, Collection<String> initial) {
		this.limit = Math.max(1, limit);

		if (initial != null) {
			// Oldest first, so the list comes out in the order it was stored.
			List<String> reversed = new ArrayList<>(initial);
			java.util.Collections.reverse(reversed);
			reversed.forEach(this::add);
		}
	}

	public synchronized void add(String name) {
		if (name == null || name.isBlank()) {
			return;
		}

		String trimmed = name.trim();
		names.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
		names.addFirst(trimmed);

		while (names.size() > limit) {
			names.removeLast();
		}
	}

	public synchronized List<String> names() {
		return List.copyOf(names);
	}
}
