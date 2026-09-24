package de.raindancer118.hypixelscout.mixin;

import net.minecraft.client.gui.GuiMainMenu;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one trivial mixin that proves the whole bootstrap chain (coremod → MixinBootstrap →
 * launchwrapper tweaker → this class) actually applies in the dev client. It does nothing besides
 * log a line that {@code runClientStartupTest} (and a human reading the log) can look for.
 */
@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu {

	private static final Logger HYPIXELSCOUT_LOGGER = LogManager.getLogger("HypixelScout/Mixin");

	@Inject(method = "initGui", at = @At("HEAD"))
	private void hypixelscout$onInitGui(CallbackInfo ci) {
		HYPIXELSCOUT_LOGGER.info("[HypixelScout] mixin bootstrap OK — MixinGuiMainMenu.initGui reached");
	}
}
