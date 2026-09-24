package de.raindancer118.hypixelscout.mixin;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the server's own target position and rotation for a remote player, before
 * {@code onLivingUpdate} smooths towards it over {@code otherPlayerMPPosRotationIncrements} ticks.
 *
 * <p>1.8.9's equivalent of 26.2's {@code mixin.LivingEntityAccessor}, but for a different reason:
 * 26.2 needs the target because {@code getYHeadRot()} still lags a rotation-packet lerp; 1.8.9's
 * body yaw/pitch and position are lerped the same way ({@code EntityOtherPlayerMP.onLivingUpdate}),
 * but head yaw ({@code rotationYawHead}) is set directly by the head-look packet with no lerp at
 * all here — verified from the packet handler's own bytecode, which has no interpolation fields.
 * {@link de.raindancer118.hypixelscout.game.CheatSensor#frame} therefore reads
 * {@code rotationYawHead} straight, and only needs this accessor for the body/position target.
 */
@Mixin(EntityOtherPlayerMP.class)
public interface EntityOtherPlayerMPAccessor {
	@Accessor("otherPlayerMPX")
	double hypixelscout$targetX();

	@Accessor("otherPlayerMPY")
	double hypixelscout$targetY();

	@Accessor("otherPlayerMPZ")
	double hypixelscout$targetZ();

	@Accessor("otherPlayerMPYaw")
	double hypixelscout$targetYaw();

	@Accessor("otherPlayerMPPitch")
	double hypixelscout$targetPitch();

	@Accessor("otherPlayerMPPosRotationIncrements")
	int hypixelscout$posRotationIncrements();
}
