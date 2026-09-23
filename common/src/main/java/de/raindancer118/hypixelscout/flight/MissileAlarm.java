package de.raindancer118.hypixelscout.flight;

/**
 * The missile-inbound tone for a fireball: which warning sounds it, and at what pitch.
 *
 * <p>The tone is the F/A-18's launch warning (see {@code tools/missile_tone.py}): it loops for as
 * long as the player stays in the fireball's path or blast, steady like the real one — the urgency is
 * in the countdown on the card, not in a rising whine. An arrow is over too quickly for a tone to mean
 * anything; it keeps its single ping.
 */
public final class MissileAlarm {
	private MissileAlarm() {
	}

	public static boolean sounds(IncomingWatch.Warning warning) {
		return warning != null && warning.kind() == ProjectileKind.FIREBALL;
	}

	/** Always the recorded pitch: 455 and 555 Hz are the tone, and shifting them would change it. */
	public static float pitch(double ticksToImpact) {
		return 1.0f;
	}
}
