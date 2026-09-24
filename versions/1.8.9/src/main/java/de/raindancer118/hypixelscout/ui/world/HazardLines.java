package de.raindancer118.hypixelscout.ui.world;

import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.flight.Fall;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.game.Hazards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * The world half of {@link Hazards}, drawn by hand with GL lines ({@link WorldLines}) — 1.8.9's
 * equivalent of 26.2's vanilla-gizmo drawing in {@code ui.world.HazardLines}: primed TNT's fall and
 * the reach of its blast (full and half strength), the spot a long fall comes down on, and the
 * softest exposed block of the bed being looked at.
 */
public final class HazardLines {
	private static final int TNT_SAFE = 0xC0FF9A3C;
	private static final int TNT_HITS = 0xF0FF3B3B;
	private static final int LANDING = 0xE06ADF8A;
	private static final int WEAK_SPOT = 0xF0FFCC55;
	private static final float WIDTH = 2.5f;

	private final Hazards hazards;

	public HazardLines(Hazards hazards) {
		this.hazards = hazards;
	}

	public void register() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onRenderWorldLast(RenderWorldLastEvent event) {
		Minecraft client = Minecraft.getMinecraft();
		if (!hazards.active(client)) {
			return;
		}

		EntityPlayerSP player = client.thePlayer;
		float partialTick = event.partialTicks;
		double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTick;
		double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTick;
		double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTick;

		WorldLines.begin(camX, camY, camZ);
		try {
			for (Hazards.Tnt tnt : hazards.tnt()) {
				drawTnt(tnt);
			}

			Fall fall = hazards.fall();
			if (fall != null && fall.landing() != null) {
				Vec3 at = Flights.vec3(fall.landing()).addVector(0, 0.02, 0);
				WorldLines.circle(at, 0.45f, LANDING, WIDTH);
			}

			Hazards.Bed bed = hazards.bed();
			if (bed != null && bed.report().weakest() != null) {
				BedDefense.Cell cell = bed.report().weakest();
				WorldLines.box(new AxisAlignedBB(cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + 1, cell.z() + 1)
						.expand(0.02, 0.02, 0.02), WEAK_SPOT, WIDTH);
			}
		} finally {
			WorldLines.end();
		}
	}

	private static void drawTnt(Hazards.Tnt tnt) {
		int colour = tnt.knock() != null ? TNT_HITS : TNT_SAFE;
		List<Vec> path = tnt.path();
		for (int i = 1; i < path.size(); i++) {
			WorldLines.line(Flights.vec3(path.get(i - 1)), Flights.vec3(path.get(i)), colour, WIDTH);
		}

		Vec3 centre = Flights.vec3(tnt.center());
		WorldLines.box(new AxisAlignedBB(centre.xCoord, centre.yCoord, centre.zCoord, centre.xCoord, centre.yCoord,
				centre.zCoord).expand(0.2, 0.2, 0.2), colour, WIDTH);
		// Where the push ends, and where it is still half its strength.
		WorldLines.circle(centre, (float) tnt.reach(), colour, WIDTH);
		WorldLines.circle(centre, (float) tnt.reach() / 2, colour, WIDTH);
	}
}
