package de.raindancer118.hypixelscout.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.raindancer118.hypixelscout.HypixelScout;

/**
 * Puts a settings button next to the mod in Mod Menu.
 *
 * <p>A soft dependency: compiled against, never shipped, and only loaded when Mod Menu itself asks
 * for this entrypoint. Without Mod Menu installed the class is never touched.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> HypixelScout.get().settingsScreen(parent);
	}
}
