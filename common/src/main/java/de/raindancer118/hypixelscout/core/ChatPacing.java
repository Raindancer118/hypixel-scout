package de.raindancer118.hypixelscout.core;

/**
 * How long to wait between two chat lines the mod sends.
 *
 * <p>The player picks the interval. Hypixel still has the last word: without a rank it lets a
 * player chat only every three seconds and swallows anything sent sooner, so a shorter interval
 * would lose lines instead of saving time.
 */
public final class ChatPacing {
	/** Half a second: faster than this reads as spam, to Hypixel and to whoever gets the lines. */
	public static final int MIN_TICKS = 10;
	public static final int MAX_TICKS = 100;
	/** Hypixel's three seconds for players without a rank, with a little room for lag. */
	public static final int UNRANKED_TICKS = 64;

	private ChatPacing() {
	}

	public static int ticksBetween(boolean ranked, int configuredTicks) {
		int ticks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, configuredTicks));
		return ranked ? ticks : Math.max(ticks, UNRANKED_TICKS);
	}
}
