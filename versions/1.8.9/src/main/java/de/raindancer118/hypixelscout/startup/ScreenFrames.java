package de.raindancer118.hypixelscout.startup;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Counts the frames the current screen has actually been drawn. Under xvfb the client ticks faster
 * than it renders, so a screenshot taken a fixed number of ticks after opening a screen showed the
 * previous one — and never proved the new one draws without throwing.
 */
public final class ScreenFrames {
	private static GuiScreen screen;
	private static int frames;

	/** Frames {@code gui} has been drawn since it became the drawn screen, 0 if it never was. */
	static int of(GuiScreen gui) {
		return gui != null && gui == screen ? frames : 0;
	}

	@SubscribeEvent
	public void onDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
		if (event.gui != screen) {
			screen = event.gui;
			frames = 0;
		}
		frames++;
	}
}
