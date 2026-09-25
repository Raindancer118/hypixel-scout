package de.raindancer118.hypixelscout.mixin;

import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the bed ledger every block placed into what was open space, before the client applies it —
 * so the old block is still there to compare with. On the render thread only: the handler runs once
 * on the network thread first, where it only re-queues itself. Reading only; nothing is changed or sent.
 */
@Mixin(ClientPacketListener.class)
public abstract class BlockUpdateMixin {
	private static void hypixelscout$changing(BlockPos pos, BlockState next) {
		Minecraft client = Minecraft.getInstance();
		HypixelScout mod = HypixelScout.get();
		if (mod != null && client.level != null && client.player != null
				&& client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty()) {
			mod.hazards().placed(client.level, client.player, pos, next);
		}
	}

	@Inject(method = "handleBlockUpdate", at = @At("HEAD"))
	private void hypixelscout$block(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) {
			hypixelscout$changing(packet.getPos(), packet.getBlockState());
		}
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
	private void hypixelscout$blocks(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) {
			packet.runUpdates(BlockUpdateMixin::hypixelscout$changing);
		}
	}
}
