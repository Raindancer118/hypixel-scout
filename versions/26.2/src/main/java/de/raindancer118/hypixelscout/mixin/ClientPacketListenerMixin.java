package de.raindancer118.hypixelscout.mixin;

import de.raindancer118.hypixelscout.game.CheatSensor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands {@link CheatSensor} the packets it watches — swings, hurts, pushes, blasts and block
 * changes — before the client applies them.
 *
 * <p>Each handler runs twice: first on the network thread, where it only re-queues itself, then on
 * the render thread. The first call gives the arrival time, which pairs an attacker's swing with the
 * push on their victim ({@link CheatSensor#arrived}); the second, at the head, gives everything else
 * while the old block is still in place. Reading only; nothing is changed or sent.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static boolean hypixelscout$onRenderThread() {
		return Minecraft.getInstance().isSameThread();
	}

	@Inject(method = "handleAnimate", at = @At("HEAD"))
	private void hypixelscout$swing(ClientboundAnimatePacket packet, CallbackInfo ci) {
		boolean swing = packet.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
				|| packet.getAction() == ClientboundAnimatePacket.SWING_OFF_HAND;
		// 4 and 5 are the crit and enchanted-hit particles on the one hit.
		boolean crit = packet.getAction() == 4 || packet.getAction() == 5;
		if (!hypixelscout$onRenderThread()) {
			if (swing || crit) {
				CheatSensor.arrived(swing, packet.getId());
			}
		} else if (swing) {
			CheatSensor.onSwing(packet.getId());
		}
	}

	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void hypixelscout$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		if (!hypixelscout$onRenderThread()) {
			CheatSensor.arrived(false, packet.entityId());
		} else {
			CheatSensor.onDamage(packet);
		}
	}

	@Inject(method = "handleHurtAnimation", at = @At("HEAD"))
	private void hypixelscout$hurt(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
		if (!hypixelscout$onRenderThread()) {
			CheatSensor.arrived(false, packet.id());
		} else {
			CheatSensor.onHurtAnimation(packet.id());
		}
	}

	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
	private void hypixelscout$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		if (!hypixelscout$onRenderThread()) {
			if (packet.movement().lengthSqr() > 1e-6) {
				CheatSensor.arrived(false, packet.id());
			}
		} else {
			CheatSensor.onMotion(packet.id(), packet.movement());
		}
	}

	@Inject(method = "handleExplosion", at = @At("HEAD"))
	private void hypixelscout$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			CheatSensor.onExplosion(packet.center());
		}
	}

	@Inject(method = "handleBlockUpdate", at = @At("HEAD"))
	private void hypixelscout$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			CheatSensor.onBlock(packet.getPos(), packet.getBlockState());
		}
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
	private void hypixelscout$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			packet.runUpdates(CheatSensor::onBlock);
		}
	}
}
