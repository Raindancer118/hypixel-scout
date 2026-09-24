package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.CardField;
import de.raindancer118.hypixelscout.core.CardLines;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * What every player card shows, and how big it is: the look tooltip, the proximity popup and the
 * chat hover each with their own fields — switched on and put in order, so many to a line — star,
 * rank, face and size; then the profile parts and the peek overlay's size.
 *
 * <p>Ported from 26.2's {@code ui.screen.CardsScreen}. 26.2 scrolls this with a {@code
 * ScrollableLayout}; here the whole grid is laid out into an off-screen coordinate space (a plain
 * {@code int} scroll offset shifts every widget's {@code yPosition} before each frame) and clipped
 * with {@link Scissor} — the same idea, without a widget class for it.
 */
public final class CardsScreen extends GuiScreen {
	private static final int WIDE = 150;
	private static final int SMALL = 20;
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
	private final List<int[]> rowBaseY = new ArrayList<int[]>();

	public CardsScreen(HypixelScout mod, GuiScreen parent) {
		this.mod = mod;
		this.parent = parent;
	}

	@Override
	public void initGui() {
		buttonList.clear();
		sections.clear();
		rowButtons.clear();
		rowBaseY.clear();
		nextId = 0;

		ScoutSettings.Cards cards = mod.settings().cards;
		TwoColumnGrid grid = new TwoColumnGrid(width / 2 - (WIDE * 2 + GAP) / 2, 30, WIDE, GAP, ROW);

		card(grid, "tooltip", cards.tooltip, CardLines.Layout.TOOLTIP, true);
		card(grid, "popup", cards.popup, CardLines.Layout.POPUP, true);
		card(grid, "hover", cards.hover, CardLines.Layout.TOOLTIP, false);

		section(grid, "message.hypixelscout.cards.profile");
		final ScoutSettings.Profile profile = cards.profile;
		buttonList.add(new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.cards.peek_size", ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE,
				cards.peekScale, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						cards.peekScale = Math.round(value * 20) / 20.0;
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0);
					}
				}));
		grid.advance();
		toggle(grid, "message.hypixelscout.cards.part.threat", profile.threat, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.threat = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.facts", profile.facts, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.facts = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.last_login", profile.lastLogin, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.lastLogin = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.combat", profile.combat, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.combat = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.games", profile.games, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.games = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.beds", profile.beds, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.beds = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.pace", profile.pace, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.pace = v; }
		});
		toggle(grid, "message.hypixelscout.cards.part.socials", profile.socials, new Consumer<Boolean>() {
			public void accept(Boolean v) { profile.socials = v; }
		});

		contentHeight = grid.bottom() - 30;
		int max = Math.max(0, contentHeight - (height - FOOTER - 30));
		scroll = clamp(scroll, 0, max);
		for (GuiButton button : rowButtons) {
			button.yPosition -= scroll;
		}

		buttonList.add(new ActionButton(nextId++, width / 2 - 100, height - FOOTER + 5, 200, 20,
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

	private GuiButton toggle(TwoColumnGrid grid, String key, boolean initial, Consumer<Boolean> set) {
		GuiButton button = CycleButton.onOff(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, key, initial, set);
		buttonList.add(button);
		rowButtons.add(button);
		grid.advance();
		return button;
	}

	private void card(TwoColumnGrid grid, String key, final ScoutSettings.Card card, final CardLines.Layout defaults,
			final boolean sizeable) {
		section(grid, "message.hypixelscout.cards." + key);

		if (sizeable) {
			GuiButton size = new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
					"message.hypixelscout.cards.size", ScoutSettings.MIN_SCALE, ScoutSettings.MAX_SCALE, card.scale,
					new java.util.function.DoubleConsumer() {
						@Override
						public void accept(double value) {
							card.scale = Math.round(value * 20) / 20.0;
						}
					}, new java.util.function.DoubleFunction<String>() {
						@Override
						public String apply(double value) {
							return String.format(Locale.ROOT, "%.2f×", Math.round(value * 20) / 20.0);
						}
					});
			buttonList.add(size);
			rowButtons.add(size);
			grid.advance();
		}

		GuiButton perLine = new SettingSlider(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.cards.per_line", 1, 6, card.perLine, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						card.perLine = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				});
		buttonList.add(perLine);
		rowButtons.add(perLine);
		grid.advance();

		toggle(grid, "message.hypixelscout.cards.stars", card.stars, new Consumer<Boolean>() {
			public void accept(Boolean v) { card.stars = v; }
		});
		toggle(grid, "message.hypixelscout.cards.rank", card.rank, new Consumer<Boolean>() {
			public void accept(Boolean v) { card.rank = v; }
		});
		if (sizeable) {
			toggle(grid, "message.hypixelscout.cards.head", card.head, new Consumer<Boolean>() {
				public void accept(Boolean v) { card.head = v; }
			});
		}

		GuiButton reset = new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.cards.reset"), new Runnable() {
					@Override
					public void run() {
						ScoutSettings.Card fresh = ScoutSettings.Card.of(defaults);
						card.fields = fresh.fields;
						card.perLine = fresh.perLine;
						card.stars = true;
						card.rank = true;
						card.head = true;
						card.scale = 1.0;
						initGui();
					}
				});
		buttonList.add(reset);
		rowButtons.add(reset);
		if (sizeable) {
			grid.advance();
		} else {
			grid.advanceRow();
		}

		List<CardField> order = new ArrayList<CardField>(card.fields);
		for (CardField field : CardField.values()) {
			if (!order.contains(field)) {
				order.add(field);
			}
		}

		for (final CardField field : order) {
			boolean on = card.fields.contains(field);
			String name = "message.hypixelscout.card.field." + field.name().toLowerCase(Locale.ROOT);
			GuiButton fieldToggle = CycleButton.onOff(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, name, on,
					new Consumer<Boolean>() {
						@Override
						public void accept(Boolean value) {
							card.fields.remove(field);
							if (value) {
								card.fields.add(field);
							}
							initGui();
						}
					});
			buttonList.add(fieldToggle);
			rowButtons.add(fieldToggle);

			int moveX = grid.x() + grid.columnWidth() - 2 * SMALL - 4;
			int index = card.fields.indexOf(field);
			GuiButton up = new ActionButton(nextId++, moveX, grid.y(), SMALL, 20, "↑", new Runnable() {
				@Override
				public void run() {
					move(card.fields, field, -1);
					initGui();
				}
			});
			up.enabled = on && index > 0;
			GuiButton down = new ActionButton(nextId++, moveX + SMALL + 4, grid.y(), SMALL, 20, "↓", new Runnable() {
				@Override
				public void run() {
					move(card.fields, field, 1);
					initGui();
				}
			});
			down.enabled = on && index < card.fields.size() - 1;
			buttonList.add(up);
			buttonList.add(down);
			rowButtons.add(up);
			rowButtons.add(down);
			grid.advance();
		}
		grid.newRow();
	}

	private static void move(List<CardField> fields, CardField field, int by) {
		int index = fields.indexOf(field);
		int target = index + by;
		if (index >= 0 && target >= 0 && target < fields.size()) {
			Collections.swap(fields, index, target);
		}
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

		drawScreenTitle();
	}

	private void drawScreenTitle() {
		ScoutTheme.textCentred(fontRendererObj, StatCollector.translateToLocal("message.hypixelscout.cards.title"),
				width / 2, 10, ScoutTheme.TEXT);
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
