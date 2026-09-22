package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.List;
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
	public record Span(int start, int end, String name) {
	}

	private ChatNames() {
	}

	public static List<Span> find(String text, Predicate<String> isPlayer) {
		if (text == null || text.isEmpty()) {
			return List.of();
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
