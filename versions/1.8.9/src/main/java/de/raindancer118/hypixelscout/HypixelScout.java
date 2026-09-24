package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.startup.StartupTestListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Build skeleton for the Forge 1.8.9 port of Hypixel Scout (see {@code Project.md}). This is
 * deliberately minimal — a real port of the 26.2 mod's features comes later — and exists to prove
 * three things end to end before any of that is written:
 *
 * <ul>
 *   <li>common's classes are actually reachable from here (shaded into the jar, not just on the
 *       compile classpath) — proven by referencing {@link StatFormat#star(int)} below;</li>
 *   <li>the Mixin bootstrap (coremod → MixinBootstrap → launchwrapper tweaker →
 *       {@code MixinGuiMainMenu}) actually applies in the dev client;</li>
 *   <li>the dev client boots headlessly to the main menu at all — the
 *       {@code runClientStartupTest} Gradle task.</li>
 * </ul>
 */
@Mod(modid = HypixelScout.MOD_ID, name = "Hypixel Scout", version = HypixelScout.VERSION,
		clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]",
		dependencies = "required-after:hypixel_mod_api")
public final class HypixelScout {

	public static final String MOD_ID = "hypixelscout";
	/**
	 * Forge wants this as a compile-time constant, so it cannot be read from a resource at
	 * runtime. {@link HypixelScoutVersion#VERSION} is itself a {@code static final String}
	 * initialised from {@code project.version} by the {@code generateVersion} Gradle task, which
	 * Java's constant-folding treats as a constant expression too.
	 */
	public static final String VERSION = HypixelScoutVersion.VERSION;

	private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	@Mod.EventHandler
	public void onInit(FMLInitializationEvent event) {
		LOGGER.info("Hypixel Scout {} loaded on Forge 1.8.9 (common: {} star={})", VERSION,
				StatFormat.class.getName(), StatFormat.star(1));

		if (Boolean.getBoolean("hypixelscout.startupTest")) {
			LOGGER.info("hypixelscout.startupTest=true — registering the startup test listener");
			MinecraftForge.EVENT_BUS.register(new StartupTestListener());
		}
	}
}
