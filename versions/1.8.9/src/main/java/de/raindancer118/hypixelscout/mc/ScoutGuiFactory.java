package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.ui.screen.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/**
 * "Mod Options" in Forge's own mod list opens the mod's real settings screen.
 *
 * <p>Ported from the {@code 1.8.9-support} branch's own {@code mc.ScoutGuiFactory} (see {@code git
 * show 1.8.9-support:.../mc/ScoutGuiFactory.java}), updated for this branch's hand-rolled {@link
 * SettingsScreen} instead of that branch's {@code GuiConfig}-generated one.
 *
 * <p>Forge's mod list constructs {@link #mainConfigGuiClass()} through reflection, calling a
 * single-argument {@code (GuiScreen parent)} constructor — {@link SettingsScreen} itself takes a
 * {@code HypixelScout} as well, so {@link ScoutConfigScreen} is a thin, single-frame stand-in: its
 * {@code initGui()} swaps itself for the real screen the instant Forge displays it, parented on
 * whatever Forge would otherwise have shown ({@code parent}, the mod list itself).
 */
public final class ScoutGuiFactory implements IModGuiFactory {
	@Override
	public void initialize(Minecraft minecraft) {
	}

	@Override
	public Class<? extends GuiScreen> mainConfigGuiClass() {
		return ScoutConfigScreen.class;
	}

	@Override
	public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
		return null;
	}

	@Override
	public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
		return null;
	}

	/** Forge constructs this itself, so it needs the single parent-screen constructor and nothing else. */
	public static final class ScoutConfigScreen extends GuiScreen {
		private final GuiScreen parent;

		public ScoutConfigScreen(GuiScreen parent) {
			this.parent = parent;
		}

		@Override
		public void initGui() {
			mc.displayGuiScreen(new SettingsScreen(HypixelScout.get(), parent));
		}

		@Override
		public boolean doesGuiPauseGame() {
			return false;
		}
	}
}
