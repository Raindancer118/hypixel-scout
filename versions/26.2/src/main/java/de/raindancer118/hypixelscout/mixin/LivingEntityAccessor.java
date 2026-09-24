package de.raindancer118.hypixelscout.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the head-yaw interpolation target set by the last rotation packet. {@code getYHeadRot()}
 * only returns the value on its way there — for every other player it is still lerping towards
 * {@code lerpYHeadRot} over {@code lerpHeadSteps} ticks, the same way the position and pitch
 * interpolation used elsewhere lags the server's actual value by a tick or more.
 * {@link de.raindancer118.hypixelscout.game.CheatSensor#frame} reads the target instead, once it is
 * active, so a Scaffold look-check does not judge a placement against a look that has not caught up
 * to the rotation packet yet.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("lerpYHeadRot")
	double hypixelscout$lerpYHeadRot();

	@Accessor("lerpHeadSteps")
	int hypixelscout$lerpHeadSteps();
}
