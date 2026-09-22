package de.raindancer118.hypixelscout.core;

/** Hypixel answered, and said no: a bad key, a throttle, or an outage. */
public class HypixelApiException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** The status of a request that never got an answer: offline, refused, timed out. */
	public static final int NO_RESPONSE = -1;

	private final int statusCode;

	public HypixelApiException(String message) {
		this(message, 0);
	}

	public HypixelApiException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	/**
	 * The HTTP status, {@code 0} when the failure was in the body rather than the status, or
	 * {@link #NO_RESPONSE} when nothing came back at all.
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/** A 403 is the one error worth telling the player about in chat: their key is wrong. */
	public boolean isKeyProblem() {
		return statusCode == 403 || getMessage().toLowerCase(java.util.Locale.ROOT).contains("api key");
	}
}
