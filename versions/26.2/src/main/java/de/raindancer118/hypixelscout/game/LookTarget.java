package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.Aim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whoever the player is aiming at.
 *
 * <p>Picked by angle rather than by a ray, so a player is still picked up at the far end of a map
 * where a ray would have to travel hundreds of blocks. The wall check is a separate step on purpose:
 * the pick says who is centred, the ray then says whether they can actually be seen. Through a wall
 * there is no answer.
 *
 * <p>The range is whatever the server tells the client about. Outside the entity tracking distance
 * the player does not exist on this side at all, and nothing client-side can change that.
 */
public final class LookTarget {
	private LookTarget() {
	}

	public static AbstractClientPlayer pick(double minimumAlignment, boolean throughWalls,
			float partialTick) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			return null;
		}

		Vec3 eyes = self.getEyePosition(partialTick);
		Vec3 look = self.getViewVector(partialTick);

		double best = minimumAlignment;
		AbstractClientPlayer target = null;

		for (AbstractClientPlayer candidate : client.level.players()) {
			if (candidate == self || candidate.isInvisible() || candidate.isRemoved()
					|| candidate.isSpectator()) {
				continue;
			}

			// Measured to wherever on their body the view passes, head to feet: aiming at the head of
			// somebody close is aiming at them.
			Vec3 feet = candidate.getPosition(partialTick);
			double top = feet.y + candidate.getBbHeight();
			double aimY = Aim.aimHeight(eyes.x, eyes.y, eyes.z, look.x, look.y, look.z,
					feet.x, feet.y, top, feet.z);
			Vec3 at = new Vec3(feet.x, Math.clamp(aimY, feet.y + 0.1, top - 0.1), feet.z);
			double alignment = Aim.alignment(eyes.x, eyes.y, eyes.z, look.x, look.y, look.z,
					at.x, at.y, at.z);

			if (alignment > best && (throughWalls || canSee(client, self, eyes, at))) {
				best = alignment;
				target = candidate;
			}
		}

		return target;
	}

	/** A clip that reaches its end without hitting a block is exactly the clear line needed. */
	private static boolean canSee(Minecraft client, LocalPlayer self, Vec3 eyes, Vec3 at) {
		HitResult hit = client.level.clip(new ClipContext(eyes, at, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, self));
		return hit.getType() == HitResult.Type.MISS;
	}
}
