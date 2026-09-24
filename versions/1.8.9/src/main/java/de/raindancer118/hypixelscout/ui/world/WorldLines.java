package de.raindancer118.hypixelscout.ui.world;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * The GL line drawing every world overlay in this module shares: 26.2 hands its paths, markers and
 * rings to vanilla's own per-frame "gizmo" collector; Forge 1.8.9 has nothing of the kind, so this
 * draws them by hand with a {@link Tessellator}, the same way mods drawing world-space debug lines
 * on this version always have (WorldEditCUI, VoxelMap's waypoint beams, …): in
 * {@code RenderWorldLastEvent}, after moving the GL origin from the camera back to the world by the
 * negative of the render view entity's interpolated position — vanilla's own world render already
 * did that translate and popped it back off before this event fires.
 *
 * <p>One simplification against 26.2's gizmos: translucent fills (a circle's disc, a marker's face)
 * are left out — only the stroke is drawn. Vanilla's fixed-function pipeline can do a translucent
 * quad here too, but the extra blend-state juggling bought nothing a player would notice for a
 * one-tick-refreshed overlay, and every one of these shapes already carries its meaning in the
 * stroke alone.
 */
final class WorldLines {
	private WorldLines() {
	}

	/** Moves the GL origin from the camera to the world; call once per {@code RenderWorldLastEvent}. */
	static void begin(double camX, double camY, double camZ) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(-camX, -camY, -camZ);
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.depthMask(false);
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GL11.glDisable(GL11.GL_CULL_FACE);
	}

	static void end() {
		GL11.glEnable(GL11.GL_CULL_FACE);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.disableBlend();
		GlStateManager.enableLighting();
		GlStateManager.enableTexture2D();
		GlStateManager.popMatrix();
	}

	static void line(Vec3 from, Vec3 to, int argb, float width) {
		GL11.glLineWidth(width);
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer renderer = tessellator.getWorldRenderer();
		renderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		vertex(renderer, from, argb);
		vertex(renderer, to, argb);
		tessellator.draw();
	}

	/** A wireframe box, for a marker or a bed's weak spot. */
	static void box(AxisAlignedBB box, int argb, float width) {
		GL11.glLineWidth(width);
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer renderer = tessellator.getWorldRenderer();
		renderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		double[] xs = {box.minX, box.maxX};
		double[] ys = {box.minY, box.maxY};
		double[] zs = {box.minZ, box.maxZ};
		// The twelve edges of the box, each as its own pair of vertices (GL_LINES, not a strip).
		for (int a = 0; a < 2; a++) {
			for (int b = 0; b < 2; b++) {
				vertex(renderer, new Vec3(xs[0], ys[a], zs[b]), argb);
				vertex(renderer, new Vec3(xs[1], ys[a], zs[b]), argb);
				vertex(renderer, new Vec3(xs[a], ys[0], zs[b]), argb);
				vertex(renderer, new Vec3(xs[a], ys[1], zs[b]), argb);
				vertex(renderer, new Vec3(xs[a], ys[b], zs[0]), argb);
				vertex(renderer, new Vec3(xs[a], ys[b], zs[1]), argb);
			}
		}
		tessellator.draw();
	}

	/** A flat ring in the XZ plane, {@code segments} straight pieces around it. */
	static void circle(Vec3 centre, float radius, int argb, float width) {
		GL11.glLineWidth(width);
		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer renderer = tessellator.getWorldRenderer();
		renderer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
		int segments = 32;
		Vec3 previous = null;
		for (int i = 0; i <= segments; i++) {
			double angle = 2 * Math.PI * i / segments;
			Vec3 point = centre.addVector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
			if (previous != null) {
				vertex(renderer, previous, argb);
				vertex(renderer, point, argb);
			}
			previous = point;
		}
		tessellator.draw();
	}

	private static void vertex(WorldRenderer renderer, Vec3 at, int argb) {
		float a = ((argb >> 24) & 0xFF) / 255f;
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		renderer.pos(at.xCoord, at.yCoord, at.zCoord).color(r, g, b, a).endVertex();
	}
}
