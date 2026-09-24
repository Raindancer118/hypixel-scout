package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.flight.MissileAlarm;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.Supplier;

/**
 * One of the looping warnings: missile inbound while a fireball's path or blast has the player in
 * it, the lock while somebody aims a fire charge at them.
 *
 * <p>Asks every tick whether its warning is still the one that should sound and stops itself the
 * tick it is not, so the tone ends the moment the player steps out of the way. Not positional: it is
 * in the player's head, like a cockpit's, not coming from the fireball.
 */
final class MissileTone extends AbstractTickableSoundInstance {
	/** Played by name through the sound manager; a client-only sound needs no registry entry. */
	static final Identifier LAUNCH = Identifier.fromNamespaceAndPath(HypixelScout.MOD_ID, "missile_inbound");
	static final Identifier LOCK = Identifier.fromNamespaceAndPath(HypixelScout.MOD_ID, "missile_lock");

	private final MissileAlarm.Tone kind;
	private final Supplier<MissileAlarm.Tone> wanted;

	/** @param wanted which tone should sound now; this one stops as soon as that is not {@code kind} */
	MissileTone(MissileAlarm.Tone kind, Supplier<MissileAlarm.Tone> wanted) {
		super(SoundEvent.createVariableRangeEvent(id(kind)), SoundSource.UI, RandomSource.create());
		this.kind = kind;
		this.wanted = wanted;
		this.looping = true;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.volume = kind == MissileAlarm.Tone.LOCK ? 0.5f : 0.7f;
		this.pitch = MissileAlarm.pitch(0);
	}

	static Identifier id(MissileAlarm.Tone kind) {
		return kind == MissileAlarm.Tone.LOCK ? LOCK : LAUNCH;
	}

	MissileAlarm.Tone kind() {
		return kind;
	}

	@Override
	public void tick() {
		if (wanted.get() != kind) {
			stop();
		}
	}

	/** Ends it from outside, for leaving the game or another tone taking over. */
	void end() {
		stop();
	}
}
