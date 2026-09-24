package de.raindancer118.hypixelscout.startup;

import net.minecraft.client.Minecraft;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Creates a flat creative singleplayer world, programmatically — the same idea 26.2's
 * {@code HypixelScoutStartupTest} game test uses (a real client, a real world) so the HUD has
 * something to render onto and a player to attach fake enemies around.
 *
 * <p>{@link Minecraft#launchIntegratedServer} kicks the load off (world generation, the integrated
 * server starting, the loading screen); this step is done once {@code theWorld}/{@code thePlayer}
 * exist, exactly the way {@link MainMenuScreenshotCheck} waits for {@code GuiMainMenu} rather than
 * guessing a tick count.
 */
public final class WorldEntryCheck implements StartupCheck {
	/** Fresh every run: nothing here is meant to persist between {@code runClientStartupTest}s. */
	private static final String SAVE_NAME = "hypixelscout-startup-test";

	private static final int GIVE_UP_TICKS = 20 * 60;

	private boolean launched;
	private int waited;

	@Override
	public String name() {
		return "world-entry";
	}

	@Override
	public boolean tick() {
		Minecraft client = Minecraft.getMinecraft();

		if (!launched) {
			WorldSettings settings = new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false,
					WorldType.FLAT);
			settings.setWorldName(SAVE_NAME);
			// Out of the tick event into the game loop's own task queue — the place a click on
			// "Create New World" would start it from.
			client.addScheduledTask(() -> client.launchIntegratedServer(SAVE_NAME, SAVE_NAME, settings));
			launched = true;
			return false;
		}

		if (client.theWorld != null && client.thePlayer != null) {
			return true;
		}
		// Back on the main menu with no world and no connection means the join failed; say so now
		// instead of letting the watchdog find out five minutes later.
		waited++;
		if (waited > GIVE_UP_TICKS && client.getNetHandler() == null && client.getIntegratedServer() == null) {
			throw new IllegalStateException("the singleplayer world never loaded — see the client log");
		}
		return false;
	}
}
