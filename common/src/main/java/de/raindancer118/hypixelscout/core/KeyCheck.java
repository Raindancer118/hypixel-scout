package de.raindancer118.hypixelscout.core;

import java.util.UUID;

/**
 * Whether the stored API key actually works, and if not, why not.
 *
 * <p>"A key is set" only means a string is in the config. This asks Hypixel for one player — the
 * account playing, so the answer is also a useful sanity check — and sorts the answer into the
 * handful of things a player can do something about. It costs one request of the budget.
 *
 * <p>Blocking: call it off the render thread.
 */
public final class KeyCheck {
	public enum Outcome {
		/** Nothing stored, so nothing was asked. */
		NO_KEY,
		/** Hypixel answered with a profile. */
		OK,
		/** Hypixel refused the key: wrong, expired, or revoked. */
		INVALID_KEY,
		/** The key is fine but used up for now — by this mod or anything else sharing it. */
		RATE_LIMITED,
		/** No answer at all: offline, DNS, firewall, or Hypixel down hard. */
		UNREACHABLE,
		/** Some other refusal, with Hypixel's own reason in the detail. */
		FAILED
	}

	/** @param detail the account name on success, otherwise the reason given, or {@code ""} */
	public record Result(Outcome outcome, String detail) {
	}

	private KeyCheck() {
	}

	public static Result run(HypixelClient client, UUID self) {
		if (!client.hasApiKey()) {
			return new Result(Outcome.NO_KEY, "");
		}

		try {
			PlayerStats stats = client.fetch(self, null);
			return new Result(Outcome.OK, stats.getName() == null ? "" : stats.getName());
		} catch (RateLimitedException e) {
			return new Result(Outcome.RATE_LIMITED, e.getMessage());
		} catch (HypixelApiException e) {
			return new Result(classify(e), e.getMessage());
		}
	}

	private static Outcome classify(HypixelApiException e) {
		return switch (e.getStatusCode()) {
			case HypixelApiException.NO_RESPONSE -> Outcome.UNREACHABLE;
			case 403 -> Outcome.INVALID_KEY;
			case 429 -> Outcome.RATE_LIMITED;
			default -> Outcome.FAILED;
		};
	}
}
