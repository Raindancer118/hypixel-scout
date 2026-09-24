package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.flight.MissileAlarm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.function.Supplier;

/**
 * One of the looping warnings: missile inbound while a fireball's path or blast has the player in
 * it, the lock while somebody aims a fire charge at them.
 *
 * <p>Ported from 26.2's {@code game.MissileTone}. Forge 1.8.9 has no non-positional "UI" sound
 * category or {@code relative} flag: instead this follows vanilla's own pattern for a looping sound
 * that must keep playing while a condition holds ({@code MovingSoundMinecart} sets {@code repeat}/
 * {@code repeatDelay} in its constructor the same way) — {@link #update()} re-centres the sound on
 * the player every tick so it never drifts away from "in your head", and ends the loop by setting
 * {@link #donePlaying} the instant the wanted tone changes.
 */
final class MissileTone extends MovingSound {
	/** Played by name through the sound handler; a client-only sound needs no registry entry. */
	static final ResourceLocation LAUNCH = new ResourceLocation(HypixelScout.MOD_ID, "missile_inbound");
	static final ResourceLocation LOCK = new ResourceLocation(HypixelScout.MOD_ID, "missile_lock");

	private final MissileAlarm.Tone kind;
	private final Supplier<MissileAlarm.Tone> wanted;

	/** @param wanted which tone should sound now; this one stops as soon as that is not {@code kind} */
	MissileTone(MissileAlarm.Tone kind, Supplier<MissileAlarm.Tone> wanted) {
		super(id(kind));
		this.kind = kind;
		this.wanted = wanted;
		this.repeat = true;
		this.repeatDelay = 0;
		this.attenuationType = ISound.AttenuationType.NONE;
		this.volume = kind == MissileAlarm.Tone.LOCK ? 0.5f : 0.7f;
		this.pitch = MissileAlarm.pitch(0);
		followPlayer();
	}

	static ResourceLocation id(MissileAlarm.Tone kind) {
		return kind == MissileAlarm.Tone.LOCK ? LOCK : LAUNCH;
	}

	MissileAlarm.Tone kind() {
		return kind;
	}

	@Override
	public void update() {
		if (wanted.get() != kind) {
			donePlaying = true;
			return;
		}
		followPlayer();
	}

	private void followPlayer() {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player != null) {
			this.xPosF = (float) player.posX;
			this.yPosF = (float) player.posY;
			this.zPosF = (float) player.posZ;
		}
	}

	/** Ends it from outside, for leaving the game or another tone taking over. */
	void end() {
		donePlaying = true;
		Minecraft.getMinecraft().getSoundHandler().stopSound(this);
	}
}
