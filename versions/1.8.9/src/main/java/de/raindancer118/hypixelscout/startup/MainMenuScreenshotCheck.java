package de.raindancer118.hypixelscout.startup;

import java.io.File;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;

/**
 * Waits for the vanilla main menu to be the current screen, then takes exactly the screenshot the
 * F2 key binding would (same helper, same target directory), and is done.
 *
 * <p>Waiting for {@link GuiMainMenu} rather than "N ticks after launch" is the point: it is the
 * first screen that only exists once the client has actually finished initialising rendering,
 * resources and the mod list, so a screenshot of it is real proof the game came up — a fixed
 * tick count would just as happily "pass" while stuck on the resource-pack-loading darkness.
 */
public final class MainMenuScreenshotCheck implements StartupCheck {

	@Override
	public String name() {
		return "main-menu-screenshot";
	}

	@Override
	public boolean tick() throws IOException {
		Minecraft mc = Minecraft.getMinecraft();
		if (!(mc.currentScreen instanceof GuiMainMenu)) {
			return false;
		}

		// saveScreenshot's own File argument is the game directory, not the screenshots folder —
		// it appends "screenshots" itself. Passing mc.mcDataDir/screenshots here silently wrote
		// nothing (a caught, merely logged IOException) into a screenshots/screenshots/ path that
		// never existed, which is exactly the kind of "green but did nothing" failure worth
		// double-checking below rather than trusting the vanilla helper's own error handling.
		ScreenShotHelper.saveScreenshot(mc.mcDataDir, mc.displayWidth, mc.displayHeight,
				mc.getFramebuffer());

		File screenshotsDir = new File(mc.mcDataDir, "screenshots");
		File[] shots = screenshotsDir.listFiles((dir, name) -> name.endsWith(".png"));
		if (shots == null || shots.length == 0) {
			throw new IOException("no screenshot appeared in " + screenshotsDir);
		}
		return true;
	}
}
