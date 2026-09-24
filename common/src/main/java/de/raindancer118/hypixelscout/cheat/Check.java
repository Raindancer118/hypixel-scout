package de.raindancer118.hypixelscout.cheat;

/**
 * What a player can be caught doing from the outside, each with how much one sighting weighs and how
 * long after one sighting the next counts again (so one incident is not counted every tick).
 *
 * <p>{@link Suspicion} flags a player for a check once the weight of their sightings reaches
 * {@link Suspicion#FLAG_AT}: a single sighting of anything but a bed nuker is never enough.
 *
 * <p>{@link #sureness()} is how likely one sighting is to be a cheat rather than lag, a missed
 * cause or bad luck — a judgement, set low where the client sees little: a block placed behind
 * somebody says less than a bed broken through end stone.
 */
public enum Check {
	/** Swinging while blocking, eating or drawing a bow — which no legit client can do. */
	AUTOBLOCK("AutoBlock", 2.5, 0, 0.25),
	/** Walking at full speed while eating, blocking or drawing a bow, or sprinting while doing it. */
	NOSLOW("NoSlow", 2.0, 20, 0.2),
	/** Sprinting backwards or sideways, or while sneaking. */
	SPRINT("Omni-sprint", 2.0, 40, 0.2),
	/** A melee hit from further than an arm reaches. */
	REACH("Reach", 3.0, 0, 0.3),
	/** Hit, and neither pushed back nor lifted. */
	VELOCITY("Anti-knockback", 3.0, 0, 0.25),
	/**
	 * A melee hit on somebody well outside the attacker's view, through a wall again and again, or
	 * while eating or drawing a bow.
	 */
	KILLAURA("KillAura", 4.0, 0, 0.35),
	/** Hitting two players in the same tick. */
	MULTIAURA("Multi-aura", 5.0, 0, 0.5),
	/** Going on at full speed through sprint-hits, which slow a legit attacker to about 0.6. */
	KEEPSPRINT("KeepSprint", 4.0, 0, 0.3),
	/** A block placed where the placer was not looking, or bridging backwards faster than legs can. */
	SCAFFOLD("Scaffold", 1.5, 0, 0.12),
	/** More blocks a second than anybody can click. */
	FASTPLACE("FastPlace", 5.0, 20, 0.35),
	/** A bed broken while still wrapped in its defence. */
	NUKER("Bed nuker", 10.0, 0, 0.8),
	/** Moving faster than a Speed II sprint-jump, with nothing to push them. */
	SPEED("Speed", 3.0, 40, 0.3),
	/** Moving about in mid-air without falling. */
	FLY("Fly", 5.0, 60, 0.4);

	private final String label;
	private final double weight;
	private final int cooldownTicks;
	private final double sureness;

	Check(String label, double weight, int cooldownTicks, double sureness) {
		this.label = label;
		this.weight = weight;
		this.cooldownTicks = cooldownTicks;
		this.sureness = sureness;
	}

	public double sureness() {
		return sureness;
	}

	public String label() {
		return label;
	}

	public double weight() {
		return weight;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}
}
