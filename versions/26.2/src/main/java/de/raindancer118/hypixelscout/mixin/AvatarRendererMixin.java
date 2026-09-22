package de.raindancer118.hypixelscout.mixin;

import de.raindancer118.hypixelscout.game.Nametags;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands each player's finished nametag to {@link Nametags} before the frame draws it.
 *
 * <p>At the tail of the render-state extraction, so vanilla has already decided whether a tag is
 * shown at all — sneaking, invisibility and distance keep working exactly as they always did.
 * Client rendering only; nothing here reaches the server.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void hypixelscout$decorateNameTag(Avatar entity, AvatarRenderState state, float partialTick,
			CallbackInfo ci) {
		if (state.nameTag != null) {
			state.nameTag = Nametags.decorate(entity, state.nameTag);
		}
	}
}
