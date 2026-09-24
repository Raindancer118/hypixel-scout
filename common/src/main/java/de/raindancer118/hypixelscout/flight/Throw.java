package de.raindancer118.hypixelscout.flight;

/**
 * How vanilla sets a thrown or shot projectile off: from just below the eyes, along the look, at the
 * item's speed, plus whatever the thrower was moving (Projectile.shootFromRotation). The random
 * spread vanilla adds on top (a hair either way) is left out: the line is where it goes on average.
 */
public final class Throw {
	/** EnderpearlItem: 1.5 blocks a tick. */
	public static final double PEARL_SPEED = 1.5;
	/** BowItem: three blocks a tick at full draw. */
	public static final double ARROW_SPEED = 3.0;

	private Throw() {
	}

	/** Where it leaves: ThrowableItemProjectile and AbstractArrow both start 0.1 below the eyes. */
	public static Vec start(Vec eye) {
		return new Vec(eye.x(), eye.y() - 0.1, eye.z());
	}

	/**
	 * @param look     where the thrower looks, at length one
	 * @param movement how the thrower moves, blocks per tick
	 * @param onGround whether they stand: their vertical movement is only added in the air
	 */
	public static Vec velocity(Vec look, double speed, Vec movement, boolean onGround) {
		return look.normalize().scale(speed).add(new Vec(movement.x(), onGround ? 0.0 : movement.y(), movement.z()));
	}

	/** BowItem.getPowerForTime: how hard a bow drawn for this many ticks shoots, 0 to 1. */
	public static double bowPower(int ticksDrawn) {
		double seconds = ticksDrawn / 20.0;
		return Math.min(1.0, (seconds * seconds + seconds * 2.0) / 3.0);
	}

	/** Vanilla lets go of nothing below a tenth of the power. */
	public static boolean bowShoots(int ticksDrawn) {
		return bowPower(ticksDrawn) >= 0.1;
	}
}
