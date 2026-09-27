package de.raindancer118.hypixelscout.startup;

import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.ClientCommandHandler;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 1's own proof, beyond "the client boots": {@code /scout} is a real, working client command,
 * the key bindings it and {@link de.raindancer118.hypixelscout.ScoutKeys} register actually reached
 * the game's key-bind registry, the settings file gets written to disk, and the language file has
 * every string this test exercised.
 *
 * <p>Runs as a single tick once {@link HypixelScout#get()} exists (guaranteed by the time the main
 * menu screenshot step before this one in the runner's list has already passed — {@code FMLInitializationEvent}
 * fires long before the main menu screen does). There is no singleplayer world in this test, so the
 * commands below are run through a {@link TestCommandSender} rather than the real player — every one
 * of them only touches mod state ({@code HypixelScout}'s settings/roster/client), never the world.
 */
public final class ScoutFeatureCheck implements StartupCheck {

	@Override
	public String name() {
		return "scout-commands-keys-settings-lang";
	}

	@Override
	public boolean tick() throws Exception {
		HypixelScout mod = HypixelScout.get();
		if (mod == null) {
			throw new IllegalStateException("HypixelScout.get() is null — onInit never ran");
		}

		checkKeysRegistered();
		checkCommands(mod);
		checkSettingsFileWritten(mod);
		checkLanguageKeys();

		return true;
	}

	/**
	 * Every key {@code ScoutKeys.register()} handed to {@code ClientRegistry} actually reached
	 * {@code GameSettings.keyBindings} — the array the controls screen reads and {@code
	 * KeyBinding.onTick} polls, so this is the one place that is actually "registered" in any way
	 * that matters at runtime. ({@code KeyBinding.getKeybinds()} is a common-looking but wrong
	 * pick here: it returns the set of *category* names seen so far, not individual bindings.)
	 */
	private void checkKeysRegistered() {
		List<String> registered = new java.util.ArrayList<String>();
		for (KeyBinding binding : Minecraft.getMinecraft().gameSettings.keyBindings) {
			registered.add(binding.getKeyDescription());
		}

		List<String> expected = Arrays.asList("key.hypixelscout.open", "key.hypixelscout.table",
				"key.hypixelscout.peek", "key.hypixelscout.refresh", "key.hypixelscout.queue_1",
				"key.hypixelscout.queue_random", "key.hypixelscout.callout_1");

		for (String key : expected) {
			if (!registered.contains(key)) {
				throw new IllegalStateException("key binding not registered: " + key
						+ " (registered: " + registered + ")");
			}
		}
	}

	/** {@code /scout} and a handful of its subcommands, through the real client command handler. */
	private void checkCommands(HypixelScout mod) {
		if (ClientCommandHandler.instance.getCommands().get("scout") == null) {
			throw new IllegalStateException("/scout is not registered with ClientCommandHandler");
		}

		TestCommandSender sender = new TestCommandSender();

		// /scout (bare) now really opens the mod's own screen (Phase 3 of this branch's port, see
		// Project.md) — closed straight back to the main menu so the world-entry step after this one
		// still finds the screen it expects (none).
		int result = ClientCommandHandler.instance.executeCommand(sender, "/scout");
		Minecraft client = Minecraft.getMinecraft();
		if (result == 0 || !(client.currentScreen instanceof de.raindancer118.hypixelscout.ui.screen.ScoutScreen)) {
			throw new IllegalStateException("/scout (bare) should open ScoutScreen, current screen is "
					+ (client.currentScreen == null ? "null" : client.currentScreen.getClass().getSimpleName()));
		}
		if (((de.raindancer118.hypixelscout.ui.screen.ScoutScreen) client.currentScreen).page()
				!= de.raindancer118.hypixelscout.ui.screen.ScoutScreen.Page.GAME) {
			throw new IllegalStateException("/scout (bare) should open on the GAME page");
		}
		client.displayGuiScreen(null);

		sender.messages().clear();
		ClientCommandHandler.instance.executeCommand(sender, "/scout status");
		if (sender.messages().size() < 2) {
			throw new IllegalStateException("/scout status produced too little feedback: " + sender.messages());
		}

		sender.messages().clear();
		ClientCommandHandler.instance.executeCommand(sender, "/scout key not-a-uuid");
		if (sender.messages().isEmpty()) {
			throw new IllegalStateException("/scout key <malformed> produced no feedback");
		}

		checkScoutsSubcommands(sender, client);
	}

	/**
	 * Scout, bundled in, has its own {@code /scout} subcommands; there is only one {@code /scout}
	 * (this mod's — 1.8.9 has no Brigadier to merge two), which hands them on.
	 */
	private void checkScoutsSubcommands(TestCommandSender sender, Minecraft client) {
		if (ClientCommandHandler.instance.getCommands().get("scout") != de.raindancer118.hypixelscout.ScoutCommands.instance()) {
			throw new IllegalStateException("/scout is not this mod's command but "
					+ ClientCommandHandler.instance.getCommands().get("scout"));
		}

		// No game yet, so nobody is flagged — but the answer is Scout's.
		for (String command : new String[] {"/scout cheats", "/scout telemetry show"}) {
			sender.messages().clear();
			int result = ClientCommandHandler.instance.executeCommand(sender, command);
			if (result == 0 || sender.messages().isEmpty() || !sender.messages().get(0).contains("[Scout]")) {
				throw new IllegalStateException(command + " should be answered by Scout, got: " + sender.messages());
			}
		}

		ClientCommandHandler.instance.executeCommand(sender, "/scout options");
		if (!(client.currentScreen instanceof de.raindancer118.scout.forge.ui.screen.SettingsScreen)) {
			throw new IllegalStateException("/scout options should open Scout's settings, current screen is "
					+ (client.currentScreen == null ? "null" : client.currentScreen.getClass().getSimpleName()));
		}
		client.displayGuiScreen(null);
		ClientCommandHandler.instance.executeCommand(sender, "/scout suspects");
		if (!(client.currentScreen instanceof de.raindancer118.scout.forge.ui.screen.SuspectsScreen)) {
			throw new IllegalStateException("/scout suspects should open Scout's suspects screen, current screen is "
					+ (client.currentScreen == null ? "null" : client.currentScreen.getClass().getSimpleName()));
		}
		client.displayGuiScreen(null);

		java.util.List<String> offered = de.raindancer118.hypixelscout.ScoutCommands.instance()
				.addTabCompletionOptions(sender, new String[] {""}, null);
		for (String expected : new String[] {"game", "cheats", "options", "hud", "telemetry", "suspects"}) {
			if (!offered.contains(expected)) {
				throw new IllegalStateException("tab completion lacks " + expected + ": " + offered);
			}
		}
		java.util.List<String> cheats = de.raindancer118.hypixelscout.ScoutCommands.instance()
				.addTabCompletionOptions(sender, new String[] {"cheats", ""}, null);
		for (String expected : new String[] {"party", "team", "wrong", "right"}) {
			if (!cheats.contains(expected)) {
				throw new IllegalStateException("tab completion of /scout cheats lacks " + expected + ": " + cheats);
			}
		}
	}

	/** {@code HypixelScout.saveSettings()} actually writes {@code config/hypixelscout.json}. */
	private void checkSettingsFileWritten(HypixelScout mod) {
		File configFile = new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "hypixelscout.json");
		mod.saveSettings();

		if (!configFile.isFile()) {
			throw new IllegalStateException("settings file was not written to " + configFile);
		}
		if (configFile.length() == 0) {
			throw new IllegalStateException("settings file at " + configFile + " is empty");
		}
	}

	/** Every string key this test itself touched actually resolves to real text, not the raw key. */
	private void checkLanguageKeys() {
		String[] keys = {
				"message.hypixelscout.refreshed",
				"message.hypixelscout.status.key",
				"message.hypixelscout.status.set",
				"message.hypixelscout.status.missing",
				"message.hypixelscout.status.game",
				"message.hypixelscout.key.malformed",
				"key.hypixelscout.open",
				"key.categories.hypixelscout",
				// Scout's, from its bundle inside this jar.
				"message.scout.suspects.wrong",
				"message.hypixelscout.settings.cheats.scout",
				"message.hypixelscout.suspects.profile",
		};

		for (String key : keys) {
			if (!StatCollector.canTranslate(key)) {
				throw new IllegalStateException("language file is missing key: " + key);
			}
			String translated = StatCollector.translateToLocal(key);
			if (translated.equals(key)) {
				throw new IllegalStateException("key resolved to itself, not real text: " + key);
			}
		}
	}
}
