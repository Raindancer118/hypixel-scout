package de.raindancer118.hypixelscout.mixin;

import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the bed ledger every block placed into what was open space, before the client applies it —
 * so the old block is still there to compare with. On the render thread only: each handler runs once
 * on the network thread first, where it only re-queues itself. Reading only; nothing is changed or
 * sent. Ported from 26.2's {@code mixin.BlockUpdateMixin}.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class BlockUpdateMixin {
	private static void hypixelscout$changing(BlockPos pos, IBlockState next) {
		Minecraft client = Minecraft.getMinecraft();
		HypixelScout mod = HypixelScout.get();
		if (mod != null && client.theWorld != null && client.thePlayer != null) {
			IBlockState before = client.theWorld.getBlockState(pos);
			if (before.getBlock().getCollisionBoundingBox(client.theWorld, pos, before) == null) {
				mod.hazards().placed(client.theWorld, client.thePlayer, pos, next);
			}
		}
	}

	@Inject(method = "handleBlockChange", at = @At("HEAD"))
	private void hypixelscout$block(S23PacketBlockChange packet, CallbackInfo ci) {
		if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
			hypixelscout$changing(packet.getBlockPosition(), packet.getBlockState());
		}
	}

	@Inject(method = "handleMultiBlockChange", at = @At("HEAD"))
	private void hypixelscout$blocks(S22PacketMultiBlockChange packet, CallbackInfo ci) {
		if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
			for (S22PacketMultiBlockChange.BlockUpdateData update : packet.getChangedBlocks()) {
				hypixelscout$changing(update.getPos(), update.getBlockState());
			}
		}
	}
}
