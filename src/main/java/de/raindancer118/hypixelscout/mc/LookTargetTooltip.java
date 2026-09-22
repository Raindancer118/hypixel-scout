package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.Aim;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * The stats of whoever you are looking at, drawn under the crosshair.
 *
 * <p>The target is picked by angle rather than by a ray, so a player is still picked up at the far
 * end of a map where a ray would have to travel hundreds of blocks. The wall check is a separate
 * step and deliberately so: the pick says who is centred, and the ray then says whether you can
 * actually see them. Through a wall there is no tooltip.
 *
 * <p>The range is whatever the server is willing to tell the client about. Outside the entity
 * tracking distance the player does not exist on this side at all, and nothing client-side can
 * change that.
 */
public final class LookTargetTooltip extends Gui {
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 3;
	private static final int BACKGROUND = 0xC0100010;
	private static final int BORDER = 0x80B0A0FF;

	private final StatsService stats;
	private final ScoutConfig config;
	private final RosterTracker roster;

	public LookTargetTooltip(StatsService stats, ScoutConfig config, RosterTracker roster) {
		this.stats = stats;
		this.config = config;
		this.roster = roster;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
			return;
		}

		if (!config.isLookTooltipEnabled() || !roster.isInBedwars()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo) {
			return;
		}

		EntityPlayer target = pick(mc, event.partialTicks);
		if (target == null) {
			return;
		}

		String name = target.getName();
		java.util.UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = target.getGameProfile().getId();
		}

		// Asking here is what makes the tooltip work for somebody who walked in late: the roster
		// scan and this share one cache, so it costs a request only the first time.
		stats.request(uuid, name);

		PlayerStats playerStats = stats.peek(uuid);
		List<String> lines = StatsLines.detail(name, playerStats, stats.isPending(uuid),
				stats.failureFor(uuid));

		draw(mc, lines);
	}

	/**
	 * The player closest to the centre of the screen who is both inside the configured cone and
	 * actually visible.
	 */
	private EntityPlayer pick(Minecraft mc, float partialTicks) {
		EntityPlayerSP self = mc.thePlayer;
		Vec3 eyes = self.getPositionEyes(partialTicks);
		Vec3 look = self.getLook(partialTicks);

		double bestAlignment = config.getLookCosine();
		EntityPlayer best = null;

		for (Object each : mc.theWorld.playerEntities) {
			EntityPlayer candidate = (EntityPlayer) each;
			if (candidate == self || candidate.isInvisible() || candidate.isDead) {
				continue;
			}

			// The chest rather than the feet: aiming at a player means aiming at their body.
			double targetY = candidate.posY + candidate.getEyeHeight() * 0.7;
			double alignment = Aim.alignment(eyes.xCoord, eyes.yCoord, eyes.zCoord,
					look.xCoord, look.yCoord, look.zCoord,
					candidate.posX, targetY, candidate.posZ);

			if (alignment > bestAlignment && hasLineOfSight(mc, eyes, candidate, targetY)) {
				bestAlignment = alignment;
				best = candidate;
			}
		}

		return best;
	}

	/**
	 * Whether anything solid stands in the way. {@code rayTraceBlocks} stops at the first block it
	 * hits, so a miss — a null result — is exactly the clear line this needs.
	 */
	private boolean hasLineOfSight(Minecraft mc, Vec3 eyes, EntityPlayer target, double targetY) {
		Vec3 to = new Vec3(target.posX, targetY, target.posZ);
		// Non-solid blocks are not obstacles: glass panes, ladders and beds should not hide anyone,
		// and liquids are ignored for the same reason.
		MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eyes, to, false, true, false);

		return hit == null;
	}

	private void draw(Minecraft mc, List<String> lines) {
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, mc.fontRendererObj.getStringWidth(line));
		}

		ScaledResolution resolution = new ScaledResolution(mc);
		int x = (resolution.getScaledWidth() - width) / 2;
		// Below the crosshair, clear of it: the tooltip must not sit where you are aiming.
		int y = resolution.getScaledHeight() / 2 + 14;
		int height = lines.size() * LINE_HEIGHT;

		drawRect(x - PADDING, y - PADDING, x + width + PADDING, y + height + PADDING - 2,
				BACKGROUND);
		drawRect(x - PADDING, y - PADDING, x + width + PADDING, y - PADDING + 1, BORDER);

		for (String line : lines) {
			mc.fontRendererObj.drawStringWithShadow(line, x, y, 0xFFFFFF);
			y += LINE_HEIGHT;
		}
	}
}
