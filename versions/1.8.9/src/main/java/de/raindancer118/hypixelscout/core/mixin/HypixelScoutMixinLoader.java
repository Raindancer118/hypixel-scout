package de.raindancer118.hypixelscout.core.mixin;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Legacy Forge (1.8.9) predates Forge's own Mixin support by several major versions, so this mod
 * carries its own copy of Mixin and bootstraps it itself.
 *
 * <p>This has to be a coremod (declared via the {@code FMLCorePlugin} manifest attribute, wired up
 * in {@code build.gradle}'s {@code jar.manifest}) rather than plain {@code @Mod} setup code,
 * because coremods are instantiated by launchwrapper before FML even starts scanning for
 * {@code @Mod} classes — by the time a normal mod's {@code FMLPreInitializationEvent} fires, the
 * classes a mixin config targets have long since been loaded (and, without Mixin registered
 * first, loaded unmodified).
 */
@IFMLLoadingPlugin.Name("HypixelScoutMixinLoader")
@IFMLLoadingPlugin.MCVersion("1.8.9")
public final class HypixelScoutMixinLoader implements IFMLLoadingPlugin {

	public HypixelScoutMixinLoader() {
		MixinBootstrap.init();
		Mixins.addConfiguration("hypixelscout.mixins.json");
		// Scout's, shaded in with its bundle, which has no coremod of its own.
		Mixins.addConfiguration("scout.mixins.json");
	}

	@Override
	public String[] getASMTransformerClass() {
		// Mixin's own tweaker (wired via the TweakClass manifest attribute) registers the
		// transformer that actually applies mixins; this coremod only needs to load the config.
		return new String[0];
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
		// Nothing needed from FML's environment data.
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
