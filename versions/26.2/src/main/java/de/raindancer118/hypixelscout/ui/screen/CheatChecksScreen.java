package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.cheatwatch.Check;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import net.minecraft.client.Minecraft;
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
 * Every cheat option in one scrolling screen: each check on its own switch with its own sensitivity,
 * the limits the checks measure against, and the suspects card on the HUD. A check switched off
 * stops looking at once, and whatever it had seen this round is forgotten with its flags.
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

		section(rows, "message.hypixelscout.settings.cheats.checks.section");
		for (Check check : Check.values()) {
			String key = "message.hypixelscout.cheat.check." + check.name().toLowerCase(Locale.ROOT);
			rows.addChild(CycleButton.onOffBuilder(cheats.isOn(check))
					.withTooltip(value -> Tooltip.create(Component.translatable(key + ".tooltip")))
					.create(0, 0, BUTTON_WIDTH, 20, Component.literal(check.label()),
							(button, value) -> cheats.set(check, value)));
			SettingSlider sensitivity = new SettingSlider(0, 0, BUTTON_WIDTH, "message.hypixelscout.settings.cheats.sensitivity",
					ScoutSettings.MIN_CHEAT_SENSITIVITY, ScoutSettings.MAX_CHEAT_SENSITIVITY, cheats.sensitivityOf(check),
					value -> cheats.setSensitivity(check, (int) Math.round(value / 5) * 5),
					value -> Math.round(value / 5) * 5 + "%");
			sensitivity.setTooltip(Tooltip.create(Component.translatable("message.hypixelscout.settings.cheats.check_sensitivity.tooltip",
					check.label())));
			rows.addChild(sensitivity);
		}

		section(rows, "message.hypixelscout.settings.cheats.limits");
		rows.addChild(limit("reach_standing", 3.0, 4.5, cheats.reachStanding, value -> cheats.reachStanding = round(value, 0.05), "%.2f"));
		rows.addChild(limit("reach_moving", 3.0, 5.0, cheats.reachMoving, value -> cheats.reachMoving = round(value, 0.05), "%.2f"));
		rows.addChild(limit("speed", 8.0, 30.0, cheats.speedPerSecond, value -> cheats.speedPerSecond = round(value, 0.1), "%.1f"));
		rows.addChild(limit("fastplace", 8, 30, cheats.fastPlacePerSecond, value -> cheats.fastPlacePerSecond = (int) Math.round(value), "%.0f"));
		rows.addChild(limit("bridge", 3.0, 10.0, cheats.bridgePerSecond, value -> cheats.bridgePerSecond = round(value, 0.1), "%.1f"));
		rows.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.cheats.limits.reset"), button -> {
			cheats.reachStanding = 3.2;
			cheats.reachMoving = 3.8;
			cheats.speedPerSecond = 12.4;
			cheats.fastPlacePerSecond = 13;
			cheats.bridgePerSecond = 5.0;
			cheats.checkSensitivity.clear();
			rebuildWidgets();
		}).width(BUTTON_WIDTH).build());

		section(rows, "message.hypixelscout.settings.cheats.hud");
		ScoutSettings.Hud hud = cheats.hud;
		rows.addChild(toggle("hud.enabled", hud.enabled, value -> hud.enabled = value));
		rows.addChild(toggle("hud.only_flagged", hud.onlyFlagged, value -> hud.onlyFlagged = value));
		rows.addChild(new SettingSlider(0, 0, BUTTON_WIDTH, "message.hypixelscout.settings.cheats.hud.min_percent", 1, 99,
				hud.minPercent, value -> hud.minPercent = (int) Math.round(value), value -> Math.round(value) + "%"));
		rows.addChild(new SettingSlider(0, 0, BUTTON_WIDTH, "message.hypixelscout.settings.cheats.hud.rows", 1, 12,
				hud.maxRows, value -> hud.maxRows = (int) Math.round(value), value -> String.valueOf(Math.round(value))));
		rows.addChild(toggle("hud.show_checks", hud.showChecks, value -> hud.showChecks = value));
		rows.addChild(Button.builder(Component.translatable("message.hypixelscout.suspects.move"),
				button -> minecraft.gui.setScreen(new SuspectsEditorScreen(mod::settings, mod.cheats(), mod::saveSettings, this)))
				.width(BUTTON_WIDTH).build());

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

	private static void section(GridLayout.RowHelper rows, String key) {
		rows.addChild(new StringWidget(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GOLD), Minecraft.getInstance().font),
				2, rows.newCellSettings().alignHorizontallyCenter().paddingTop(6));
	}

	private CycleButton<Boolean> toggle(String key, boolean initial, java.util.function.Consumer<Boolean> set) {
		String full = "message.hypixelscout.settings.cheats." + key;
		return CycleButton.onOffBuilder(initial)
				.withTooltip(value -> Tooltip.create(Component.translatable(full + ".tooltip")))
				.create(0, 0, BUTTON_WIDTH, 20, Component.translatable(full), (button, value) -> set.accept(value));
	}

	private static SettingSlider limit(String key, double min, double max, double initial,
			java.util.function.DoubleConsumer apply, String format) {
		String full = "message.hypixelscout.settings.cheats.limit." + key;
		SettingSlider slider = new SettingSlider(0, 0, BUTTON_WIDTH, full, min, max, initial, apply,
				value -> String.format(Locale.ROOT, format, value));
		slider.setTooltip(Tooltip.create(Component.translatable(full + ".tooltip")));
		return slider;
	}

	private static double round(double value, double step) {
		return Math.round(value / step) * step;
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
