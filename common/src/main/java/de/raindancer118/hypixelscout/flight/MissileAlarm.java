package de.raindancer118.hypixelscout.flight;

/**
 * The missile-inbound tone for a fireball: which warning sounds it, and how urgent it sounds.
 *
 * <p>It loops for as long as the player stays in the fireball's path or blast, and gets higher —
 * and so, with the pulses sped up alongside, faster — over the last second before impact. An arrow
 * is over too quickly for a tone to mean anything; it keeps its single ping.
 */
public final class MissileAlarm {
	/** The pitch while the fireball is still more than a second out. */
	public static final float CALM_PITCH = 1.0f;
	/** The pitch at impact: half again as fast. */
	public static final float IMPACT_PITCH = 1.5f;
	/** From here on the tone rises: the last second. */
	public static final double CLOSING_TICKS = 20.0;

	private MissileAlarm() {
	}

	public static boolean sounds(IncomingWatch.Warning warning) {
		return warning != null && warning.kind() == ProjectileKind.FIREBALL;
	}

	public static float pitch(double ticksToImpact) {
		double closing = 1.0 - Math.clamp(ticksToImpact / CLOSING_TICKS, 0.0, 1.0);
		return (float) (CALM_PITCH + (IMPACT_PITCH - CALM_PITCH) * closing);
	}
}
