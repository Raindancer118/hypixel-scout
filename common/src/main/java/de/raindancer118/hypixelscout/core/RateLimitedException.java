package de.raindancer118.hypixelscout.core;

/**
 * The mod's own budget for this window is spent.
 *
 * <p>Distinct from a 403 or a 429 because nothing was sent: the caller can try the same player
 * again in a moment, and the overlay says "…" rather than "error".
 */
public class RateLimitedException extends HypixelApiException {
	private static final long serialVersionUID = 1L;

	public RateLimitedException(String message) {
		super(message);
	}
}
