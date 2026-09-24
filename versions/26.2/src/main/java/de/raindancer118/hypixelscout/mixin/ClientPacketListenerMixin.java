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
 * the render thread. Only the second call is passed on, and at the head, so a block update is seen
 * while the old block is still in place. Reading only; nothing is changed or sent.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static boolean hypixelscout$onRenderThread() {
		return Minecraft.getInstance().isSameThread();
	}

	@Inject(method = "handleAnimate", at = @At("HEAD"))
	private void hypixelscout$swing(ClientboundAnimatePacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread() && (packet.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND
				|| packet.getAction() == ClientboundAnimatePacket.SWING_OFF_HAND)) {
			CheatSensor.onSwing(packet.getId());
		}
	}

	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void hypixelscout$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			CheatSensor.onDamage(packet);
		}
	}

	@Inject(method = "handleHurtAnimation", at = @At("HEAD"))
	private void hypixelscout$hurt(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			CheatSensor.onHurtAnimation(packet.id());
		}
	}

	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
	private void hypixelscout$motion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
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
