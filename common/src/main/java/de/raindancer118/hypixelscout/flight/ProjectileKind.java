package de.raindancer118.hypixelscout.flight;

/**
 * The two projectiles Bedwars is decided by, with vanilla's flight rules for each.
 *
 * <p>Arrows (AbstractArrow.tick): move by the velocity, then air drag 0.99, then gravity 0.05.
 * Fireballs (AbstractHurtingProjectile.tick): no gravity; each tick the velocity gains its own
 * direction times the acceleration power and then loses to inertia 0.95 — a straight line that
 * settles at a top speed of 19 times the acceleration.
 */
public enum ProjectileKind {
	ARROW(0.3, 0.0),
	FIREBALL(0.6, 2.5);

	/** How close the path has to pass a hitbox to count as a hit: half the projectile's size, and some. */
	private final double reach;
	/** How close to a hitbox a path that ends on a block has to end to count: the blast. */
	private final double blast;

	ProjectileKind(double reach, double blast) {
		this.reach = reach;
		this.blast = blast;
	}

	public double reach() {
		return reach;
	}

	public double blast() {
		return blast;
	}

	/** The velocity one tick later. */
	Vec nextVelocity(Vec velocity, double accelerationPower) {
		return switch (this) {
			case ARROW -> {
				Vec dragged = velocity.scale(0.99);
				yield new Vec(dragged.x(), dragged.y() - 0.05, dragged.z());
			}
			case FIREBALL -> velocity.add(velocity.normalize().scale(accelerationPower)).scale(0.95);
		};
	}
}
