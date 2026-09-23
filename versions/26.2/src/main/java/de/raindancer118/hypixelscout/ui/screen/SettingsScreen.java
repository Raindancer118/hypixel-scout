package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.ScoutCommands;
import de.raindancer118.hypixelscout.config.Accent;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.ChatPacing;
import de.raindancer118.hypixelscout.core.RequeueMode;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.ui.widget.KeyBindButton;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every setting, in four tabs of vanilla widgets.
 *
 * <p>The JSON file stays the source of truth and stays hand-editable; this is the front for it, so
 * nobody has to leave the game to turn the nametags on. Changes apply at once and are written when
 * the screen closes.
 */
public final class SettingsScreen extends Screen {
	private static final int FOOTER = 33;
	private static final int WIDE = 310;
	private static final int NARROW = 150;

	/** Hypixel's developer dashboard, where a player creates the key this mod reads with. */
	private static final String KEY_PAGE = "https://developer.hypixel.net/dashboard";

	private final HypixelScout mod;
	private final Screen parent;
	private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);

	private MenuTabBar tabBar;
	private int selected;

	private EditBox keyBox;
	private MultiLineTextWidget keyStatus;
	/** What the status line says; kept across a re-layout, which rebuilds the widget. */
	private Component keyMessage;

	public SettingsScreen(HypixelScout mod, Screen parent) {
		super(Component.translatable("message.hypixelscout.settings.title"));
		this.mod = mod;
		this.parent = parent;
	}

	private ScoutSettings settings() {
		return mod.settings();
	}

	/** For commands and the empty game tab, which want the key field first. */
	public SettingsScreen onTab(int index) {
		selected = index;
		return this;
	}

	@Override
	protected void init() {
		if (keyMessage == null) {
			keyMessage = Component.translatable(mod.client().hasApiKey()
					? "message.hypixelscout.key.stored" : "message.hypixelscout.key.none");
		}

		tabBar = MenuTabBar.builder(tabManager, width)
				.addTabs(new GeneralTab(), new TableTab(), new OverlaysTab(), new AlertsTab(), new KeysTab())
				.build();
		addRenderableWidget(tabBar);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - FOOTER + 7, 200, 20).build());

		tabBar.selectTab(selected, false);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (tabBar == null) {
			return;
		}

		tabBar.arrangeElements(width);
		int top = tabBar.getRectangle().bottom();
		tabManager.setTabArea(new ScreenRectangle(0, top, width, height - FOOTER - top));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		g.blit(RenderPipelines.GUI_TEXTURED, Screen.INWORLD_FOOTER_SEPARATOR, 0, height - FOOTER - 2, 0.0f, 0.0f,
				width, 2, 32, 2);
	}

	// --- widgets --------------------------------------------------------------------------------

	private CycleButton<Boolean> toggle(String key, boolean initial, Consumer<Boolean> set) {
		return CycleButton.onOffBuilder(initial)
				.withTooltip(value -> Tooltip.create(Component.translatable("message.hypixelscout.settings." + key + ".tooltip")))
				.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings." + key),
						(button, value) -> set.accept(value));
	}

	private static <E extends Enum<E>> Component enumLabel(String group, E value) {
		return Component.translatable("message.hypixelscout." + group + "." + value.name().toLowerCase(Locale.ROOT));
	}

	private abstract static class SettingsTab extends GridLayoutTab {
		protected final GridLayout.RowHelper rows;

		SettingsTab(String key) {
			super(Component.translatable("message.hypixelscout.settings.tab." + key));
			layout.rowSpacing(4).columnSpacing(10);
			rows = layout.createRowHelper(2);
		}
	}

	private final class GeneralTab extends SettingsTab {
		GeneralTab() {
			super("general");

			keyBox = new EditBox(font, 0, 0, WIDE, 20, Component.translatable("message.hypixelscout.settings.key"));
			keyBox.setMaxLength(36);
			keyBox.setHint(Component.translatable("message.hypixelscout.settings.key.hint"));
			keyBox.setValue(settings().apiKey);
			// Only the start of the key is readable while the field is not being edited: this is a
			// screen people open on stream.
			keyBox.addFormatter((text, offset) -> keyBox.isFocused() ? FormattedCharSequence.forward(text, Style.EMPTY)
					: FormattedCharSequence.forward(mask(text, offset), Style.EMPTY));
			keyBox.setTooltip(Tooltip.create(Component.translatable("message.hypixelscout.settings.key.tooltip")));
			rows.addChild(keyBox, 2);

			rows.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.key.save"),
					button -> saveKey()).width(NARROW).build());
			rows.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.key.test"),
					button -> testKey()).width(NARROW).build());

			keyStatus = new MultiLineTextWidget(keyMessage, font).setMaxWidth(WIDE).setCentered(true);
			rows.addChild(keyStatus, 2, rows.newCellSettings().alignHorizontallyCenter());

			rows.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.key.get"),
							ConfirmLinkScreen.confirmLink(SettingsScreen.this, KEY_PAGE))
					.tooltip(Tooltip.create(Component.literal(KEY_PAGE)))
					.width(NARROW).build());

			rows.addChild(CycleButton.builder((Accent value) -> Component.translatable(
									"message.hypixelscout.accent." + value.name().toLowerCase(Locale.ROOT))
							.withColor(value.argb() & 0xFFFFFF), settings().accent)
					.withValues(Accent.values())
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.accent"),
							(button, value) -> settings().accent = value));

			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.cache", 1,
					ScoutSettings.MAX_CACHE_MINUTES, settings().cacheMinutes,
					value -> settings().cacheMinutes = (int) Math.round(value),
					value -> String.valueOf(Math.round(value))));

			rows.addChild(toggle("hypixel_only", settings().queue.onlyOnHypixel,
					value -> settings().queue.onlyOnHypixel = value));

			rows.addChild(toggle("lobby", settings().lookUpInLobby, value -> settings().lookUpInLobby = value));
			rows.addChild(CycleButton.builder((ThreatScale.Basis value) -> enumLabel("threat_basis", value),
							settings().threatBasis)
					.withValues(ThreatScale.Basis.values())
					.withTooltip(value -> Tooltip.create(Component.translatable("message.hypixelscout.threat_basis."
							+ value.name().toLowerCase(Locale.ROOT) + ".tooltip")))
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.threat_basis"),
							(button, value) -> settings().threatBasis = value));

			SettingSlider sensitivity = new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.threat_sensitivity",
					ScoutSettings.MIN_SENSITIVITY, ScoutSettings.MAX_SENSITIVITY, settings().threatSensitivity,
					value -> settings().threatSensitivity = sensitivityStep(value),
					value -> sensitivityStep(value) + "%");
			sensitivity.setTooltip(Tooltip.create(
					Component.translatable("message.hypixelscout.settings.threat_sensitivity.tooltip")));
			rows.addChild(sensitivity);

			rows.addChild(CycleButton.builder((Threat value) -> Component.literal(value.colour() + value.label()),
							settings().threatReportFrom)
					.withValues(Threat.rated())
					.withTooltip(value -> Tooltip.create(
							Component.translatable("message.hypixelscout.settings.report_from.tooltip")))
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.report_from"),
							(button, value) -> settings().threatReportFrom = value));
		}
	}

	/** Steps of five percent: finer than anybody can tell apart in a game, coarse enough to hit 100 again. */
	private static int sensitivityStep(double value) {
		return (int) Math.round(value / 5.0) * 5;
	}

	private final class TableTab extends SettingsTab {
		TableTab() {
			super("table");

			rows.addChild(CycleButton.builder((HudMode value) -> enumLabel("mode", value), settings().table.mode)
					.withValues(HudMode.values())
					.withTooltip(value -> Tooltip.create(Component.translatable("message.hypixelscout.mode."
							+ value.name().toLowerCase(Locale.ROOT) + ".tooltip")))
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.mode"),
							(button, value) -> settings().table.mode = value));

			rows.addChild(CycleButton.builder((SortMode value) -> enumLabel("sort", value), settings().table.sort)
					.withValues(SortMode.values())
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.sort"),
							(button, value) -> settings().table.sort = value));

			rows.addChild(toggle("group", settings().table.groupByTeam, value -> settings().table.groupByTeam = value));
			rows.addChild(toggle("hide_own", settings().table.hideOwnTeam, value -> settings().table.hideOwnTeam = value));
			rows.addChild(toggle("wlr", settings().table.showWlr, value -> settings().table.showWlr = value));
			rows.addChild(toggle("winstreak", settings().table.showWinstreak,
					value -> settings().table.showWinstreak = value));
			rows.addChild(toggle("beds_per_game", settings().table.showBedsPerGame,
					value -> settings().table.showBedsPerGame = value));
			rows.addChild(toggle("kills_per_game", settings().table.showKillsPerGame,
					value -> settings().table.showKillsPerGame = value));
			rows.addChild(toggle("beds", settings().table.showBeds, value -> settings().table.showBeds = value));
			rows.addChild(toggle("age", settings().table.showAccountAge, value -> settings().table.showAccountAge = value));

			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.rows", 1,
					ScoutSettings.MAX_ROWS, settings().table.maxRows,
					value -> settings().table.maxRows = (int) Math.round(value),
					value -> String.valueOf(Math.round(value))));

			rows.addChild(Button.builder(Component.translatable("message.hypixelscout.move_table"),
					button -> minecraft.gui.setScreen(mod.tableEditor(SettingsScreen.this))).width(NARROW).build());
		}
	}

	private final class OverlaysTab extends SettingsTab {
		OverlaysTab() {
			super("overlays");

			rows.addChild(toggle("tooltip", settings().tooltip.enabled, value -> settings().tooltip.enabled = value));
			rows.addChild(toggle("walls", settings().tooltip.throughWalls,
					value -> settings().tooltip.throughWalls = value));

			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.angle",
					ScoutSettings.MIN_ANGLE, ScoutSettings.MAX_ANGLE, settings().tooltip.angle,
					value -> settings().tooltip.angle = Math.round(value * 2) / 2.0,
					value -> String.format(Locale.ROOT, "%.1f°", Math.round(value * 2) / 2.0)));
			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.offset", -100, 100,
					settings().tooltip.offsetY, value -> settings().tooltip.offsetY = (int) Math.round(value),
					value -> String.valueOf(Math.round(value))));

			rows.addChild(toggle("tab", settings().tab.enabled, value -> settings().tab.enabled = value));
			rows.addChild(toggle("nametag_stars", settings().nametag.stars, value -> settings().nametag.stars = value));
			rows.addChild(toggle("nametag_fkdr", settings().nametag.fkdr, value -> settings().nametag.fkdr = value));

			rows.addChild(toggle("proximity", settings().proximity.enabled,
					value -> settings().proximity.enabled = value));
			rows.addChild(CycleButton.builder((Threat value) -> Component.literal(value.colour() + value.label()),
							settings().proximity.from)
					.withValues(Threat.rated())
					.withTooltip(value -> Tooltip.create(
							Component.translatable("message.hypixelscout.settings.proximity_from.tooltip")))
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.proximity_from"),
							(button, value) -> settings().proximity.from = value));
			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.proximity_radius", 2,
					ScoutSettings.MAX_RADIUS, settings().proximity.radius,
					value -> settings().proximity.radius = (int) Math.round(value),
					value -> String.valueOf(Math.round(value))));
			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.proximity_seconds", 1,
					ScoutSettings.MAX_POPUP_SECONDS, settings().proximity.seconds,
					value -> settings().proximity.seconds = (int) Math.round(value),
					value -> Math.round(value) + " s"));
		}
	}

	/** Index of the keys tab, for whoever wants to open the screen right there. */
	public static final int KEYS_TAB = 4;

	/**
	 * The bindings that matter mid-game, changeable without leaving for vanilla's controls — which
	 * the button at the bottom still opens, for the queue slots and the toggles.
	 */
	private final class KeysTab extends SettingsTab {
		KeysTab() {
			super("keys");
			keyButtons.clear();

			for (KeyMapping mapping : mod.keys().settingsMappings()) {
				String name = mapping.getName().substring("key.hypixelscout.".length());
				keyButtons.add(rows.addChild(new KeyBindButton(NARROW, mapping,
						Component.translatable("message.hypixelscout.settings.keys." + name),
						SettingsScreen.this::listenWith)));
			}

			rows.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.keys.reset"),
					button -> {
						for (KeyMapping mapping : mod.keys().settingsMappings()) {
							mapping.setKey(mapping.getDefaultKey());
						}
						KeyMapping.resetMapping();
						minecraft.options.save();
						keyButtons.forEach(KeyBindButton::refresh);
					}).width(NARROW).build());
			rows.addChild(Button.builder(Component.translatable("message.hypixelscout.settings.keys.all"),
					button -> minecraft.gui.setScreen(new KeyBindsScreen(SettingsScreen.this, minecraft.options)))
					.width(NARROW).build());
		}
	}

	private final java.util.List<KeyBindButton> keyButtons = new java.util.ArrayList<>();
	private KeyBindButton listening;

	private void listenWith(KeyBindButton button) {
		if (listening != null && listening != button) {
			listening.stopListening();
		}
		listening = button;
	}

	/** While a binding listens, the next key goes to it — Escape included, which then unbinds. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening != null) {
			listening.bind(InputConstants.getKey(event));
			listening = null;
			keyButtons.forEach(KeyBindButton::refresh);
			return true;
		}

		return super.keyPressed(event);
	}

	/** A mouse button can be bound too; side buttons are where a lot of people want this. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (listening != null) {
			listening.bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
			listening = null;
			keyButtons.forEach(KeyBindButton::refresh);
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	private final class AlertsTab extends SettingsTab {
		AlertsTab() {
			super("alerts");

			rows.addChild(toggle("chat_hover", settings().alerts.chatHover, value -> settings().alerts.chatHover = value));
			rows.addChild(toggle("nick_alert", settings().alerts.nickAlert, value -> settings().alerts.nickAlert = value));
			rows.addChild(toggle("streak_alert", settings().alerts.streakAlert,
					value -> settings().alerts.streakAlert = value));
			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.streak", 5, 200,
					settings().alerts.streakThreshold,
					value -> settings().alerts.streakThreshold = (int) Math.round(value),
					value -> String.valueOf(Math.round(value))));

			// In tenths of a second on the label; stored in ticks, one tick being a twentieth.
			SettingSlider interval = new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.report_interval",
					ChatPacing.MIN_TICKS, ChatPacing.MAX_TICKS, settings().reportIntervalTicks,
					value -> settings().reportIntervalTicks = intervalStep(value),
					value -> String.format(Locale.ROOT, "%.1f s", intervalStep(value) / 20.0));
			interval.setTooltip(Tooltip.create(
					Component.translatable("message.hypixelscout.settings.report_interval.tooltip")));
			rows.addChild(interval);

			rows.addChild(CycleButton.builder((RequeueMode value) -> enumLabel("requeue", value), settings().requeue.mode)
					.withValues(RequeueMode.values())
					.withTooltip(value -> Tooltip.create(Component.translatable("message.hypixelscout.requeue."
							+ value.name().toLowerCase(Locale.ROOT) + ".tooltip")))
					.create(0, 0, NARROW, 20, Component.translatable("message.hypixelscout.settings.requeue"),
							(button, value) -> settings().requeue.mode = value));
			rows.addChild(new SettingSlider(0, 0, NARROW, "message.hypixelscout.settings.requeue_delay", 0,
					ScoutSettings.MAX_REQUEUE_DELAY, settings().requeue.delaySeconds,
					value -> settings().requeue.delaySeconds = (int) Math.round(value),
					value -> Math.round(value) + " s"));
		}
	}

	/** Whole tenths of a second, i.e. even tick counts. */
	private static int intervalStep(double ticks) {
		return (int) Math.round(ticks / 2.0) * 2;
	}

	// --- the key --------------------------------------------------------------------------------

	/** Keeps the first block of the key readable and hides the rest. */
	static String mask(String visible, int offset) {
		char[] chars = visible.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (offset + i >= 8 && chars[i] != '-') {
				chars[i] = '•';
			}
		}
		return new String(chars);
	}

	private boolean saveKey() {
		String typed = keyBox.getValue().trim();

		if (typed.isEmpty()) {
			mod.setApiKey("");
			status(Component.translatable("message.hypixelscout.key.none"));
			return true;
		}

		try {
			UUID.fromString(typed);
		} catch (IllegalArgumentException e) {
			status(Component.translatable("message.hypixelscout.key.malformed").withColor(0xFF5555));
			return false;
		}

		if (!typed.equals(settings().apiKey)) {
			mod.setApiKey(typed);
		}
		status(Component.translatable("message.hypixelscout.key.saved"));
		return true;
	}

	private void testKey() {
		if (!saveKey()) {
			return;
		}

		status(Component.translatable("message.hypixelscout.key.checking"));
		mod.checkKey(result -> status(ScoutCommands.describe(result)
				.withColor(result.outcome() == KeyCheck.Outcome.OK ? 0x55FF55 : 0xFF7070)));
	}

	private void status(Component message) {
		keyMessage = message;
		if (keyStatus != null) {
			keyStatus.setMessage(message);
			repositionElements();
		}
	}

	/** For the client game test, which drives the key field like a player would. */
	public void typeKeyForTest(String key) {
		keyBox.setValue(key);
		saveKey();
	}

	public Component keyMessage() {
		return keyMessage;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		// A key that was typed but not saved still counts, as long as it is one.
		String typed = keyBox == null ? settings().apiKey : keyBox.getValue().trim();
		if (!typed.equals(settings().apiKey) && (typed.isEmpty() || isUuid(typed))) {
			mod.setApiKey(typed);
		} else {
			mod.saveSettings();
		}

		minecraft.gui.setScreen(parent);
	}

	private static boolean isUuid(String value) {
		try {
			UUID.fromString(value);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

}
