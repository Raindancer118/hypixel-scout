package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.config.TablePlacement;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import de.raindancer118.hypixelscout.ui.widget.Clickable;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drag the table where you want it.
 *
 * <p>Ported from 26.2's {@code ui.hud.TableEditorScreen}. Outside a game the table shows a made-up
 * lobby ({@link SampleGame}), so it can be placed before there is a real one. 1.8.9 draws the
 * scaled, dragged table with a plain {@link GlStateManager}/{@link GL11} matrix push instead of
 * 26.2's {@code GuiGraphicsExtractor.pose()}.
 */
public final class TableEditorScreen extends GuiScreen {
	private static final int SNAP_THRESHOLD = 5;
	private static final int GUIDE_COLOUR = 0x55FFFFFF;
	private static final int HINT_COLOUR = 0xFFD0D0D8;
	private static final int COLUMN_WIDTH = 150;

	private final HypixelScout mod;
	private final TableHud live;
	private final TableHud sample;
	private final Roster roster;
	private final GuiScreen parent;
	private int nextId;

	private boolean dragging;
	private int dragX;
	private int dragY;
	private int dragOffsetX;
	private int dragOffsetY;

	public TableEditorScreen(HypixelScout mod, GuiScreen parent) {
		this.mod = mod;
		this.live = mod.tableHud();
		this.sample = new SampleGame().table(new java.util.function.Supplier<ScoutSettings>() {
			@Override
			public ScoutSettings get() {
				return mod.settings();
			}
		});
		this.roster = mod.roster();
		this.parent = parent;
	}

	private TableHud table() {
		return roster.isInGame() && !roster.members().isEmpty() ? live : sample;
	}

	private ScoutSettings.Table config() {
		return mod.settings().table;
	}

	private static final class Chrome {
		final boolean column;
		final int x;
		final int y;
		final int width;

		Chrome(boolean column, int x, int y, int width) {
			this.column = column;
			this.x = x;
			this.y = y;
			this.width = width;
		}
	}

	private Chrome chrome() {
		TableHud.Layout layout = table().measure(fontRendererObj);
		double scale = config().scale;
		int left = (int) Math.round(config().placement.x(scaled(width), layout.width()) * scale);
		int right = left + (int) Math.round(layout.width() * scale);
		int freeLeft = left;
		int freeRight = width - right;

		if (Math.max(freeLeft, freeRight) >= COLUMN_WIDTH + 16) {
			int centre = freeRight >= freeLeft ? right + freeRight / 2 : freeLeft / 2;
			int lineHeight = hintLines(COLUMN_WIDTH).size() * 10 + 8 + 4 * 24;
			return new Chrome(true, centre - COLUMN_WIDTH / 2, (height - lineHeight) / 2, COLUMN_WIDTH);
		}

		boolean top = tableIsInTheBottomHalf();
		return new Chrome(false, width / 2 - 155, top ? 8 : height - 8 - hintLines(310).size() * 10 - 8 - 44, 310);
	}

	@Override
	public void initGui() {
		buttonList.clear();
		nextId = 0;

		Chrome chrome = chrome();
		int x = chrome.x;
		int y = chrome.y + hintLines(chrome.width).size() * 10 + 8;
		int half = chrome.column ? COLUMN_WIDTH : 150;
		int second = chrome.column ? x : x + 160;
		int step = chrome.column ? 24 : 0;

		buttonList.add(new SettingSlider(nextId++, x, y, half, 20, "message.hypixelscout.editor.size",
				ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE, config().scale, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						config().scale = Math.round(value * 20) / 20.0;
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0);
					}
				}));

		buttonList.add(new SettingSlider(nextId++, second, y + step, half, 20, "message.hypixelscout.editor.opacity",
				0, 100, config().opacity, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						config().opacity = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return Math.round(value) + "%";
					}
				}));

		int buttons = y + (chrome.column ? 2 * step : 24);
		buttonList.add(new ActionButton(nextId++, x, buttons, half, 20,
				StatCollector.translateToLocal("message.hypixelscout.editor.reset"), new Runnable() {
					@Override
					public void run() {
						config().placement = TablePlacement.DEFAULT;
						config().scale = 1.0;
						initGui();
					}
				}));

		buttonList.add(new ActionButton(nextId++, second, buttons + step, half, 20,
				StatCollector.translateToLocal("gui.done"), new Runnable() {
					@Override
					public void run() {
						onGuiClosed();
						mc.displayGuiScreen(parent);
					}
				}));
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button instanceof Clickable) {
			((Clickable) button).onClick();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		// No dimmed vanilla background: the point is to see where the table sits over the real game.
		float scale = (float) config().scale;
		TableHud.Layout layout = table().measure(fontRendererObj);
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);

		int x = dragging ? dragX : config().placement.x(screenWidth, layout.width());
		int y = dragging ? dragY : config().placement.y(screenHeight, layout.height());

		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1.0f);

		if (dragging) {
			Gui.drawRect(0, 0, 1, screenHeight, GUIDE_COLOUR);
			Gui.drawRect(screenWidth / 2, 0, screenWidth / 2 + 1, screenHeight, GUIDE_COLOUR);
			Gui.drawRect(screenWidth - 1, 0, screenWidth, screenHeight, GUIDE_COLOUR);
			Gui.drawRect(0, 0, screenWidth, 1, GUIDE_COLOUR);
			Gui.drawRect(0, screenHeight / 2, screenWidth, screenHeight / 2 + 1, GUIDE_COLOUR);
			Gui.drawRect(0, screenHeight - 1, screenWidth, screenHeight, GUIDE_COLOUR);
		}

		table().draw(fontRendererObj, layout, x, y);
		outline(x - 1, y - 1, layout.width() + 2, layout.height() + 2, dragging ? 0xFFFFFFFF : ScoutTheme.accent(0xAA));
		GlStateManager.popMatrix();

		if (!dragging) {
			drawHints();
			super.drawScreen(mouseX, mouseY, partialTicks);
		}
	}

	private static void outline(int x, int y, int width, int height, int colour) {
		Gui.drawRect(x, y, x + width, y + 1, colour);
		Gui.drawRect(x, y + height - 1, x + width, y + height, colour);
		Gui.drawRect(x, y, x + 1, y + height, colour);
		Gui.drawRect(x + width - 1, y, x + width, y + height, colour);
	}

	private List<String> hintLines(int width) {
		List<String> lines = new ArrayList<String>();
		lines.addAll(wrap(StatCollector.translateToLocal("message.hypixelscout.editor.hint.drag"), width));
		lines.addAll(wrap(StatCollector.translateToLocal("message.hypixelscout.editor.hint.keys"), width));
		lines.addAll(wrap(StatCollector.translateToLocalFormatted("message.hypixelscout.editor.hint.anchor",
				StatCollector.translateToLocal("message.hypixelscout.anchor."
						+ config().placement.anchor().name().toLowerCase(Locale.ROOT))), width));
		return lines;
	}

	private List<String> wrap(String text, int width) {
		return fontRendererObj.listFormattedStringToWidth(text, width);
	}

	private void drawHints() {
		Chrome chrome = chrome();
		List<String> lines = hintLines(chrome.width);
		int controls = chrome.column ? 4 * 24 : 44;
		ScoutTheme.panel(chrome.x - 8, chrome.y - 7, chrome.width + 16, lines.size() * 10 + 8 + controls + 10, 85);

		int y = chrome.y;
		for (String line : lines) {
			ScoutTheme.textCentred(fontRendererObj, line, chrome.x + chrome.width / 2, y, HINT_COLOUR);
			y += 10;
		}
	}

	private boolean tableIsInTheBottomHalf() {
		TableHud.Layout layout = table().measure(fontRendererObj);
		int centre = config().placement.y(scaled(height), layout.height()) + layout.height() / 2;
		return centre > scaled(height) / 2;
	}

	private int scaled(double value) {
		return (int) Math.round(value / config().scale);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton != 0) {
			return;
		}

		TableHud.Layout layout = table().measure(fontRendererObj);
		int x = config().placement.x(scaled(width), layout.width());
		int y = config().placement.y(scaled(height), layout.height());
		int mx = scaled(mouseX);
		int my = scaled(mouseY);

		if (mx >= x && mx < x + layout.width() && my >= y && my < y + layout.height()) {
			dragging = true;
			dragX = x;
			dragY = y;
			dragOffsetX = mx - x;
			dragOffsetY = my - y;
		}
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int lastButtonClicked, long timeSinceLastClick) {
		if (!dragging) {
			return;
		}

		TableHud.Layout layout = table().measure(fontRendererObj);
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);
		int x = scaled(mouseX) - dragOffsetX;
		int y = scaled(mouseY) - dragOffsetY;

		if (!isShiftKeyDown()) {
			x = TablePlacement.snap(x, new int[] {0, (screenWidth - layout.width()) / 2, screenWidth - layout.width()},
					SNAP_THRESHOLD);
			y = TablePlacement.snap(y, new int[] {0, (screenHeight - layout.height()) / 2, screenHeight - layout.height()},
					SNAP_THRESHOLD);
		}

		dragX = clamp(x, 0, Math.max(0, screenWidth - layout.width()));
		dragY = clamp(y, 0, Math.max(0, screenHeight - layout.height()));
		config().placement = TablePlacement.fromTopLeft(screenWidth, screenHeight, layout.width(), layout.height(),
				dragX, dragY);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		if (dragging) {
			dragging = false;
			initGui();
			return;
		}
		super.mouseReleased(mouseX, mouseY, state);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = org.lwjgl.input.Mouse.getEventDWheel();
		if (wheel != 0) {
			config().scale = clamp(Math.round((config().scale + (wheel > 0 ? 0.05 : -0.05)) * 20) / 20.0,
					ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE);
			initGui();
		}
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : value > max ? max : value;
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		int dx = (keyCode == org.lwjgl.input.Keyboard.KEY_LEFT ? -1 : 0) + (keyCode == org.lwjgl.input.Keyboard.KEY_RIGHT ? 1 : 0);
		int dy = (keyCode == org.lwjgl.input.Keyboard.KEY_UP ? -1 : 0) + (keyCode == org.lwjgl.input.Keyboard.KEY_DOWN ? 1 : 0);

		if (dx == 0 && dy == 0) {
			super.keyTyped(typedChar, keyCode);
			return;
		}

		TableHud.Layout layout = table().measure(fontRendererObj);
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);
		int step = isShiftKeyDown() ? 10 : 1;

		int x = clamp(config().placement.x(screenWidth, layout.width()) + dx * step, 0,
				Math.max(0, screenWidth - layout.width()));
		int y = clamp(config().placement.y(screenHeight, layout.height()) + dy * step, 0,
				Math.max(0, screenHeight - layout.height()));
		config().placement = TablePlacement.fromTopLeft(screenWidth, screenHeight, layout.width(), layout.height(), x, y);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void onGuiClosed() {
		mod.saveSettings();
	}
}
