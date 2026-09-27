package de.raindancer118.hypixelscout.startup;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Registered on {@code MinecraftForge.EVENT_BUS} only when {@code -Dhypixelscout.startupTest=true}
 * is set (see {@code HypixelScout.onInit}) — the {@code runClientStartupTest} Gradle task sets it,
 * a normal dev/production client never does.
 *
 * <p>Phase 2a of this branch's port (see {@code Project.md}, the HUD) extends the chain past the
 * main menu: {@link WorldEntryCheck} enters a flat creative singleplayer world (needed for the HUD
 * to have anything to render onto), {@link HudSetupCheck} stages a fake Bedwars round in it — fake
 * enemies, scoreboard teams, a stubbed Hypixel/Mojang API — and {@link HudScreenshotCheck}
 * screenshots the table, the tab list, the look tooltip, the proximity popup and the peek overlay.
 * The trailing {@code MainMenuScreenshotCheck} Phase 1 used to prove nothing crashed since the
 * first one is gone: once a world is entered there is no going back to the main menu to screenshot.
 */
public final class StartupTestListener {

	private final StartupTestRunner runner;

	public StartupTestListener() {
		HudSetupCheck hudSetup = new HudSetupCheck();
		this.runner = new StartupTestRunner(StartupTestRunner.gameDir(), new MainMenuScreenshotCheck(),
				new ScoutFeatureCheck(), new WorldEntryCheck(), hudSetup, new HudScreenshotCheck(hudSetup),
				new WorldAndCheatsCheck(), new ScreensCheck(), new LeavingCheck());
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			runner.tick();
		}
	}
}
