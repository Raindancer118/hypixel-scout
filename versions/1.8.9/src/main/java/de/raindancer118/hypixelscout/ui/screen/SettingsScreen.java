package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.ScoutCommands;
import de.raindancer118.hypixelscout.config.Accent;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.ChatPacing;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.RequeueMode;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatFocus;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import de.raindancer118.hypixelscout.ui.widget.Clickable;
import de.raindancer118.hypixelscout.ui.widget.CycleButton;
import de.raindancer118.hypixelscout.ui.widget.KeyBindButton;
import de.raindancer118.hypixelscout.ui.widget.TabBar;
import de.raindancer118.hypixelscout.ui.widget.TwoColumnGrid;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every setting, in nine tabs of this module's own widgets.
 *
 * <p>Ported from 26.2's {@code ui.screen.SettingsScreen}: a {@link TabBar} stands in for {@code
 * MenuTabBar}/{@code GridLayoutTab} (26.2's own, see that class's documentation) and a
 * {@link TwoColumnGrid} stands in for {@code GridLayout.RowHelper}. The JSON file stays the source
 * of truth and stays hand-editable; this is the front for it, so nobody has to leave the game to
 * turn the nametags on. Changes apply at once and are written when the screen closes.
 */
public final class SettingsScreen extends GuiScreen {
	private static final int FOOTER = 33;
	private static final int WIDE = 310;
	private static final int NARROW = 150;
	private static final int GAP = 10;
	private static final int ROW = 24;

	private static final String[] TAB_KEYS = {"general", "table", "overlays", "alerts", "projectiles",
			"awareness", "cheats", "callouts", "keys"};
	public static final int KEYS_TAB = 8;
	public static final int CALLOUTS_TAB = 7;

	private final HypixelScout mod;
	private final GuiScreen parent;
	private TabBar tabBar;
	private int selected;
	private int nextId;

	private GuiTextField keyBox;
	private String keyMessage;
	/** For the client startup test: the general tab's "look up in the lobby" toggle. */
	private GuiButton lobbyToggle;
	private final List<KeyBindButton> keyButtons = new ArrayList<KeyBindButton>();
	private KeyBindButton listening;

	/**
	 * Every tab's own widgets scroll together, clipped to the band between the tab bar and the
	 * footer — some tabs (General, Keys) have more rows than a small window has room for, and 1.8.9
	 * has nothing like 26.2's {@code ScrollableLayout} to fall back on.
	 */
	private final List<GuiButton> rowButtons = new ArrayList<GuiButton>();
	private int scroll;
	private int contentTop;
	private int contentBottom;
	private int contentHeight;

	public SettingsScreen(HypixelScout mod, GuiScreen parent) {
		this.mod = mod;
		this.parent = parent;
	}

	public SettingsScreen onTab(int index) {
		selected = index;
		return this;
	}

	private ScoutSettings settings() {
		return mod.settings();
	}

	@Override
	public void initGui() {
		buttonList.clear();
		keyButtons.clear();
		calloutBoxes.clear();
		nextId = 0;
		keyBox = null;

		if (keyMessage == null) {
			keyMessage = StatCollector.translateToLocal(mod.client().hasApiKey()
					? "message.hypixelscout.key.stored" : "message.hypixelscout.key.none");
		}

		String[] labels = new String[TAB_KEYS.length];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = StatCollector.translateToLocal("message.hypixelscout.settings.tab." + TAB_KEYS[i]);
		}
		tabBar = new TabBar(labels, selected, new java.util.function.IntConsumer() {
			@Override
			public void accept(int value) {
				selected = value;
				initGui();
			}
		});
		tabBar.layout(0, 0, width);

		contentTop = tabBar.bottom() + 4;
		contentBottom = height - FOOTER;
		int top = contentTop + 10;
		TwoColumnGrid grid = new TwoColumnGrid(width / 2 - (NARROW * 2 + GAP) / 2, top, NARROW, GAP, ROW);

		switch (TAB_KEYS[selected]) {
			case "general":
				general(grid);
				break;
			case "table":
				table(grid);
				break;
			case "overlays":
				overlays(grid);
				break;
			case "alerts":
				alerts(grid);
				break;
			case "projectiles":
				projectiles(grid);
				break;
			case "awareness":
				awareness(grid);
				break;
			case "cheats":
				cheats(grid);
				break;
			case "callouts":
				callouts(grid);
				break;
			case "keys":
				keys(grid);
				break;
			default:
		}

		// Everything the tab just built scrolls as one unit; the tab bar and the Done button, added
		// after this, do not.
		rowButtons.clear();
		rowButtons.addAll(buttonList);
		contentHeight = grid.bottom() - top;
		int maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
		scroll = clamp(scroll, 0, maxScroll);
		for (GuiButton button : rowButtons) {
			button.yPosition -= scroll;
		}
		for (String[] label : sectionLabels) {
			label[1] = String.valueOf(Integer.parseInt(label[1]) - scroll);
		}
		if (keyBox != null) {
			keyBox.yPosition -= scroll;
		}
		keyMessageBottom -= scroll;
		for (GuiTextField box : calloutBoxes) {
			box.yPosition -= scroll;
		}

		buttonList.add(new ActionButton(nextId++, width / 2 - 100, height - FOOTER + 7, 200, 20,
				StatCollector.translateToLocal("gui.done"), new Runnable() {
					@Override
					public void run() {
						onGuiClosed();
						mc.displayGuiScreen(parent);
					}
				}));
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button instanceof Clickable) {
			((Clickable) button).onClick();
		}
	}

	// --- widgets --------------------------------------------------------------------------------

	private GuiButton toggle(TwoColumnGrid grid, String key, boolean initial, Consumer<Boolean> set) {
		GuiButton button = CycleButton.onOff(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.settings." + key, initial, set);
		buttonList.add(button);
		grid.advance();
		return button;
	}

	private <E extends Enum<E>> CycleButton<E> enumCycle(TwoColumnGrid grid, String captionKey, E[] values, E initial,
			final String group, Consumer<E> set) {
		CycleButton<E> button = new CycleButton<E>(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, captionKey,
				values, initial, new java.util.function.Function<E, String>() {
					@Override
					public String apply(E value) {
						return StatCollector.translateToLocal("message.hypixelscout." + group + "." + value.name().toLowerCase(Locale.ROOT));
					}
				}, set);
		grid.advance();
		return button;
	}

	private void section(TwoColumnGrid grid, String key) {
		// Drawn by drawScreen at the row the grid was about to use; the row itself is still consumed.
		sectionLabels.add(new String[] {key, String.valueOf(grid.y())});
		grid.advanceRow();
	}

	private final List<String[]> sectionLabels = new ArrayList<String[]>();

	// --- general ----------------------------------------------------------------------------

	private void general(TwoColumnGrid grid) {
		sectionLabels.clear();
		int x = grid.wideX();
		keyBox = new GuiTextField(nextId++, fontRendererObj, x, grid.y(), WIDE, 20);
		keyBox.setMaxStringLength(36);
		keyBox.setText(settings().apiKey);
		grid.advanceRow();

		buttonList.add(new ActionButton(nextId++, x, grid.y(), NARROW, 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.key.save"), new Runnable() {
					@Override
					public void run() {
						saveKey();
					}
				}));
		buttonList.add(new ActionButton(nextId++, x + NARROW + GAP, grid.y(), NARROW, 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.key.test"), new Runnable() {
					@Override
					public void run() {
						testKey();
					}
				}));
		grid.advanceRow();
		grid.advanceRow();

		buttonList.add(new ActionButton(nextId++, x, grid.y(), NARROW, 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.key.get"), new Runnable() {
					@Override
					public void run() {
						openKeyPage();
					}
				}));
		grid.advance();

		buttonList.add(enumCycle(grid, "message.hypixelscout.settings.accent", Accent.values(), settings().accent,
				"accent", new Consumer<Accent>() {
					@Override
					public void accept(Accent value) {
						settings().accent = value;
					}
				}));

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.cache", 1, ScoutSettings.MAX_CACHE_MINUTES,
				settings().cacheMinutes, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						settings().cacheMinutes = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				}));
		grid.advance();

		toggle(grid, "hypixel_only", settings().queue.onlyOnHypixel, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().queue.onlyOnHypixel = value;
			}
		});
		lobbyToggle = toggle(grid, "lobby", settings().lookUpInLobby, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().lookUpInLobby = value;
			}
		});

		buttonList.add(enumCycle(grid, "message.hypixelscout.settings.threat_basis", ThreatScale.Basis.values(),
				settings().threatBasis, "threat_basis", new Consumer<ThreatScale.Basis>() {
					@Override
					public void accept(ThreatScale.Basis value) {
						settings().threatBasis = value;
					}
				}));
		buttonList.add(enumCycle(grid, "message.hypixelscout.settings.threat_focus", ThreatFocus.values(),
				settings().threatFocus, "threat_focus", new Consumer<ThreatFocus>() {
					@Override
					public void accept(ThreatFocus value) {
						settings().threatFocus = value;
					}
				}));

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.threat_sensitivity", ScoutSettings.MIN_SENSITIVITY,
				ScoutSettings.MAX_SENSITIVITY, settings().threatSensitivity, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						settings().threatSensitivity = sensitivityStep(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return sensitivityStep(value) + "%";
					}
				}));
		grid.advance();

		List<Threat> rated = Threat.rated();
		buttonList.add(new CycleButton<Threat>(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.settings.report_from", rated.toArray(new Threat[0]), settings().threatReportFrom,
				new java.util.function.Function<Threat, String>() {
					@Override
					public String apply(Threat value) {
						return value.colour() + value.label();
					}
				}, new Consumer<Threat>() {
					@Override
					public void accept(Threat value) {
						settings().threatReportFrom = value;
					}
				}));
		grid.advance();

		keyMessageBottom = grid.bottom() + 6;
	}

	private int keyMessageBottom;

	private static int sensitivityStep(double value) {
		return (int) Math.round(value / 5.0) * 5;
	}

	// --- table --------------------------------------------------------------------------------

	private void table(TwoColumnGrid grid) {
		sectionLabels.clear();
		buttonList.add(enumCycle(grid, "message.hypixelscout.settings.mode", HudMode.values(), settings().table.mode,
				"mode", new Consumer<HudMode>() {
					@Override
					public void accept(HudMode value) {
						settings().table.mode = value;
					}
				}));
		buttonList.add(enumCycle(grid, "message.hypixelscout.settings.sort", SortMode.values(), settings().table.sort,
				"sort", new Consumer<SortMode>() {
					@Override
					public void accept(SortMode value) {
						settings().table.sort = value;
					}
				}));

		toggle(grid, "group", settings().table.groupByTeam, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.groupByTeam = value;
			}
		});
		toggle(grid, "hide_own", settings().table.hideOwnTeam, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.hideOwnTeam = value;
			}
		});
		toggle(grid, "wlr", settings().table.showWlr, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.showWlr = value;
			}
		});
		toggle(grid, "winstreak", settings().table.showWinstreak, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.showWinstreak = value;
			}
		});
		toggle(grid, "beds_per_game", settings().table.showBedsPerGame, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.showBedsPerGame = value;
			}
		});
		toggle(grid, "kills_per_game", settings().table.showKillsPerGame, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.showKillsPerGame = value;
			}
		});
		toggle(grid, "beds", settings().table.showBeds, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.showBeds = value;
			}
		});
		toggle(grid, "age", settings().table.showAccountAge, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().table.showAccountAge = value;
			}
		});

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.rows", 1, ScoutSettings.MAX_ROWS,
				settings().table.maxRows, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						settings().table.maxRows = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				}));
		grid.advance();

		buttonList.add(new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.move_table"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(mod.tableEditor(SettingsScreen.this));
					}
				}));
		grid.advance();
	}

	// --- overlays ---------------------------------------------------------------------------

	private void overlays(TwoColumnGrid grid) {
		sectionLabels.clear();
		final ScoutSettings.Tooltip tooltip = settings().tooltip;
		toggle(grid, "tooltip", tooltip.enabled, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				tooltip.enabled = value;
			}
		});
		toggle(grid, "walls", tooltip.throughWalls, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				tooltip.throughWalls = value;
			}
		});

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.angle", ScoutSettings.MIN_ANGLE,
				ScoutSettings.MAX_ANGLE, tooltip.angle, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						tooltip.angle = Math.round(value * 2) / 2.0;
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.format(Locale.ROOT, "%.1f°", Math.round(value * 2) / 2.0);
					}
				}));
		grid.advance();

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.offset", -100, 100, tooltip.offsetY,
				new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						tooltip.offsetY = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				}));
		grid.advance();

		toggle(grid, "tab", settings().tab.enabled, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().tab.enabled = value;
			}
		});
		toggle(grid, "nametag_stars", settings().nametag.stars, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().nametag.stars = value;
			}
		});
		toggle(grid, "nametag_fkdr", settings().nametag.fkdr, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				settings().nametag.fkdr = value;
			}
		});

		final ScoutSettings.Proximity proximity = settings().proximity;
		toggle(grid, "proximity", proximity.enabled, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				proximity.enabled = value;
			}
		});

		List<Threat> rated = Threat.rated();
		buttonList.add(new CycleButton<Threat>(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				"message.hypixelscout.settings.proximity_from", rated.toArray(new Threat[0]), proximity.from,
				new java.util.function.Function<Threat, String>() {
					@Override
					public String apply(Threat value) {
						return value.colour() + value.label();
					}
				}, new Consumer<Threat>() {
					@Override
					public void accept(Threat value) {
						proximity.from = value;
					}
				}));
		grid.advance();

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.proximity_radius", 2, ScoutSettings.MAX_RADIUS,
				proximity.radius, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						proximity.radius = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				}));
		grid.advance();

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.proximity_seconds", 1,
				ScoutSettings.MAX_POPUP_SECONDS, proximity.seconds, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						proximity.seconds = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return Math.round(value) + " s";
					}
				}));
		grid.advance();

		buttonList.add(new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.cards"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(new CardsScreen(mod, SettingsScreen.this));
					}
				}));
		grid.advance();
	}

	// --- alerts -----------------------------------------------------------------------------

	private void alerts(TwoColumnGrid grid) {
		sectionLabels.clear();
		final ScoutSettings.Alerts a = settings().alerts;
		toggle(grid, "chat_hover", a.chatHover, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				a.chatHover = value;
			}
		});
		toggle(grid, "nick_alert", a.nickAlert, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				a.nickAlert = value;
			}
		});
		toggle(grid, "streak_alert", a.streakAlert, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				a.streakAlert = value;
			}
		});

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.streak", 5, 200, a.streakThreshold,
				new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						a.streakThreshold = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				}));
		grid.advance();

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.report_interval", ChatPacing.MIN_TICKS,
				ChatPacing.MAX_TICKS, settings().reportIntervalTicks, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						settings().reportIntervalTicks = intervalStep(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.format(Locale.ROOT, "%.1f s", intervalStep(value) / 20.0);
					}
				}));
		grid.advance();

		buttonList.add(enumCycle(grid, "message.hypixelscout.settings.requeue", RequeueMode.values(),
				settings().requeue.mode, "requeue", new Consumer<RequeueMode>() {
					@Override
					public void accept(RequeueMode value) {
						settings().requeue.mode = value;
					}
				}));

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.requeue_delay", 0, ScoutSettings.MAX_REQUEUE_DELAY,
				settings().requeue.delaySeconds, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						settings().requeue.delaySeconds = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return Math.round(value) + " s";
					}
				}));
		grid.advance();
	}

	private static int intervalStep(double ticks) {
		return (int) Math.round(ticks / 2.0) * 2;
	}

	// --- projectiles -------------------------------------------------------------------------

	private void projectiles(TwoColumnGrid grid) {
		sectionLabels.clear();
		final ScoutSettings.Projectiles p = settings().projectiles;
		toggle(grid, "paths", p.paths, new Consumer<Boolean>() { public void accept(Boolean v) { p.paths = v; } });
		toggle(grid, "incoming", p.alarm, new Consumer<Boolean>() { public void accept(Boolean v) { p.alarm = v; } });
		toggle(grid, "incoming_sound", p.sound, new Consumer<Boolean>() { public void accept(Boolean v) { p.sound = v; } });
		toggle(grid, "target_lock", p.lock, new Consumer<Boolean>() { public void accept(Boolean v) { p.lock = v; } });
		toggle(grid, "fireball_aim", p.aim, new Consumer<Boolean>() { public void accept(Boolean v) { p.aim = v; } });
		toggle(grid, "arrows", p.arrows, new Consumer<Boolean>() { public void accept(Boolean v) { p.arrows = v; } });
		toggle(grid, "fireballs", p.fireballs, new Consumer<Boolean>() { public void accept(Boolean v) { p.fireballs = v; } });
		toggle(grid, "only_in_game", p.onlyInGame, new Consumer<Boolean>() { public void accept(Boolean v) { p.onlyInGame = v; } });
		toggle(grid, "pearls", p.pearls, new Consumer<Boolean>() { public void accept(Boolean v) { p.pearls = v; } });
		toggle(grid, "pearl_aim", p.pearlAim, new Consumer<Boolean>() { public void accept(Boolean v) { p.pearlAim = v; } });
		toggle(grid, "bow_aim", p.bowAim, new Consumer<Boolean>() { public void accept(Boolean v) { p.bowAim = v; } });
		toggle(grid, "blast_preview", p.blastPreview, new Consumer<Boolean>() { public void accept(Boolean v) { p.blastPreview = v; } });
	}

	// --- awareness ---------------------------------------------------------------------------

	private void awareness(TwoColumnGrid grid) {
		sectionLabels.clear();
		final ScoutSettings.Awareness a = settings().awareness;
		toggle(grid, "tnt", a.tnt, new Consumer<Boolean>() { public void accept(Boolean v) { a.tnt = v; } });
		toggle(grid, "void_warning", a.voidWarning, new Consumer<Boolean>() { public void accept(Boolean v) { a.voidWarning = v; } });
		toggle(grid, "bed_defense", a.bedDefense, new Consumer<Boolean>() { public void accept(Boolean v) { a.bedDefense = v; } });
		toggle(grid, "offscreen", a.offscreen, new Consumer<Boolean>() { public void accept(Boolean v) { a.offscreen = v; } });

		buttonList.add(new de.raindancer118.hypixelscout.ui.widget.SettingSlider(nextId++, grid.x(), grid.y(),
				grid.columnWidth(), 20, "message.hypixelscout.settings.offscreen_range", 8, ScoutSettings.MAX_OFFSCREEN_RANGE,
				a.offscreenRange, new java.util.function.DoubleConsumer() {
					@Override
					public void accept(double value) {
						a.offscreenRange = (int) Math.round(value);
					}
				}, new java.util.function.DoubleFunction<String>() {
					@Override
					public String apply(double value) {
						return String.valueOf(Math.round(value));
					}
				}));
		grid.advance();
	}

	// --- cheats -------------------------------------------------------------------------------

	private void cheats(TwoColumnGrid grid) {
		sectionLabels.clear();
		// The detection is Scout's now, with its own settings: this tab only leads there.
		buttonList.add(new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.cheats.scout"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(new de.raindancer118.scout.forge.ui.screen.SettingsScreen(SettingsScreen.this));
					}
				}));
		grid.advance();
		buttonList.add(new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.suspects.move"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(new de.raindancer118.scout.forge.ui.hud.HudEditorScreen(SettingsScreen.this));
					}
				}));
		grid.advance();
	}

	// --- callouts -----------------------------------------------------------------------------

	private void callouts(TwoColumnGrid grid) {
		sectionLabels.clear();
		final ScoutSettings.Callouts callouts = settings().callouts;
		int fieldWidth = WIDE - NARROW / 2 - GAP - 40;

		for (int slot = 0; slot < ScoutSettings.CALLOUTS; slot++) {
			final int index = slot;
			GuiTextField box = new GuiTextField(nextId++, fontRendererObj, grid.wideX(), grid.y(), fieldWidth, 20);
			box.setMaxStringLength(100);
			box.setText(callouts.messages[slot]);
			calloutBoxes.add(box);

			KeyBindButton keyButton = new KeyBindButton(nextId++, grid.wideX() + fieldWidth + GAP, grid.y(),
					NARROW / 2 + 40, 20, mod.keys().calloutBinding(slot),
					StatCollector.translateToLocalFormatted("message.hypixelscout.settings.callout.key", slot + 1),
					new Consumer<KeyBindButton>() {
						@Override
						public void accept(KeyBindButton value) {
							listenWith(value);
						}
					});
			buttonList.add(keyButton);
			keyButtons.add(keyButton);
			grid.advanceRow();
		}

		toggle(grid, "callout_party", callouts.toParty, new Consumer<Boolean>() {
			@Override
			public void accept(Boolean value) {
				callouts.toParty = value;
			}
		});
	}

	private final List<GuiTextField> calloutBoxes = new ArrayList<GuiTextField>();

	// --- keys ---------------------------------------------------------------------------------

	private void keys(TwoColumnGrid grid) {
		sectionLabels.clear();
		for (KeyBinding mapping : mod.keys().settingsMappings()) {
			String name = mapping.getKeyDescription().substring("key.hypixelscout.".length());
			KeyBindButton button = new KeyBindButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20, mapping,
					StatCollector.translateToLocal("message.hypixelscout.settings.keys." + name),
					new Consumer<KeyBindButton>() {
						@Override
						public void accept(KeyBindButton value) {
							listenWith(value);
						}
					});
			buttonList.add(button);
			keyButtons.add(button);
			grid.advance();
		}

		buttonList.add(new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.keys.reset"), new Runnable() {
					@Override
					public void run() {
						for (KeyBinding mapping : mod.keys().settingsMappings()) {
							mapping.setKeyCode(mapping.getKeyCodeDefault());
						}
						KeyBinding.resetKeyBindingArrayAndHash();
						mc.gameSettings.saveOptions();
						for (KeyBindButton button : keyButtons) {
							button.refresh();
						}
					}
				}));
		grid.advance();

		buttonList.add(new ActionButton(nextId++, grid.x(), grid.y(), grid.columnWidth(), 20,
				StatCollector.translateToLocal("message.hypixelscout.settings.keys.all"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(new GuiControls(SettingsScreen.this, mc.gameSettings));
					}
				}));
		grid.advance();
	}

	private void listenWith(KeyBindButton button) {
		if (listening != null && listening != button) {
			listening.stopListening();
		}
		listening = button;
	}

	// --- the key --------------------------------------------------------------------------------

	static String mask(String visible) {
		char[] chars = visible.toCharArray();
		for (int i = 8; i < chars.length; i++) {
			if (chars[i] != '-') {
				chars[i] = '•';
			}
		}
		return new String(chars);
	}

	private boolean saveKey() {
		String typed = keyBox.getText().trim();

		if (typed.isEmpty()) {
			mod.setApiKey("");
			status("message.hypixelscout.key.none");
			return true;
		}

		try {
			UUID.fromString(typed);
		} catch (IllegalArgumentException e) {
			status("message.hypixelscout.key.malformed");
			return false;
		}

		if (!typed.equals(settings().apiKey)) {
			mod.setApiKey(typed);
		}
		status("message.hypixelscout.key.saved");
		return true;
	}

	private void testKey() {
		if (!saveKey()) {
			return;
		}

		status("message.hypixelscout.key.checking");
		mod.checkKey(new java.util.function.Consumer<KeyCheck.Result>() {
			@Override
			public void accept(KeyCheck.Result result) {
				keyMessage = ScoutCommands.describeLocal(result);
			}
		});
	}

	private void status(String key) {
		keyMessage = StatCollector.translateToLocal(key);
	}

	/** For the client game test, which drives the key field like a player would. */
	public void typeKeyForTest(String key) {
		if (keyBox != null) {
			keyBox.setText(key);
		}
		saveKey();
	}

	public String keyMessage() {
		return keyMessage;
	}

	/** For the client startup test: the general tab's "look up in the lobby" toggle, clicked exactly as a player's would be. */
	public GuiButton lobbyToggleForTest() {
		return lobbyToggle;
	}

	private void openKeyPage() {
		try {
			java.awt.Desktop.getDesktop().browse(new java.net.URI("https://developer.hypixel.net/dashboard"));
		} catch (Exception e) {
			// No desktop/browser available (a server, a headless test) — nothing sensible to do about it.
		}
	}

	// --- frame ----------------------------------------------------------------------------------

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();

		de.raindancer118.hypixelscout.ui.widget.Scissor.enable(0, contentTop, width, contentBottom - contentTop);

		for (String[] label : sectionLabels) {
			int y = Integer.parseInt(label[1]);
			if (y > contentTop - 12 && y < contentBottom) {
				ScoutTheme.textCentred(fontRendererObj, "§6" + StatCollector.translateToLocal(label[0]), width / 2,
						y + 6, ScoutTheme.accent());
			}
		}

		if (TAB_KEYS[selected].equals("general") && keyBox != null) {
			boolean unmasked = keyBox.isFocused();
			String real = keyBox.getText();
			if (!unmasked) {
				keyBox.setText(mask(real));
			}
			keyBox.drawTextBox();
			if (!unmasked) {
				keyBox.setText(real);
			}
			ScoutTheme.textCentred(fontRendererObj, keyMessage, width / 2, keyMessageBottom, ScoutTheme.TEXT);
		}

		if (TAB_KEYS[selected].equals("callouts")) {
			for (GuiTextField box : calloutBoxes) {
				box.drawTextBox();
			}
		}

		for (GuiButton button : rowButtons) {
			if (button.yPosition + 20 > contentTop && button.yPosition < contentBottom) {
				button.drawButton(mc, mouseX, mouseY);
			}
		}

		de.raindancer118.hypixelscout.ui.widget.Scissor.disable();

		tabBar.layout(0, 0, width);
		tabBar.draw(fontRendererObj, mouseX, mouseY);
		for (GuiButton button : buttonList) {
			if (!rowButtons.contains(button)) {
				button.drawButton(mc, mouseX, mouseY);
			}
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (tabBar.mouseClicked(mouseX, mouseY)) {
			initGui();
			return;
		}

		if (listening != null) {
			listening.bind(KeyBindButton.encodeMouseButton(mouseButton));
			listening = null;
			for (KeyBindButton button : keyButtons) {
				button.refresh();
			}
			return;
		}

		if (mouseButton == 0 && mouseY >= contentTop && mouseY < contentBottom) {
			if (keyBox != null) {
				keyBox.mouseClicked(mouseX, mouseY, mouseButton);
			}
			for (GuiTextField box : calloutBoxes) {
				box.mouseClicked(mouseX, mouseY, mouseButton);
			}
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
		int wheel = org.lwjgl.input.Mouse.getEventDWheel();
		if (wheel != 0) {
			int maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
			scroll = clamp(scroll - Integer.signum(wheel) * 16, 0, maxScroll);
			initGui();
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (listening != null) {
			listening.bind(keyCode);
			listening = null;
			for (KeyBindButton button : keyButtons) {
				button.refresh();
			}
			return;
		}

		if (tabBar.keyTyped(keyCode)) {
			initGui();
			return;
		}

		if (keyBox != null && keyBox.isFocused()) {
			if (keyBox.textboxKeyTyped(typedChar, keyCode)) {
				return;
			}
		}
		for (GuiTextField box : calloutBoxes) {
			if (box.isFocused() && box.textboxKeyTyped(typedChar, keyCode)) {
				syncCalloutText();
				return;
			}
		}

		super.keyTyped(typedChar, keyCode);
	}

	private void syncCalloutText() {
		ScoutSettings.Callouts callouts = settings().callouts;
		for (int i = 0; i < calloutBoxes.size(); i++) {
			callouts.messages[i] = calloutBoxes.get(i).getText();
		}
	}

	@Override
	public void updateScreen() {
		if (TAB_KEYS[selected].equals("callouts")) {
			syncCalloutText();
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void onGuiClosed() {
		String typed = keyBox == null ? settings().apiKey : keyBox.getText().trim();
		if (!typed.equals(settings().apiKey) && (typed.isEmpty() || isUuid(typed))) {
			mod.setApiKey(typed);
		} else {
			mod.saveSettings();
		}
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
