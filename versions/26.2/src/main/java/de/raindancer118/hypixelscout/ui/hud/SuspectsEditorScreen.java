package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.cheatwatch.Check;
import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.config.TablePlacement;
import de.raindancer118.hypixelscout.game.CheatSensor;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Drag the suspects card where you want it, and size it. With nobody suspected yet it shows two
 * made-up ones, so it can be placed before the first game.
 */
public final class SuspectsEditorScreen extends Screen {
	private static final int SNAP_THRESHOLD = 5;

	private static final List<Suspicion.Suspect> SAMPLE = List.of(
			new Suspicion.Suspect("Sundial", 0.91, true, 0, List.of(
					new Suspicion.Seen(Check.REACH, 4, 0.76, true, "4.2 blocks", 0),
					new Suspicion.Seen(Check.SCAFFOLD, 8, 0.64, true, "bridged backwards", 0))),
			new Suspicion.Suspect("Brickmason", 0.58, false, 0, List.of(
					new Suspicion.Seen(Check.VELOCITY, 3, 0.58, false, "barely moved from a hit", 0))));

	private final Supplier<ScoutSettings> settings;
	private final CheatSensor cheats;
	private final Runnable save;
	private final Screen parent;

	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	public SuspectsEditorScreen(Supplier<ScoutSettings> settings, CheatSensor cheats, Runnable save, Screen parent) {
		super(Component.translatable("message.hypixelscout.suspects.editor"));
		this.settings = settings;
		this.cheats = cheats;
		this.save = save;
		this.parent = parent;
	}

	private ScoutSettings.Hud hud() {
		return settings.get().cheats.hud;
	}

	private List<Suspicion.Suspect> shown() {
		List<Suspicion.Suspect> real = SuspectsHud.shown(cheats.suspects(), hud());
		return real.isEmpty() ? SAMPLE : real;
	}

	@Override
	protected void init() {
		int y = height / 2 - 34;
		int x = width / 2 - 75;
		addRenderableWidget(new SettingSlider(x, y, 150, "message.hypixelscout.editor.size",
				ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE, hud().scale,
				value -> hud().scale = Math.round(value * 20) / 20.0,
				value -> String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0)));
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.editor.reset"), button -> {
			hud().placement = ScoutSettings.DEFAULT_SUSPECTS_PLACEMENT;
			hud().scale = 1.0;
			rebuildWidgets();
		}).bounds(x, y + 24, 150, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(x, y + 48, 150, 20).build());
	}

	private int scaled(double value) {
		return (int) Math.round(value / hud().scale);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		ScoutTheme.textCentred(g, "§7" + I18n.get("message.hypixelscout.suspects.editor.hint"), width / 2, height / 2 - 50,
				ScoutTheme.TEXT);
		SuspectsElement.draw(g, shown(), settings.get());
	}

	/** The card's top-left corner and size in unscaled GUI pixels. */
	private int[] box() {
		SuspectsHud.Layout layout = SuspectsHud.measure(shown(), hud());
		int x = hud().placement.x(scaled(width), layout.width());
		int y = hud().placement.y(scaled(height), layout.height());
		return new int[] {x, y, layout.width(), layout.height()};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		int[] box = box();
		double mx = event.x() / hud().scale;
		double my = event.y() / hud().scale;
		if (mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
			dragging = true;
			dragOffsetX = (int) Math.round(mx) - box[0];
			dragOffsetY = (int) Math.round(my) - box[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (!dragging) {
			return super.mouseDragged(event, deltaX, deltaY);
		}
		int[] box = box();
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);
		int x = (int) Math.round(event.x() / hud().scale) - dragOffsetX;
		int y = (int) Math.round(event.y() / hud().scale) - dragOffsetY;
		x = TablePlacement.snap(x, new int[] {0, (screenWidth - box[2]) / 2, screenWidth - box[2]}, SNAP_THRESHOLD);
		y = TablePlacement.snap(y, new int[] {0, (screenHeight - box[3]) / 2, screenHeight - box[3]}, SNAP_THRESHOLD);
		hud().placement = TablePlacement.fromTopLeft(screenWidth, screenHeight, box[2], box[3], x, y);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
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
