package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the players of this game are named in a line of chat.
 *
 * <p>A name is a whole word of three to sixteen word characters — so {@code Dream} is found in
 * "Dream was killed" but not in "Dreamer". Which words count is the caller's roster.
 */
public final class ChatNames {
	private static final Pattern WORD = Pattern.compile("(?<![\\w])\\w{3,16}(?![\\w])");

	/** One name, and where in the line it sits; {@code end} is exclusive. */
	public static final class Span {
		private final int start;
		private final int end;
		private final String name;

		public Span(int start, int end, String name) {
			this.start = start;
			this.end = end;
			this.name = name;
		}

		public int start() {
			return start;
		}

		public int end() {
			return end;
		}

		public String name() {
			return name;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Span)) return false;
			Span other = (Span) obj;
			return start == other.start && end == other.end && Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(start, end, name);
		}

		@Override
		public String toString() {
			return "Span[start=" + start + ", end=" + end + ", name=" + name + "]";
		}
	}

	private ChatNames() {
	}

	public static List<Span> find(String text, Predicate<String> isPlayer) {
		if (text == null || text.isEmpty()) {
			return Collections.emptyList();
		}

		List<Span> spans = new ArrayList<>();
		Matcher matcher = WORD.matcher(text);

		while (matcher.find()) {
			if (isPlayer.test(matcher.group())) {
				spans.add(new Span(matcher.start(), matcher.end(), matcher.group()));
			}
		}

		return spans;
	}
}
