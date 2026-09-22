package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /scout} — the mod's whole surface from chat.
 *
 * <p>Client side: registered with Forge's client command handler, so nothing is ever sent to the
 * server by typing it.
 */
public final class ScoutCommand extends CommandBase {
	private final HypixelScout mod;

	public ScoutCommand(HypixelScout mod) {
		this.mod = mod;
	}

	@Override
	public String getCommandName() {
		return "scout";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/scout <player> | lobby | config | key <key> | party | table | status | reload";
	}

	@Override
	public List<String> getCommandAliases() {
		return Arrays.asList("hypixelscout", "bw");
	}

	@Override
	public int getRequiredPermissionLevel() {
		// A client command: the server is never asked, so it needs no permission from one.
		return 0;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if (args.length == 0) {
			open(null, null);
			return;
		}

		String first = args[0].toLowerCase(java.util.Locale.ROOT);

		if ("key".equals(first)) {
			setKey(args);
			return;
		}
		if ("config".equals(first) || "settings".equals(first)) {
			openSettings();
			return;
		}
		if ("lobby".equals(first) || "list".equals(first)) {
			openLobby();
			return;
		}
		if ("party".equals(first)) {
			party();
			return;
		}
		if ("table".equals(first)) {
			mod.getConfig().setTableEnabled(!mod.getConfig().isTableEnabled());
			say("Table " + (mod.getConfig().isTableEnabled() ? "on" : "off") + ".");
			return;
		}
		if ("status".equals(first)) {
			status();
			return;
		}
		if ("reload".equals(first)) {
			mod.getConfig().load();
			mod.getStats().invalidate();
			say("Settings reloaded, cache cleared.");
			return;
		}

		// Anything else is a player name, which is the common case and so needs no keyword.
		open(args[0], mod.getRoster().uuidOf(args[0]));
	}

	private void setKey(String[] args) {
		if (args.length < 2) {
			say("§cUsage: /scout key <key> §7- get one at developer.hypixel.net");
			return;
		}

		String key = args[1].trim();
		try {
			UUID.fromString(key);
		} catch (IllegalArgumentException e) {
			say("§cThat does not look like an API key. It is a UUID, from "
					+ "developer.hypixel.net.");
			return;
		}

		mod.getConfig().setApiKey(key);
		mod.getClient().setApiKey(key);
		mod.getStats().invalidate();
		say("Key saved. Everything will be looked up again with it.");
	}

	private void party() {
		if (!mod.getRoster().isInBedwars()) {
			say("§cNot in a Bedwars game.");
			return;
		}

		List<String> messages = mod.getPartyReport().send();
		if (messages.isEmpty()) {
			say("§cNo enemy teams to report on yet.");
			return;
		}

		say("Sending " + messages.size() + " line" + (messages.size() == 1 ? "" : "s")
				+ " to party chat…");
	}

	private void status() {
		HypixelClient client = mod.getClient();

		say("Key " + (client.hasApiKey() ? "§aset" : "§cmissing")
				+ "§7, " + client.getLimiter().remaining() + " requests left this window.");
		say("Mod API " + (mod.isModApiPresent() ? "§aconnected" : "§cmissing")
				+ "§7, " + (mod.getRoster().isInBedwars()
						? "in a Bedwars game with " + mod.getRoster().members().size() + " players"
						: "not in a Bedwars game") + ".");

		String error = mod.getStats().getLastError();
		if (error != null) {
			say("§cLast error: §7" + error);
		}
	}

	/**
	 * Opens the profile screen on the next tick. Doing it here would put the screen up while
	 * Minecraft is still closing the chat that ran the command, and the chat would win.
	 */
	private void open(final String name, final UUID uuid) {
		final Minecraft mc = Minecraft.getMinecraft();
		final StatsService stats = mod.getStats();
		final MojangClient mojang = mod.getMojang();

		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				mc.displayGuiScreen(new ProfileScreen(stats, mojang, name, uuid));
			}
		});
	}

	/**
	 * The same settings screen the Mods menu opens, without going through the Mods menu. Its
	 * parent is null, so Done closes it back into the game.
	 */
	private void openSettings() {
		final Minecraft mc = Minecraft.getMinecraft();

		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				mc.displayGuiScreen(new ScoutGuiFactory.ScoutConfigScreen(null));
			}
		});
	}

	/** The lobby list, opened on the next tick for the same reason the profile screen is. */
	private void openLobby() {
		final Minecraft mc = Minecraft.getMinecraft();

		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				mc.displayGuiScreen(new LobbyScreen(mod.getStats(), mod.getMojang(),
						mod.getRoster(), mod.getConfig()));
			}
		});
	}

	private void say(String message) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null) {
			return;
		}

		IChatComponent line = new ChatComponentText("§6[Scout] §r§7" + message);
		mc.thePlayer.addChatMessage(line);
	}
}
