package de.raindancer118.hypixelscout.startup;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.mc.ScoutGuiFactory;
import de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen;
import de.raindancer118.hypixelscout.ui.hud.TableEditorScreen;
import de.raindancer118.hypixelscout.ui.screen.CardsScreen;
import de.raindancer118.hypixelscout.ui.screen.CheatChecksScreen;
import de.raindancer118.hypixelscout.ui.screen.ProfileScreen;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
import de.raindancer118.hypixelscout.ui.screen.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Phase 3 of this branch's port (see {@code Project.md}): opens every screen this module has and
 * screenshots it — proof that each one actually builds a valid layout at the client's real
 * resolution and does not throw. Runs last, after {@link WorldAndCheatsCheck}, so the roster
 * ({@link HudSetupCheck}'s fake round) and the cheat flag it raises (the bed-nuker) are both
 * already in place — real data for the game list, the profile, and the suspects list rather than
 * empty ones.
 *
 * <p>A small tick-driven state machine, the same pattern every other step in this package uses:
 * open a screen, hold a few frames so it has actually rendered once, screenshot, move on. The
 * settings-persistence check (one setting flipped through the real widget's own click handler,
 * then read back from {@code config/hypixelscout.json}) rides along on the General tab's own pass.
 */
public final class ScreensCheck implements StartupCheck {
	private static final int HOLD = 3;

	private enum Phase {
		SCOUT_PAGES, PROFILE, SETTINGS_TABS, SETTINGS_PERSIST, CARDS, CHEAT_CHECKS, TABLE_EDITOR,
		SUSPECTS_EDITOR, CONFIG_GUI, DONE
	}

	private Phase phase = Phase.SCOUT_PAGES;
	private int ticksInPhase;
	private int scoutPageIndex;
	private int settingsTabIndex;
	private SettingsScreen persistScreen;

	@Override
	public String name() {
		return "screens";
	}

	@Override
	public boolean tick() throws Exception {
		HypixelScout mod = HypixelScout.get();
		Minecraft client = Minecraft.getMinecraft();
		ticksInPhase++;

		switch (phase) {
			case SCOUT_PAGES:
				return tickScoutPages(mod, client);

			case PROFILE:
				if (ticksInPhase == 1) {
					client.displayGuiScreen(new ProfileScreen(mod, "Brickmason", mod.roster().uuidOf("Brickmason"), null));
				}
				if (ticksInPhase < HOLD) {
					return false;
				}
				expect(client, ProfileScreen.class, "hypixelscout-screen-profile.png");
				return advance();

			case SETTINGS_TABS:
				return tickSettingsTabs(mod, client);

			case SETTINGS_PERSIST:
				return tickSettingsPersist(client);

			case CARDS:
				if (ticksInPhase == 1) {
					client.displayGuiScreen(new CardsScreen(mod, null));
				}
				if (ticksInPhase < HOLD) {
					return false;
				}
				expect(client, CardsScreen.class, "hypixelscout-screen-cards.png");
				return advance();

			case CHEAT_CHECKS:
				if (ticksInPhase == 1) {
					client.displayGuiScreen(new CheatChecksScreen(mod, null));
				}
				if (ticksInPhase < HOLD) {
					return false;
				}
				expect(client, CheatChecksScreen.class, "hypixelscout-screen-cheat-checks.png");
				return advance();

			case TABLE_EDITOR:
				if (ticksInPhase == 1) {
					client.displayGuiScreen(mod.tableEditor(null));
				}
				if (ticksInPhase < HOLD) {
					return false;
				}
				expect(client, TableEditorScreen.class, "hypixelscout-screen-table-editor.png");
				return advance();

			case SUSPECTS_EDITOR:
				if (ticksInPhase == 1) {
					client.displayGuiScreen(mod.suspectsEditor(null));
				}
				if (ticksInPhase < HOLD) {
					return false;
				}
				expect(client, SuspectsEditorScreen.class, "hypixelscout-screen-suspects-editor.png");
				return advance();

			case CONFIG_GUI:
				if (ticksInPhase == 1) {
					// The exact path "Mod Options" takes: Forge's mod list constructs this class through
					// reflection and displays it; its own initGui() (not this test) swaps in the real
					// SettingsScreen — proving the factory wiring, not just SettingsScreen a second time.
					client.displayGuiScreen(new ScoutGuiFactory.ScoutConfigScreen(null));
				}
				if (ticksInPhase < HOLD) {
					return false;
				}
				expect(client, SettingsScreen.class, "hypixelscout-screen-mod-options.png");
				return advance();

			default:
				return true;
		}
	}

	private boolean tickScoutPages(HypixelScout mod, Minecraft client) throws IOException {
		ScoutScreen.Page[] pages = ScoutScreen.Page.values();
		if (scoutPageIndex >= pages.length) {
			return advance();
		}

		if (ticksInPhase == 1) {
			client.displayGuiScreen(new ScoutScreen(mod, null, pages[scoutPageIndex]));
		}
		if (ticksInPhase < HOLD) {
			return false;
		}

		ScoutScreen screen = expect(client, ScoutScreen.class,
				"hypixelscout-screen-scout-" + pages[scoutPageIndex].name().toLowerCase(java.util.Locale.ROOT) + ".png");
		if (screen.page() != pages[scoutPageIndex]) {
			throw new IllegalStateException("ScoutScreen opened on " + screen.page() + ", expected " + pages[scoutPageIndex]);
		}

		scoutPageIndex++;
		ticksInPhase = 0;
		return false;
	}

	private boolean tickSettingsTabs(HypixelScout mod, Minecraft client) throws IOException {
		int tabCount = 9;
		if (settingsTabIndex >= tabCount) {
			return advance();
		}

		if (ticksInPhase == 1) {
			client.displayGuiScreen(new SettingsScreen(mod, null).onTab(settingsTabIndex));
		}
		if (ticksInPhase < HOLD) {
			return false;
		}

		String[] tabNames = {"general", "table", "overlays", "alerts", "projectiles", "awareness", "cheats",
				"callouts", "keys"};
		expect(client, SettingsScreen.class, "hypixelscout-screen-settings-" + tabNames[settingsTabIndex] + ".png");

		settingsTabIndex++;
		ticksInPhase = 0;
		return false;
	}

	private boolean tickSettingsPersist(Minecraft client) throws IOException {
		HypixelScout mod = HypixelScout.get();

		if (ticksInPhase == 1) {
			// A known starting value: config/hypixelscout.json survives between runs of this same
			// build/run directory (only the world is fresh every time, see WorldEntryCheck), so a
			// previous run's own click here could otherwise leave this already flipped on.
			mod.settings().lookUpInLobby = false;
			persistScreen = new SettingsScreen(mod, null).onTab(0);
			client.displayGuiScreen(persistScreen);
			return false;
		}
		if (ticksInPhase == HOLD) {
			// Exactly the widget a player's mouse click would fire: CycleButton.onClick() advances the
			// value and calls the setter it was built with, precisely as GuiScreen.mouseClicked's own
			// "mousePressed() then actionPerformed()" dispatch would.
			((de.raindancer118.hypixelscout.ui.widget.Clickable) persistScreen.lobbyToggleForTest()).onClick();
			if (!mod.settings().lookUpInLobby) {
				throw new IllegalStateException("clicking the lobby toggle did not flip ScoutSettings.lookUpInLobby");
			}
			return false;
		}
		if (ticksInPhase < HOLD + 2) {
			return false;
		}

		// Closing the screen the way a player closing it with Escape/Done would: GuiScreen swap calls
		// the old screen's onGuiClosed(), which is where SettingsScreen actually writes the file.
		client.displayGuiScreen(null);
		File configFile = new File(new File(client.mcDataDir, "config"), "hypixelscout.json");
		String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
		if (!json.contains("\"lookUpInLobby\": true")) {
			throw new IllegalStateException("config/hypixelscout.json does not have lookUpInLobby: true after "
					+ "closing the settings screen; content: " + json);
		}

		// Left flipped on for the rest of the test is harmless; nothing after this reads it.
		return advance();
	}

	private boolean advance() {
		Phase[] values = Phase.values();
		phase = values[phase.ordinal() + 1];
		ticksInPhase = 0;
		return phase == Phase.DONE;
	}

	@SuppressWarnings("unchecked")
	private static <T> T expect(Minecraft client, Class<T> type, String filename) throws IOException {
		if (!type.isInstance(client.currentScreen)) {
			throw new IllegalStateException("expected " + type.getSimpleName() + " to be open for " + filename
					+ ", got " + (client.currentScreen == null ? "null" : client.currentScreen.getClass().getSimpleName()));
		}
		screenshot(client, filename);
		return (T) client.currentScreen;
	}

	private static void screenshot(Minecraft client, String filename) throws IOException {
		ScreenShotHelper.saveScreenshot(client.mcDataDir, filename, client.displayWidth, client.displayHeight,
				client.getFramebuffer());
		File screenshotsDir = new File(client.mcDataDir, "screenshots");
		if (!new File(screenshotsDir, filename).isFile()) {
			throw new IOException("no screenshot appeared at " + new File(screenshotsDir, filename));
		}
	}
}
