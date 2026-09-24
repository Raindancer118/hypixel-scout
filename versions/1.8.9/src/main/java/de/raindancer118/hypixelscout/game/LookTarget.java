package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.Aim;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Whoever the player is aiming at.
 *
 * <p>Picked by angle rather than by a ray, so a player is still picked up at the far end of a map
 * where a ray would have to travel hundreds of blocks. The wall check is a separate step on
 * purpose: the pick says who is centred, the ray then says whether they can actually be seen.
 * Through a wall there is no answer.
 *
 * <p>Ported from 26.2's {@code game.LookTarget}. 1.8.9 has no partial-tick eye/look-vector helpers
 * on {@code EntityPlayer} the way 26.2's {@code LocalPlayer} does — {@link EntityPlayer#getPositionEyes}
 * and {@link EntityPlayer#getLook} already answer for "this frame" without a separate partial-tick
 * argument, so this reads the candidates' raw {@code posX/posY/posZ} rather than interpolating them;
 * good enough at the ranges this picker is used at (a crosshair tooltip, a callout key), and exactly
 * what the old {@code 1.8.9-support} branch's own aim code did.
 */
public final class LookTarget {
	private LookTarget() {
	}

	public static EntityPlayer pick(double minimumAlignment, boolean throughWalls) {
		Minecraft client = Minecraft.getMinecraft();
		EntityPlayer self = client.thePlayer;
		if (self == null || client.theWorld == null) {
			return null;
		}

		Vec3 eyes = self.getPositionEyes(1.0F);
		Vec3 look = self.getLook(1.0F);

		double best = minimumAlignment;
		EntityPlayer target = null;

		for (Object entry : client.theWorld.playerEntities) {
			EntityPlayer candidate = (EntityPlayer) entry;
			if (candidate == self || candidate.isInvisible() || candidate.isDead) {
				continue;
			}

			double top = candidate.posY + candidate.height;
			double aimY = Aim.aimHeight(eyes.xCoord, eyes.yCoord, eyes.zCoord, look.xCoord, look.yCoord,
					look.zCoord, candidate.posX, candidate.posY, top, candidate.posZ);
			double clampedY = Math.max(candidate.posY + 0.1, Math.min(top - 0.1, aimY));
			double alignment = Aim.alignment(eyes.xCoord, eyes.yCoord, eyes.zCoord, look.xCoord,
					look.yCoord, look.zCoord, candidate.posX, clampedY, candidate.posZ);

			if (alignment > best && (throughWalls
					|| canSee(client, eyes, new Vec3(candidate.posX, clampedY, candidate.posZ)))) {
				best = alignment;
				target = candidate;
			}
		}

		return target;
	}

	/** A clip that reaches its end without hitting a block first is exactly the clear line needed. */
	private static boolean canSee(Minecraft client, Vec3 from, Vec3 to) {
		MovingObjectPosition hit = client.theWorld.rayTraceBlocks(from, to);
		return hit == null;
	}
}
