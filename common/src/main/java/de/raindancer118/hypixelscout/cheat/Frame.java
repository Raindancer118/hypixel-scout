package de.raindancer118.hypixelscout.cheat;

import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.Vec;

import java.util.Objects;

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
public final class Frame {
	private final long tick;
	private final Vec feet;
	private final Vec eye;
	private final Box box;
	private final double yaw;
	private final double pitch;
	private final boolean onGround;
	private final boolean supported;
	private final boolean sprinting;
	private final boolean usingItem;
	private final boolean assisted;
	private final boolean riding;
	private final boolean sneaking;
	private final Held held;

	public Frame(long tick, Vec feet, Vec eye, Box box, double yaw, double pitch, boolean onGround,
			boolean supported, boolean sprinting, boolean usingItem, boolean assisted, boolean riding,
			boolean sneaking, Held held) {
		this.tick = tick;
		this.feet = feet;
		this.eye = eye;
		this.box = box;
		this.yaw = yaw;
		this.pitch = pitch;
		this.onGround = onGround;
		this.supported = supported;
		this.sprinting = sprinting;
		this.usingItem = usingItem;
		this.assisted = assisted;
		this.riding = riding;
		this.sneaking = sneaking;
		this.held = held;
	}

	public long tick() {
		return tick;
	}

	public Vec feet() {
		return feet;
	}

	public Vec eye() {
		return eye;
	}

	public Box box() {
		return box;
	}

	public double yaw() {
		return yaw;
	}

	public double pitch() {
		return pitch;
	}

	public boolean onGround() {
		return onGround;
	}

	public boolean supported() {
		return supported;
	}

	public boolean sprinting() {
		return sprinting;
	}

	public boolean usingItem() {
		return usingItem;
	}

	public boolean assisted() {
		return assisted;
	}

	public boolean riding() {
		return riding;
	}

	public boolean sneaking() {
		return sneaking;
	}

	public Held held() {
		return held;
	}

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

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Frame)) return false;
		Frame other = (Frame) obj;
		return tick == other.tick
				&& Double.doubleToLongBits(yaw) == Double.doubleToLongBits(other.yaw)
				&& Double.doubleToLongBits(pitch) == Double.doubleToLongBits(other.pitch)
				&& onGround == other.onGround && supported == other.supported && sprinting == other.sprinting
				&& usingItem == other.usingItem && assisted == other.assisted && riding == other.riding
				&& sneaking == other.sneaking
				&& Objects.equals(feet, other.feet) && Objects.equals(eye, other.eye)
				&& Objects.equals(box, other.box) && held == other.held;
	}

	@Override
	public int hashCode() {
		return Objects.hash(tick, feet, eye, box, yaw, pitch, onGround, supported, sprinting, usingItem,
				assisted, riding, sneaking, held);
	}

	@Override
	public String toString() {
		return "Frame[tick=" + tick + ", feet=" + feet + ", eye=" + eye + ", box=" + box + ", yaw=" + yaw
				+ ", pitch=" + pitch + ", onGround=" + onGround + ", supported=" + supported
				+ ", sprinting=" + sprinting + ", usingItem=" + usingItem + ", assisted=" + assisted
				+ ", riding=" + riding + ", sneaking=" + sneaking + ", held=" + held + "]";
	}
}
