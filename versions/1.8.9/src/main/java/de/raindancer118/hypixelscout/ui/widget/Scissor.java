package de.raindancer118.hypixelscout.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * Clips drawing to a rectangle in GUI pixels — the classic 1.8.9 scissor technique ({@code
 * GuiSlot} uses the same one internally, for the same reason: a scrolling list must not paint over
 * whatever sits above or below it). 26.2 gets this for free from {@code GuiGraphics.enableScissor};
 * here it has to go through {@link GL11} directly, converted from GUI to framebuffer pixels via
 * {@link ScaledResolution#getScaleFactor()} and flipped in Y (OpenGL's scissor origin is the
 * bottom-left corner, the GUI's is the top-left).
 */
public final class Scissor {
	private Scissor() {
	}

	public static void enable(int x, int y, int width, int height) {
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution res = new ScaledResolution(mc);
		int scale = res.getScaleFactor();

		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x * scale, mc.displayHeight - (y + height) * scale, Math.max(0, width * scale),
				Math.max(0, height * scale));
	}

	public static void disable() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
