package de.raindancer118.hypixelscout.startup;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Registered on {@code MinecraftForge.EVENT_BUS} only when {@code -Dhypixelscout.startupTest=true}
 * is set (see {@code HypixelScout.onInit}) — the {@code runClientStartupTest} Gradle task sets it,
 * a normal dev/production client never does.
 */
public final class StartupTestListener {

	private final StartupTestRunner runner;

	public StartupTestListener() {
		this.runner = new StartupTestRunner(StartupTestRunner.gameDir(), new MainMenuScreenshotCheck());
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			runner.tick();
		}
	}
}
