package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.cheatwatch.Check;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import de.raindancer118.hypixelscout.ui.widget.Clickable;
import de.raindancer118.hypixelscout.ui.widget.CycleButton;
import de.raindancer118.hypixelscout.ui.widget.Scissor;
import de.raindancer118.hypixelscout.ui.widget.SettingSlider;
import de.raindancer118.hypixelscout.ui.widget.TwoColumnGrid;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every cheat option in one scrolling screen: each check on its own switch with its own
 * sensitivity, the limits the checks measure against, and the suspects card on the HUD.
 *
 * <p>Ported from 26.2's {@code ui.screen.CheatChecksScreen}, scrolled the same way {@link
 * CardsScreen} is (see that class's own documentation for why).
 */
public final class CheatChecksScreen extends GuiScreen {
	private static final int BUTTON_WIDTH = 150;
	private static final int GAP = 10;
	private static final int ROW = 24;
	private static final int FOOTER = 30;

	private final HypixelScout mod;
	private final GuiScreen parent;
	private int nextId;
	private int scroll;
	private int contentHeight;
	private final List<String[]> sections = new ArrayList<String[]>();
	private final List<GuiButton> rowButtons = new ArrayList<GuiButton>();

	public CheatChecksScreen(HypixelScout mod, GuiScreen parent) {
		this.mod = mod;
		this.parent = parent;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		sections.clear();
		rowButtons.clear();
		nextId = 0;

		final ScoutSettings.Cheats cheats = mod.settings().cheats;
		TwoColumnGrid grid = new TwoColumnGrid(width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2, 30, BUTTON_WIDTH, GAP, ROW);

		section(grid, "message.hypixelscout.settings.cheats.checks.section");
		for (final Check check : Check.values()) {
			GuiButton on = CycleButton.onOff(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, check.label(),
					cheats.isOn(check), new java.util.function.Consumer<Boolean>() {
						@Override
						public void accept(Boolean value) {
							cheats.set(check, value);
						}
					});
			buttonList.add(on);
			rowButtons.add(on);
			grid.advance();

			GuiButton sensitivity = new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
					"message.hypixelscout.settings.cheats.sensitivity", ScoutSettings.MIN_CHEAT_SENSITIVITY,
					ScoutSettings.MAX_CHEAT_SENSITIVITY, cheats.sensitivityOf(check), new java.util.function.DoubleConsumer() {
						@Override
						public void accept(double value) {
							cheats.setSensitivity(check, (int) Math.round(value / 5) * 5);
						}
					}, new java.util.function.DoubleFunction<String>() {
						@Override
						public String apply(double value) {
							return Math.round(value / 5) * 5 + "%";
						}
					});
			buttonList.add(sensitivity);
			rowButtons.add(sensitivity);
			grid.advance();
		}

		section(grid, "message.hypixelscout.settings.cheats.limits");
		limit(grid, "reach_standing", 3.0, 4.5, cheats.reachStanding, new java.util.function.DoubleConsumer() {
			public void accept(double v) { cheats.reachStanding = round(v, 0.05); }
		}, "%.2f");
		limit(grid, "reach_moving", 3.0, 5.0, cheats.reachMoving, new java.util.function.DoubleConsumer() {
			public void accept(double v) { cheats.reachMoving = round(v, 0.05); }
		}, "%.2f");
		limit(grid, "speed", 8.0, 30.0, cheats.speedPerSecond, new java.util.function.DoubleConsumer() {
			public void accept(double v) { cheats.speedPerSecond = round(v, 0.1); }
		}, "%.1f");
		limit(grid, "fastplace", 8, 30, cheats.fastPlacePerSecond, new java.util.function.DoubleConsumer() {
			public void accept(double v) { cheats.fastPlacePerSecond = (int) Math.round(v); }
		}, "%.0f");
		limit(grid, "bridge", 3.0, 10.0, cheats.bridgePerSecond, new java.util.function.DoubleConsumer() {
			public void accept(double v) { cheats.bridgePerSecond = round(v, 0.1); }
		}, "%.1f");

		GuiButton resetLimits = new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.cheats.limits.reset"), new Runnable() {
					@Override
					public void run() {
						cheats.reachStanding = 3.2;
						cheats.reachMoving = 3.8;
						cheats.speedPerSecond = 12.4;
						cheats.fastPlacePerSecond = 13;
						cheats.bridgePerSecond = 5.0;
						cheats.checkSensitivity.clear();
						initGui();
					}
				});
		buttonList.add(resetLimits);
		rowButtons.add(resetLimits);
		grid.advance();

		section(grid, "message.hypixelscout.settings.cheats.hud");
		final ScoutSettings.Hud hud = cheats.hud;
		toggle(grid, "hud.enabled", hud.enabled, new java.util.function.Consumer<Boolean>() {
			public void accept(Boolean v) { hud.enabled = v; }
		});
		toggle(grid, "hud.only_flagged", hud.onlyFlagged, new java.util.function.Consumer<Boolean>() {
			public void accept(Boolean v) { hud.onlyFlagged = v; }
		});

		GuiButton minPercent = new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.settings.cheats.hud.min_percent", 1, 99, hud.minPercent,
				new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						hud.minPercent = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return Math.round(value) + "%";
					}
				});
		buttonList.add(minPercent);
		rowButtons.add(minPercent);
		grid.advance();

		GuiButton maxRows = new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.settings.cheats.hud.rows", 1, 12, hud.maxRows, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						hud.maxRows = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				});
		buttonList.add(maxRows);
		rowButtons.add(maxRows);
		grid.advance();

		toggle(grid, "hud.show_checks", hud.showChecks, new java.util.function.Consumer<Boolean>() {
			public void accept(Boolean v) { hud.showChecks = v; }
		});

		GuiButton move = new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.suspects.move"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(new SuspectsEditorScreen(mod, CheatChecksScreen.this));
					}
				});
		buttonList.add(move);
		rowButtons.add(move);
		grid.advance();

		contentHeight = grid.bottom() - 30;
		int max = Math.max(0, contentHeight - (height - FOOTER - 30));
		scroll = clamp(scroll, 0, max);
		for (GuiButton button : rowButtons) {
			button.yPosition -= scroll;
		}

		int footerWidth = 120;
		buttonList.add(new ActionButton(nextId++, width / 2 - footerWidth - 4, height - FOOTER + 5, footerWidth, 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.cheats.checks.all_on"), new Runnable() {
					@Override
					public void run() {
						cheats.off.clear();
						initGui();
					}
				}));
		buttonList.add(new ActionButton(nextId++, width / 2 + 4, height - FOOTER + 5, footerWidth, 20,
				StatCollector.translateToLocal("gui.done"), new Runnable() {
					@Override
					public void run() {
						onGuiClosed();
						mc.displayGuiScreen(parent);
					}
				}));
	}

	private void section(TwoColumnGrid grid, String key) {
		grid.gap(6);
		sections.add(new String[] {key, String.valueOf(grid.y())});
		grid.advanceRow();
	}

	private void toggle(TwoColumnGrid grid, String key, boolean initial, java.util.function.Consumer<Boolean> set) {
		String full = "message.hypixelscout.settings.cheats." + key;
		GuiButton button = CycleButton.onOff(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, full, initial, set);
		buttonList.add(button);
		rowButtons.add(button);
		grid.advance();
	}

	private void limit(TwoColumnGrid grid, String key, double min, double max, double initial,
			java.util.function.DoubleConsumer apply, String format) {
		final String full = "message.hypixelscout.settings.cheats.limit." + key;
		final String fmt = format;
		GuiButton slider = new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, full, min, max,
				initial, apply, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.format(Locale.ROOT, fmt, value);
					}
				});
		buttonList.add(slider);
		rowButtons.add(slider);
		grid.advance();
	}

	private static double round(double value, double step) {
		return Math.round(value / step) * step;
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button instanceof Clickable) {
			((Clickable) button).onClick();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		int top = 30;
		int bottom = height - FOOTER;

		ScoutTheme.textCentred(fontRendererObj, StatCollector.translateToLocal("message.hypixelscout.settings.cheats.checks.title"),
				width / 2, 10, ScoutTheme.TEXT);

		Scissor.enable(0, top, width, bottom - top);
		for (String[] section : sections) {
			int y = Integer.parseInt(section[1]) - scroll;
			if (y > top - 12 && y < bottom) {
				ScoutTheme.textCentred(fontRendererObj, "§6" + StatCollector.translateToLocal(section[0]), width / 2,
						y, ScoutTheme.accent());
			}
		}
		for (GuiButton button : rowButtons) {
			if (button.yPosition + 20 > top && button.yPosition < bottom) {
				button.drawButton(mc, mouseX, mouseY);
			}
		}
		Scissor.disable();

		for (GuiButton button : buttonList) {
			if (!rowButtons.contains(button)) {
				button.drawButton(mc, mouseX, mouseY);
			}
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		int top = 30;
		int bottom = height - FOOTER;
		if (mouseButton == 0 && mouseY >= top && mouseY < bottom) {
			for (int i = rowButtons.size() - 1; i >= 0; i--) {
				GuiButton button = rowButtons.get(i);
				if (button.mousePressed(mc, mouseX, mouseY)) {
					button.playPressSound(mc.getSoundHandler());
					actionPerformed(button);
					return;
				}
			}
			return;
		}
		super.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			scroll = clamp(scroll - Integer.signum(wheel) * 16, 0, Math.max(0, contentHeight - (height - FOOTER - 30)));
			initGui();
		}
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
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
