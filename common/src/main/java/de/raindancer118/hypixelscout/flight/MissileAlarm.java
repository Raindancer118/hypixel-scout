package de.raindancer118.hypixelscout.flight;

/**
 * The missile-inbound tone for a fireball: which warning sounds it, and at what pitch.
 *
 * <p>The tone is the F/A-18's launch warning (see {@code tools/missile_tone.py}): it loops for as
 * long as the player stays in the fireball's path or blast, steady like the real one — the urgency is
 * in the countdown on the card, not in a rising whine. An arrow is over too quickly for a tone to mean
 * anything; it keeps its single ping.
 *
 * <p>Before a fireball is thrown there is the lock ({@link LockWatch}): somebody aiming a fire charge
 * at the player. It has a tone of its own, a steady beeping, so the two are told apart by ear —
 * beeping, they are aiming; warbling, it is on its way. The launch outranks the lock: once one is
 * coming, that is the one to dodge.
 */
public final class MissileAlarm {
	private MissileAlarm() {
	}

	/** Which looping warning sounds, if any. */
	public enum Tone {
		NONE, LAUNCH, LOCK
	}

	public static Tone tone(IncomingWatch.Warning warning, LockWatch.Lock lock) {
		if (warning != null && warning.kind() == ProjectileKind.FIREBALL) {
			return Tone.LAUNCH;
		}
		return lock != null ? Tone.LOCK : Tone.NONE;
	}

	/** Always the recorded pitch: 455 and 555 Hz are the tone, and shifting them would change it. */
	public static float pitch(double ticksToImpact) {
		return 1.0f;
	}
}
