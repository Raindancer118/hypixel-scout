package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.config.TablePlacement;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Drag the table where you want it.
 *
 * <p>The mod ships its own editor because a launcher's HUD editor only knows the launcher's own
 * mods, and a position that can only be edited in a JSON file is a position nobody moves. Outside a
 * game the table shows a made-up lobby, so it can be placed before there is a real one.
 */
public final class TableEditorScreen extends Screen {
	private static final int SNAP_THRESHOLD = 5;
	private static final int GUIDE_COLOUR = 0x55FFFFFF;
	private static final int HINT_COLOUR = 0xFFD0D0D8;

	private final Supplier<ScoutSettings> settings;
	private final TableHud live;
	private final TableHud sample;
	private final Roster roster;
	private final Runnable save;
	private final Screen parent;

	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private int dragX;
	private int dragY;

	public TableEditorScreen(Supplier<ScoutSettings> settings, TableHud live, Roster roster,
			Runnable save, Screen parent) {
		super(Component.translatable("message.hypixelscout.editor.title"));
		this.settings = settings;
		this.live = live;
		this.sample = new SampleGame().table(settings);
		this.roster = roster;
		this.save = save;
		this.parent = parent;
	}

	private TableHud table() {
		return roster.isInGame() && !roster.members().isEmpty() ? live : sample;
	}

	private ScoutSettings.Table config() {
		return settings.get().table;
	}

	/**
	 * Where the editor's own controls go: a column beside the table where the table leaves room for
	 * one, otherwise a row in the half of the screen it is not in.
	 */
	private record Chrome(boolean column, int x, int y, int width) {
	}

	private static final int COLUMN_WIDTH = 150;

	private Chrome chrome() {
		TableHud.Layout layout = table().measure();
		double scale = config().scale;
		int left = (int) Math.round(config().placement.x(scaled(width), layout.width()) * scale);
		int right = left + (int) Math.round(layout.width() * scale);
		int freeLeft = left;
		int freeRight = width - right;

		if (Math.max(freeLeft, freeRight) >= COLUMN_WIDTH + 16) {
			int centre = freeRight >= freeLeft ? right + freeRight / 2 : freeLeft / 2;
			int height = hintLines(COLUMN_WIDTH).size() * 10 + 8 + 4 * 24;
			return new Chrome(true, centre - COLUMN_WIDTH / 2, (this.height - height) / 2, COLUMN_WIDTH);
		}

		boolean top = tableIsInTheBottomHalf();
		return new Chrome(false, width / 2 - 155,
				top ? 8 : height - 8 - hintLines(310).size() * 10 - 8 - 44, 310);
	}

	@Override
	protected void init() {
		Chrome chrome = chrome();
		int x = chrome.x();
		int y = chrome.y() + hintLines(chrome.width()).size() * 10 + 8;
		int half = chrome.column() ? COLUMN_WIDTH : 150;
		int second = chrome.column() ? x : x + 160;
		int step = chrome.column() ? 24 : 0;

		addRenderableWidget(new SettingSlider(x, y, half,
				"message.hypixelscout.editor.size", ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE,
				config().scale, value -> config().scale = Math.round(value * 20) / 20.0,
				value -> String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0)));

		addRenderableWidget(new SettingSlider(second, y + step, half,
				"message.hypixelscout.editor.opacity", 0, 100, config().opacity,
				value -> config().opacity = (int) Math.round(value),
				value -> Math.round(value) + "%"));

		int buttons = y + (chrome.column() ? 2 * step : 24);
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.editor.reset"),
						button -> {
							config().placement = TablePlacement.DEFAULT;
							config().scale = 1.0;
							rebuildWidgets();
						})
				.bounds(x, buttons, half, 20).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(second, buttons + step, half, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		float scale = (float) config().scale;
		TableHud.Layout layout = table().measure();
		int screenWidth = scaled(graphics.guiWidth());
		int screenHeight = scaled(graphics.guiHeight());

		int x = dragging ? dragX : config().placement.x(screenWidth, layout.width());
		int y = dragging ? dragY : config().placement.y(screenHeight, layout.height());

		// The table first, so the controls stay on top of it wherever it is dragged.
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);

		if (dragging) {
			for (int guide : new int[] {0, screenWidth / 2, screenWidth - 1}) {
				graphics.verticalLine(guide, 0, screenHeight, GUIDE_COLOUR);
			}
			for (int guide : new int[] {0, screenHeight / 2, screenHeight - 1}) {
				graphics.horizontalLine(0, screenWidth, guide, GUIDE_COLOUR);
			}
		}

		table().draw(graphics, layout, x, y);
		graphics.outline(x - 1, y - 1, layout.width() + 2, layout.height() + 2,
				dragging ? 0xFFFFFFFF : ScoutTheme.accent(0xAA));
		graphics.pose().popMatrix();

		if (!dragging) {
			drawHints(graphics);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}
	}

	/** The three hints, wrapped to the width the controls have. */
	private List<FormattedCharSequence> hintLines(int width) {
		List<FormattedCharSequence> lines = new java.util.ArrayList<>();
		for (Component hint : List.of(
				Component.translatable("message.hypixelscout.editor.hint.drag"),
				Component.translatable("message.hypixelscout.editor.hint.keys"),
				Component.translatable("message.hypixelscout.editor.hint.anchor",
						Component.translatable("message.hypixelscout.anchor."
								+ config().placement.anchor().name().toLowerCase(Locale.ROOT))))) {
			lines.addAll(font.split(hint, width));
		}
		return lines;
	}

	private void drawHints(GuiGraphicsExtractor graphics) {
		Chrome chrome = chrome();
		List<FormattedCharSequence> lines = hintLines(chrome.width());
		int controls = chrome.column() ? 4 * 24 : 44;
		ScoutTheme.panel(graphics, chrome.x() - 8, chrome.y() - 7, chrome.width() + 16,
				lines.size() * 10 + 8 + controls + 10, 85);

		int y = chrome.y();
		for (FormattedCharSequence line : lines) {
			graphics.centeredText(font, line, chrome.x() + chrome.width() / 2, y, HINT_COLOUR);
			y += 10;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// No blur and no dimming: the point is to see where the table sits over the real game.
	}

	private boolean tableIsInTheBottomHalf() {
		TableHud.Layout layout = table().measure();
		int centre = config().placement.y(scaled(height), layout.height()) + layout.height() / 2;
		return centre > scaled(height) / 2;
	}

	private int scaled(double value) {
		return (int) Math.round(value / config().scale);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Widgets first: the table is drawn over them and must not swallow a click on Done.
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}

		TableHud.Layout layout = table().measure();
		int x = config().placement.x(scaled(width), layout.width());
		int y = config().placement.y(scaled(height), layout.height());
		int mouseX = scaled(event.x());
		int mouseY = scaled(event.y());

		if (mouseX >= x && mouseX < x + layout.width() && mouseY >= y && mouseY < y + layout.height()) {
			dragging = true;
			dragX = x;
			dragY = y;
			dragOffsetX = mouseX - x;
			dragOffsetY = mouseY - y;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (!dragging) {
			return super.mouseDragged(event, deltaX, deltaY);
		}

		TableHud.Layout layout = table().measure();
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);
		int x = scaled(event.x()) - dragOffsetX;
		int y = scaled(event.y()) - dragOffsetY;

		// Shift bypasses the snapping, for pixel-exact placements the guides would fight.
		if (!event.hasShiftDown()) {
			x = TablePlacement.snap(x, new int[] {0, (screenWidth - layout.width()) / 2,
					screenWidth - layout.width()}, SNAP_THRESHOLD);
			y = TablePlacement.snap(y, new int[] {0, (screenHeight - layout.height()) / 2,
					screenHeight - layout.height()}, SNAP_THRESHOLD);
		}

		dragX = Math.clamp(x, 0, Math.max(0, screenWidth - layout.width()));
		dragY = Math.clamp(y, 0, Math.max(0, screenHeight - layout.height()));
		config().placement = TablePlacement.fromTopLeft(screenWidth, screenHeight, layout.width(),
				layout.height(), dragX, dragY);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;

			// The table may have crossed the middle; move the controls out from under it.
			rebuildWidgets();
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (deltaY != 0) {
			config().scale = Math.clamp(Math.round((config().scale + (deltaY > 0 ? 0.05 : -0.05)) * 20) / 20.0,
					ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE);
			rebuildWidgets();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int dx = (event.isLeft() ? -1 : 0) + (event.isRight() ? 1 : 0);
		int dy = (event.isUp() ? -1 : 0) + (event.isDown() ? 1 : 0);

		if (dx == 0 && dy == 0) {
			return super.keyPressed(event);
		}

		TableHud.Layout layout = table().measure();
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);
		int step = event.hasShiftDown() ? 10 : 1;

		int x = Math.clamp(config().placement.x(screenWidth, layout.width()) + dx * step, 0,
				Math.max(0, screenWidth - layout.width()));
		int y = Math.clamp(config().placement.y(screenHeight, layout.height()) + dy * step, 0,
				Math.max(0, screenHeight - layout.height()));
		config().placement = TablePlacement.fromTopLeft(screenWidth, screenHeight, layout.width(),
				layout.height(), x, y);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		save.run();
		minecraft.gui.setScreen(parent);
	}
}
