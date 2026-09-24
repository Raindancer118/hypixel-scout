package de.raindancer118.hypixelscout.cheat;

import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.Vec;

/**
 * One player as the client saw them at the end of one tick.
 *
 * @param feet      where they stand — the server's latest position, not the smoothed one drawn
 * @param yaw       where their head points, in Minecraft's degrees (0 = +z, 90 = −x)
 * @param pitch     up and down, positive looking down
 * @param onGround  what the server last said about them touching the ground
 * @param supported whether there is a block right under their feet
 * @param usingItem blocking, eating, drinking or drawing a bow
 * @param assisted  climbing, in water or lava, or in cobweb: movement that plain physics do not cover
 * @param riding    on something
 * @param held      what is in their main hand, as far as the checks care
 */
public record Frame(long tick, Vec feet, Vec eye, Box box, double yaw, double pitch, boolean onGround,
		boolean supported, boolean sprinting, boolean usingItem, boolean assisted, boolean riding,
		boolean sneaking, Held held) {

	/** A sword blocks, a block builds; everything else is eaten, drunk or drawn. */
	public enum Held {
		SWORD, BLOCK, OTHER, NOTHING
	}

	/** Where they look, at length one — Minecraft's own view vector. */
	public Vec look() {
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		return new Vec(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
	}

	public Vec centre() {
		return new Vec((box.minX() + box.maxX()) / 2, (box.minY() + box.maxY()) / 2, (box.minZ() + box.maxZ()) / 2);
	}

	/** How far they went since {@code before}, ignoring height. */
	public double horizontalFrom(Frame before) {
		return Math.hypot(feet.x() - before.feet.x(), feet.z() - before.feet.z());
	}
}
