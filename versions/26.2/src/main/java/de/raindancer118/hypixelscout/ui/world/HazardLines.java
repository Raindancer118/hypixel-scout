package de.raindancer118.hypixelscout.ui.world;

import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.flight.Fall;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.game.Hazards;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The world half of {@link Hazards}, as vanilla gizmos like the flight paths: primed TNT's fall and
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
		LevelExtractionEvents.END_EXTRACTION.register(this::extract);
	}

	private void extract(LevelExtractionContext context) {
		if (!hazards.active(Minecraft.getInstance())) {
			return;
		}

		try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
			for (Hazards.Tnt tnt : hazards.tnt()) {
				drawTnt(tnt);
			}

			Fall fall = hazards.fall();
			if (fall != null && fall.landing() != null) {
				Vec3 at = Flights.vec3(fall.landing()).add(0, 0.02, 0);
				Gizmos.circle(at, 0.45f, GizmoStyle.strokeAndFill(LANDING, WIDTH, 0x406ADF8A));
			}

			Hazards.Bed bed = hazards.bed();
			if (bed != null && bed.report().weakest() != null) {
				BedDefense.Cell cell = bed.report().weakest();
				Gizmos.cuboid(new BlockPos(cell.x(), cell.y(), cell.z()), 0.02f, GizmoStyle.stroke(WEAK_SPOT, WIDTH));
			}
		}
	}

	private static void drawTnt(Hazards.Tnt tnt) {
		int colour = tnt.knock() != null ? TNT_HITS : TNT_SAFE;
		List<Vec> path = tnt.path();
		for (int i = 1; i < path.size(); i++) {
			Gizmos.line(Flights.vec3(path.get(i - 1)), Flights.vec3(path.get(i)), colour, WIDTH);
		}

		Vec3 centre = Flights.vec3(tnt.center());
		Gizmos.cuboid(new AABB(centre, centre).inflate(0.2), GizmoStyle.strokeAndFill(colour, WIDTH, (colour & 0xFFFFFF) | 0x50000000));
		// Where the push ends, and where it is still half its strength.
		Gizmos.circle(centre, (float) tnt.reach(), GizmoStyle.strokeAndFill(colour, WIDTH, (colour & 0xFFFFFF) | 0x18000000));
		Gizmos.circle(centre, (float) tnt.reach() / 2, GizmoStyle.stroke(colour, WIDTH));
	}
}
