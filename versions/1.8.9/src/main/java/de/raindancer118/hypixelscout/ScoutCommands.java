package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.Chat;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.ClientCommandHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /scout} — the mod's whole surface from chat.
 *
 * <p>Client-side command: typing it never sends anything to the server ({@link
 * ClientCommandHandler}, not the server's own command dispatcher). 1.8.9 has no Brigadier, so the
 * argument tree 26.2 builds declaratively is dispatched here by hand, one {@code args[n]} at a
 * time — the same subcommand names and shape, just without the builder.
 *
 * <p>Subcommands whose only job in 26.2 is opening a screen ({@code game}/{@code teams}/{@code
 * lookup}/{@code queue}/{@code suspects}/bare {@code /scout}, {@code settings}, {@code move},
 * {@code table}, a bare player name) or that depend on {@code game.CheatSensor} ({@code cheats} and
 * its children) stay registered — so they tab-complete and are recognised, exactly what a later
 * phase needs when it wires the screens/cheats up — but answer with a plain "not available yet"
 * message instead of doing nothing silently. Everything else is fully implemented.
 */
public final class ScoutCommands extends CommandBase {
	private final HypixelScout mod;

	private ScoutCommands(HypixelScout mod) {
		this.mod = mod;
	}

	public static void register(HypixelScout mod) {
		ClientCommandHandler.instance.registerCommand(new ScoutCommands(mod));
	}

	@Override
	public String getCommandName() {
		return "scout";
	}

	@Override
	public List<String> getCommandAliases() {
		return Arrays.asList("hypixelscout");
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/scout [game|teams|lookup|queue|suspects|settings|move|table|party|team|requeue cancel"
				+ "|list team|list party|refresh|status|cheats|testkey|key <key>|<player>]";
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			notAvailableYet(sender, "the Scout screen");
			return;
		}

		String head = args[0].toLowerCase(Locale.ROOT);
		switch (head) {
			case "game":
			case "teams":
			case "lookup":
			case "queue":
			case "suspects":
				notAvailableYet(sender, "the Scout screen");
				return;
			case "settings":
				notAvailableYet(sender, "the settings screen");
				return;
			case "move":
				notAvailableYet(sender, "the table editor screen");
				return;
			case "table":
				notAvailableYet(sender, "the HUD table");
				return;
			case "party":
				mod.partyReport().send(PartyReport.Channel.PARTY);
				return;
			case "team":
				mod.partyReport().send(PartyReport.Channel.TEAM);
				return;
			case "requeue":
				requeue(sender, args);
				return;
			case "list":
				list(sender, args);
				return;
			case "refresh":
				mod.refresh();
				feedback(sender, translated("message.hypixelscout.refreshed"));
				return;
			case "status":
				status(sender);
				return;
			case "cheats":
				notAvailableYet(sender, "cheat detection");
				return;
			case "testkey":
				feedback(sender, translated("message.hypixelscout.key.checking"));
				mod.checkKey(result -> Chat.say(describe(result)));
				return;
			case "key":
				setKey(sender, args);
				return;
			default:
				player(sender, args);
		}
	}

	private void requeue(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2 || !"cancel".equalsIgnoreCase(args[1])) {
			throw new WrongUsageException("/scout requeue cancel");
		}
		feedback(sender, translated(mod.requeue().cancel()
				? "message.hypixelscout.requeue.cancelled" : "message.hypixelscout.requeue.nothing"));
	}

	private void list(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException("/scout list <team|party>");
		}
		if ("team".equalsIgnoreCase(args[1])) {
			mod.partyReport().sendAll(PartyReport.Channel.TEAM);
		} else if ("party".equalsIgnoreCase(args[1])) {
			mod.partyReport().sendAll(PartyReport.Channel.PARTY);
		} else {
			throw new WrongUsageException("/scout list <team|party>");
		}
	}

	private void status(ICommandSender sender) {
		HypixelClient client = mod.client();

		feedback(sender, translated("message.hypixelscout.status.key",
				translated(client.hasApiKey() ? "message.hypixelscout.status.set" : "message.hypixelscout.status.missing"),
				client.getLimiter().remaining(), client.getLimiter().limit()));

		IChatComponent gameState = mod.roster().isInGame()
				? translated("message.hypixelscout.status.in_game", mod.roster().members().size())
				: translated("message.hypixelscout.status.not_in_game");
		feedback(sender, translated("message.hypixelscout.status.game",
				translated(mod.isModApiPresent() ? "message.hypixelscout.status.connected" : "message.hypixelscout.status.missing"),
				gameState));

		String error = mod.stats().getLastError();
		if (error != null) {
			feedback(sender, translated("message.hypixelscout.status.error", error));
		}
	}

	private void setKey(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException("/scout key <key>");
		}
		String key = args[1].trim();

		try {
			UUID.fromString(key);
		} catch (IllegalArgumentException e) {
			// Hypixel keys are UUIDs; saying so here saves a round trip and a puzzled player.
			sender.addChatMessage(Chat.prefixed(translated("message.hypixelscout.key.malformed")));
			return;
		}

		mod.setApiKey(key);
		feedback(sender, translated("message.hypixelscout.key.saved"));
		mod.checkKey(result -> Chat.say(describe(result)));
	}

	/** A bare player name — opens the profile screen, a later phase — or {@code <player> team|party}. */
	private void player(ICommandSender sender, String[] args) throws CommandException {
		String name = args[0];

		if (args.length >= 2 && ("team".equalsIgnoreCase(args[1]) || "party".equalsIgnoreCase(args[1]))) {
			PartyReport.Channel channel = "team".equalsIgnoreCase(args[1]) ? PartyReport.Channel.TEAM
					: PartyReport.Channel.PARTY;
			UUID uuid = mod.roster().uuidOf(name);
			mod.partyReport().sendPlayer(channel, name, uuid == null ? null : mod.stats().peek(uuid));
			return;
		}

		notAvailableYet(sender, "the profile screen");
	}

	private void notAvailableYet(ICommandSender sender, String what) {
		sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "Not available yet in this build: "
				+ what + "."));
	}

	private void feedback(ICommandSender sender, IChatComponent message) {
		sender.addChatMessage(Chat.prefixed(message));
	}

	private static IChatComponent translated(String key, Object... args) {
		return new ChatComponentTranslation(key, args);
	}

	/** What a key check found, as one line. */
	static IChatComponent describe(KeyCheck.Result result) {
		String key = "message.hypixelscout.key.result." + result.outcome().name().toLowerCase(Locale.ROOT);
		return translated(key, result.detail());
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			List<String> options = new ArrayList<String>(Arrays.asList("game", "teams", "lookup", "queue",
					"suspects", "settings", "move", "table", "party", "team", "requeue", "list", "refresh",
					"status", "cheats", "testkey", "key"));
			for (Roster.Member member : mod.roster().members()) {
				options.add(member.name());
			}
			return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
		}

		if (args.length == 2) {
			String head = args[0].toLowerCase(Locale.ROOT);
			if ("requeue".equals(head)) {
				return getListOfStringsMatchingLastWord(args, "cancel");
			}
			if ("list".equals(head) || "cheats".equals(head)) {
				return getListOfStringsMatchingLastWord(args, "team", "party");
			}
			if (!isKnownSubcommand(head)) {
				// A player name in the first slot: the second slot is which channel to send to.
				return getListOfStringsMatchingLastWord(args, "team", "party");
			}
		}

		return super.addTabCompletionOptions(sender, args, pos);
	}

	private static boolean isKnownSubcommand(String head) {
		switch (head) {
			case "game": case "teams": case "lookup": case "queue": case "suspects": case "settings":
			case "move": case "table": case "party": case "team": case "requeue": case "list":
			case "refresh": case "status": case "cheats": case "testkey": case "key":
				return true;
			default:
				return false;
		}
	}
}
