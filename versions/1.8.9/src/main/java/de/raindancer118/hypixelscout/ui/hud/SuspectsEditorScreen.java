package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.cheat.Check;
import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.config.TablePlacement;
import de.raindancer118.hypixelscout.game.CheatSensor;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import de.raindancer118.hypixelscout.ui.widget.Clickable;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Drag the suspects card where you want it, and size it. Ported from 26.2's {@code
 * ui.hud.SuspectsEditorScreen}. With nobody suspected yet it shows two made-up ones, so it can be
 * placed before the first game.
 */
public final class SuspectsEditorScreen extends GuiScreen {
	private static final int SNAP_THRESHOLD = 5;

	private static final List<Suspicion.Suspect> SAMPLE = Arrays.asList(
			new Suspicion.Suspect("Sundial", 0.91, true, 0, Arrays.asList(
					new Suspicion.Seen(Check.REACH, 4, 0.76, true, "4.2 blocks", 0),
					new Suspicion.Seen(Check.SCAFFOLD, 8, 0.64, true, "bridged backwards", 0))),
			new Suspicion.Suspect("Brickmason", 0.58, false, 0, Arrays.asList(
					new Suspicion.Seen(Check.VELOCITY, 3, 0.58, false, "barely moved from a hit", 0))));

	private final HypixelScout mod;
	private final CheatSensor cheats;
	private final GuiScreen parent;
	private int nextId;

	private boolean dragging;
	private int dragOffsetX;
	private int dragOffsetY;

	public SuspectsEditorScreen(HypixelScout mod, GuiScreen parent) {
		this.mod = mod;
		this.cheats = mod.cheats();
		this.parent = parent;
	}

	private ScoutSettings.Hud hud() {
		return mod.settings().cheats.hud;
	}

	private List<Suspicion.Suspect> shown() {
		List<Suspicion.Suspect> real = SuspectsHud.shown(cheats.suspects(), hud());
		return real.isEmpty() ? SAMPLE : real;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		nextId = 0;

		final int y = height / 2 - 34;
		final int x = width / 2 - 75;

		buttonList.add(new SettingSlider(nextId++, x, y, 150, 20, "message.hypixelscout.editor.size",
				ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE, hud().scale, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						hud().scale = Math.round(value * 20) / 20.0;
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0);
					}
				}));

		buttonList.add(new ActionButton(nextId++, x, y + 24, 150, 20,
				StatCollector.translateToLocal("message.hypixelscout.editor.reset"), new Runnable() {
					@Override
					public void run() {
						hud().placement = ScoutSettings.DEFAULT_SUSPECTS_PLACEMENT;
						hud().scale = 1.0;
						initGui();
					}
				}));

		buttonList.add(new ActionButton(nextId++, x, y + 48, 150, 20, StatCollector.translateToLocal("gui.done"),
				new Runnable() {
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

	private int scaled(double value) {
		return (int) Math.round(value / hud().scale);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		ScoutTheme.textCentred(fontRendererObj, "§7" + StatCollector.translateToLocal("message.hypixelscout.suspects.editor.hint"),
				width / 2, height / 2 - 50, ScoutTheme.TEXT);

		SuspectsHud.Layout layout = SuspectsHud.measure(fontRendererObj, shown(), hud());
		int x = hud().placement.x(scaled(width), layout.width());
		int y = hud().placement.y(scaled(height), layout.height());

		net.minecraft.client.renderer.GlStateManager.pushMatrix();
		net.minecraft.client.renderer.GlStateManager.scale((float) hud().scale, (float) hud().scale, 1.0f);
		SuspectsHud.draw(fontRendererObj, layout, x, y, 92);
		net.minecraft.client.renderer.GlStateManager.popMatrix();

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	/** The card's top-left corner and size in unscaled GUI pixels. */
	private int[] box() {
		SuspectsHud.Layout layout = SuspectsHud.measure(fontRendererObj, shown(), hud());
		int x = hud().placement.x(scaled(width), layout.width());
		int y = hud().placement.y(scaled(height), layout.height());
		return new int[] {x, y, layout.width(), layout.height()};
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);
		if (mouseButton != 0) {
			return;
		}

		int[] box = box();
		int mx = (int) Math.round(mouseX / hud().scale);
		int my = (int) Math.round(mouseY / hud().scale);
		if (mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
			dragging = true;
			dragOffsetX = mx - box[0];
			dragOffsetY = my - box[1];
		}
	}

	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int lastButtonClicked, long timeSinceLastClick) {
		if (!dragging) {
			return;
		}

		int[] box = box();
		int screenWidth = scaled(width);
		int screenHeight = scaled(height);
		int x = (int) Math.round(mouseX / hud().scale) - dragOffsetX;
		int y = (int) Math.round(mouseY / hud().scale) - dragOffsetY;
		x = TablePlacement.snap(x, new int[] {0, (screenWidth - box[2]) / 2, screenWidth - box[2]}, SNAP_THRESHOLD);
		y = TablePlacement.snap(y, new int[] {0, (screenHeight - box[3]) / 2, screenHeight - box[3]}, SNAP_THRESHOLD);
		hud().placement = TablePlacement.fromTopLeft(screenWidth, screenHeight, box[2], box[3], x, y);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		dragging = false;
		super.mouseReleased(mouseX, mouseY, state);
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
