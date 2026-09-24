package de.raindancer118.hypixelscout.mixin;

import de.raindancer118.hypixelscout.game.CheatSensor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands {@link CheatSensor} the packets it watches — swings, hurts, pushes, blasts and block
 * changes — before the client applies them.
 *
 * <p>Ported from 26.2's {@code mixin.ClientPacketListenerMixin}. Every {@code handle*} method here
 * opens with {@code PacketThreadUtil.checkThreadAndEnqueue}, which re-queues the packet onto the
 * render thread and throws {@code ThreadQuickExitException} to unwind the network-thread call —
 * so an {@code @Inject at HEAD} genuinely runs twice: once on the network thread (before that
 * throw, which is where the arrival-time pairing in {@link CheatSensor#arrived} happens), and once
 * more on the render thread after the check passes silently (where every other hook fires, with
 * the old block/world state still in place). 1.8.9 has no separate damage-event or hurt-animation
 * packet, no per-entity motion packet distinct from the knockback one, and no head-look packet with
 * a payload beyond the raw byte yaw — {@link S19PacketEntityStatus} (opcode 2) is the only "you got
 * hit" signal, {@link S12PacketEntityVelocity} is both the push and this mixin's motion hook, and
 * the cause of a hit is never told apart from a fist versus something else, hence
 * {@link CheatSensor#onDamage} always attributing {@code Hit.UNKNOWN} — matching {@code CheatWatch}'s
 * own handling for a server that does not say.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {
	private static boolean hypixelscout$onRenderThread() {
		return Minecraft.getMinecraft().isCallingFromMinecraftThread();
	}

	@Inject(method = "handleAnimation", at = @At("HEAD"))
	private void hypixelscout$swing(S0BPacketAnimation packet, CallbackInfo ci) {
		// 0 = swing main arm, 1 = hurt (unused here), 4/5 = crit/enchanted-hit particles on a hit.
		boolean swing = packet.getAnimationType() == 0;
		boolean crit = packet.getAnimationType() == 4 || packet.getAnimationType() == 5;
		if (!hypixelscout$onRenderThread()) {
			if (swing || crit) {
				CheatSensor.arrived(swing, packet.getEntityID());
			}
		} else if (swing) {
			CheatSensor.onSwing(packet.getEntityID());
		}
	}

	@Inject(method = "handleEntityStatus", at = @At("HEAD"))
	private void hypixelscout$status(S19PacketEntityStatus packet, CallbackInfo ci) {
		if (packet.getOpCode() != 2) {
			// Only "entity hurt" (2) is a cheat-relevant status here; deaths (3) and the rest are not.
			return;
		}
		if (!hypixelscout$onRenderThread()) {
			CheatSensor.arrived(false, hypixelscout$entityId(packet));
		} else {
			CheatSensor.onHurt(hypixelscout$entityId(packet));
		}
	}

	private static int hypixelscout$entityId(S19PacketEntityStatus packet) {
		net.minecraft.entity.Entity entity = packet.getEntity(Minecraft.getMinecraft().theWorld);
		return entity == null ? -1 : entity.getEntityId();
	}

	@Inject(method = "handleEntityVelocity", at = @At("HEAD"))
	private void hypixelscout$motion(S12PacketEntityVelocity packet, CallbackInfo ci) {
		if (!hypixelscout$onRenderThread()) {
			if (packet.getMotionX() != 0 || packet.getMotionY() != 0 || packet.getMotionZ() != 0) {
				CheatSensor.arrived(false, packet.getEntityID());
			}
		} else {
			// The packet's ints are 1/8000ths of a block per tick (EntityVelocity's own scale).
			Vec3 movement = new Vec3(packet.getMotionX() / 8000.0, packet.getMotionY() / 8000.0,
					packet.getMotionZ() / 8000.0);
			CheatSensor.onMotion(packet.getEntityID(), movement);
		}
	}

	@Inject(method = "handleExplosion", at = @At("HEAD"))
	private void hypixelscout$explosion(S27PacketExplosion packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			CheatSensor.onExplosion(new Vec3(packet.getX(), packet.getY(), packet.getZ()));
		}
	}

	@Inject(method = "handleBlockChange", at = @At("HEAD"))
	private void hypixelscout$block(S23PacketBlockChange packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			CheatSensor.onBlock(packet.getBlockPosition(), packet.getBlockState());
		}
	}

	@Inject(method = "handleMultiBlockChange", at = @At("HEAD"))
	private void hypixelscout$blocks(S22PacketMultiBlockChange packet, CallbackInfo ci) {
		if (hypixelscout$onRenderThread()) {
			for (S22PacketMultiBlockChange.BlockUpdateData update : packet.getChangedBlocks()) {
				CheatSensor.onBlock(update.getPos(), update.getBlockState());
			}
		}
	}
}
