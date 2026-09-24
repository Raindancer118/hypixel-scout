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

		int result = ClientCommandHandler.instance.executeCommand(sender, "/scout");
		if (result == 0 || sender.messages().isEmpty()) {
			throw new IllegalStateException("/scout (bare) produced no feedback: " + sender.messages());
		}
		if (!sender.messages().get(0).contains("Not available yet")) {
			throw new IllegalStateException("/scout (bare) should say the screen is not available yet, said: "
					+ sender.messages());
		}

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

		sender.messages().clear();
		int cheatsResult = ClientCommandHandler.instance.executeCommand(sender, "/scout cheats");
		if (cheatsResult == 0 || sender.messages().isEmpty()
				|| !sender.messages().get(0).contains("Not available yet")) {
			throw new IllegalStateException("/scout cheats should be a graceful not-available-yet "
					+ "placeholder (cheat detection is a later phase), got: " + sender.messages());
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
