package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.MissileAlarm;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The missile-inbound tone, looping for as long as a fireball's path or blast has the player in it.
 *
 * <p>Asks every tick whether the warning still stands and stops itself the tick it does not, so the
 * tone ends the moment the player steps out of the way. Not positional: it is in the player's head,
 * like a cockpit's, not coming from the fireball.
 */
final class MissileTone extends AbstractTickableSoundInstance {
	/** Played by name through the sound manager; a client-only sound needs no registry entry. */
	static final Identifier ID = Identifier.fromNamespaceAndPath(HypixelScout.MOD_ID, "missile_inbound");
	private static final SoundEvent EVENT = SoundEvent.createVariableRangeEvent(ID);

	private final Supplier<IncomingWatch.Warning> warning;
	private final BooleanSupplier wanted;

	MissileTone(Supplier<IncomingWatch.Warning> warning, BooleanSupplier wanted) {
		super(EVENT, SoundSource.UI, RandomSource.create());
		this.warning = warning;
		this.wanted = wanted;
		this.looping = true;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.volume = 0.7f;
		follow();
	}

	@Override
	public void tick() {
		if (!follow()) {
			stop();
		}
	}

	/** Takes the pitch from the current warning; false once there is none worth the tone. */
	private boolean follow() {
		IncomingWatch.Warning current = warning.get();
		if (!wanted.getAsBoolean() || !MissileAlarm.sounds(current)) {
			return false;
		}
		pitch = MissileAlarm.pitch(current.ticks());
		return true;
	}

	/** Ends it from outside, for leaving the game. */
	void end() {
		stop();
	}
}
