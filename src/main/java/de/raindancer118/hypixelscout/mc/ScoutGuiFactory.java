package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.common.config.ConfigElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The settings screen under Mods, so every option can be changed without leaving the game or
 * opening a text file.
 *
 * <p>Built from the config's own categories, which means a setting added to {@link ScoutConfig}
 * appears here by itself rather than having to be listed twice.
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

	/** Forge constructs this itself, so it needs the parent-screen constructor and nothing else. */
	public static final class ScoutConfigScreen extends GuiConfig {
		public ScoutConfigScreen(GuiScreen parent) {
			super(parent, elements(), HypixelScout.MOD_ID, false, false,
					"Hypixel Scout");
		}

		private static List<IConfigElement> elements() {
			ScoutConfig config = HypixelScout.instance.getConfig();
			List<IConfigElement> categories = new ArrayList<IConfigElement>();

			for (String category : new String[] {
					ScoutConfig.CATEGORY_GENERAL, ScoutConfig.CATEGORY_TABLE,
					ScoutConfig.CATEGORY_TAB, ScoutConfig.CATEGORY_NAMETAG,
					ScoutConfig.CATEGORY_TOOLTIP, ScoutConfig.CATEGORY_ALERTS,
					ScoutConfig.CATEGORY_QUEUE, ScoutConfig.CATEGORY_ADVANCED}) {
				categories.add(new ConfigElement(
						config.getConfiguration().getCategory(category)));
			}

			return categories;
		}
	}
}
