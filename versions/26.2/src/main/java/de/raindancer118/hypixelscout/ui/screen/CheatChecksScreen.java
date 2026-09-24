package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.cheat.Check;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Every cheat check on its own switch, two to a row and scrolling where the screen is short. A check
 * switched off stops looking at once, and whatever it had seen this round is forgotten with its flags.
 */
public final class CheatChecksScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;

	private final HypixelScout mod;
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private ScrollableLayout body;

	public CheatChecksScreen(HypixelScout mod, Screen parent) {
		super(Component.translatable("message.hypixelscout.settings.cheats.checks.title"));
		this.mod = mod;
		this.parent = parent;
	}

	@Override
	protected void init() {
		layout.addToHeader(new StringWidget(title, font), LayoutSettings::alignHorizontallyCenter);

		ScoutSettings.Cheats cheats = mod.settings().cheats;
		GridLayout grid = new GridLayout().columnSpacing(10).rowSpacing(4);
		GridLayout.RowHelper rows = grid.createRowHelper(2);
		for (Check check : Check.values()) {
			String key = "message.hypixelscout.cheat.check." + check.name().toLowerCase(Locale.ROOT);
			rows.addChild(CycleButton.onOffBuilder(cheats.isOn(check))
					.withTooltip(value -> Tooltip.create(Component.translatable(key + ".tooltip")))
					.create(0, 0, BUTTON_WIDTH, 20, Component.literal(check.label()),
							(button, value) -> cheats.set(check, value)));
		}
		body = new ScrollableLayout(minecraft, grid, layout.getContentHeight());
		layout.addToContents(body);

		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.cheats.checks.all_on"),
				button -> {
					cheats.off.clear();
					rebuildWidgets();
				}).width(120).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(120).build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		body.arrangeElements();
		body.setMaxHeight(layout.getContentHeight());
		layout.arrangeElements();
	}

	@Override
	public void onClose() {
		mod.saveSettings();
		minecraft.gui.setScreen(parent);
	}
}
