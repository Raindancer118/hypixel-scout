package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.screen.ProfileScreen;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
import de.raindancer118.scout.api.ScoutApi;
import de.raindancer118.scout.forge.ui.ScoutCommand;
import net.minecraft.client.Minecraft;
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
 * <p>Scout, the bundled cheat detector, has subcommands of its own ({@code cheats}, {@code
 * suspects}, {@code options}, {@code hud}, {@code telemetry}). On 26.2 Brigadier merges its tree
 * with this one; here there is one {@code /scout} — this — which hands those to Scout's {@link
 * ScoutCommand} and keeps only the reports that need Hypixel's chats ({@code cheats party|team}).
 */
public final class ScoutCommands extends CommandBase {
	private static ScoutCommands instance;

	private final HypixelScout mod;

	private ScoutCommands(HypixelScout mod) {
		this.mod = mod;
	}

	public static void register(HypixelScout mod) {
		instance = new ScoutCommands(mod);
		ClientCommandHandler.instance.registerCommand(instance);
	}

	/** The registered {@code /scout}, {@code null} before {@link #register}. */
	public static ScoutCommands instance() {
		return instance;
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
		return "/scout [game|teams|lookup|queue|settings|move|table|party|team|requeue cancel"
				+ "|list team|list party|refresh|status|cheats party|cheats team|testkey|key <key>|<player>]"
				+ " · Scout: /scout [cheats|suspects|options|hud|telemetry]";
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			openScout(ScoutScreen.Page.GAME);
			return;
		}

		String head = args[0].toLowerCase(Locale.ROOT);
		if (isScouts(args)) {
			ScoutCommand.execute(sender, args);
			return;
		}
		switch (head) {
			case "game":
				openScout(ScoutScreen.Page.GAME);
				return;
			case "teams":
				openScout(ScoutScreen.Page.TEAMS);
				return;
			case "lookup":
				openScout(ScoutScreen.Page.LOOKUP);
				return;
			case "queue":
				openScout(ScoutScreen.Page.QUEUE);
				return;
			case "settings":
				Minecraft.getMinecraft().displayGuiScreen(mod.settingsScreen(null));
				return;
			case "move":
				Minecraft.getMinecraft().displayGuiScreen(mod.tableEditor(null));
				return;
			case "table":
				boolean open = mod.table().toggle();
				feedback(sender, translated(open ? "message.hypixelscout.table.opened"
						: "message.hypixelscout.table.closed"));
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
				cheatsReport(args);
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

	private void openScout(ScoutScreen.Page page) {
		Minecraft.getMinecraft().displayGuiScreen(new ScoutScreen(mod, null, page));
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

	/** Everything Scout answers: its subcommands, except the reports only this mod can send. */
	private static boolean isScouts(String[] args) {
		return args.length > 0 && ScoutCommand.handles(args) && !isCheatsReport(args);
	}

	private static boolean isCheatsReport(String[] args) {
		return args.length >= 2 && "cheats".equalsIgnoreCase(args[0])
				&& ("party".equalsIgnoreCase(args[1]) || "team".equalsIgnoreCase(args[1]));
	}

	/** {@code /scout cheats party|team}: everybody Scout flagged, into Hypixel's party or team chat. */
	private void cheatsReport(String[] args) {
		PartyReport.Channel channel = "party".equalsIgnoreCase(args[1]) ? PartyReport.Channel.PARTY : PartyReport.Channel.TEAM;
		mod.partyReport().sendCheats(channel, ScoutApi.get().suspicion());
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

	/** A bare player name — opens the profile screen — or {@code <player> team|party}. */
	private void player(ICommandSender sender, String[] args) throws CommandException {
		String name = args[0];

		if (args.length >= 2 && ("team".equalsIgnoreCase(args[1]) || "party".equalsIgnoreCase(args[1]))) {
			PartyReport.Channel channel = "team".equalsIgnoreCase(args[1]) ? PartyReport.Channel.TEAM
					: PartyReport.Channel.PARTY;
			UUID uuid = mod.roster().uuidOf(name);
			mod.partyReport().sendPlayer(channel, name, uuid == null ? null : mod.stats().peek(uuid));
			return;
		}

		UUID uuid = mod.roster().uuidOf(name);
		Minecraft.getMinecraft().displayGuiScreen(new ProfileScreen(mod, name, uuid, null));
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

	/** The same line as {@link #describe}, as a plain string — for {@code SettingsScreen}'s status line. */
	public static String describeLocal(KeyCheck.Result result) {
		String key = "message.hypixelscout.key.result." + result.outcome().name().toLowerCase(Locale.ROOT);
		return StatCollector.translateToLocalFormatted(key, result.detail());
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			List<String> options = new ArrayList<String>(Arrays.asList("game", "teams", "lookup", "queue",
					"settings", "move", "table", "party", "team", "requeue", "list", "refresh",
					"status", "cheats", "testkey", "key"));
			for (String scouts : ScoutCommand.tabComplete(args)) {
				if (!options.contains(scouts)) {
					options.add(scouts);
				}
			}
			for (Roster.Member member : mod.roster().members()) {
				options.add(member.name());
			}
			return getListOfStringsMatchingLastWord(args, options.toArray(new String[0]));
		}

		String head = args[0].toLowerCase(Locale.ROOT);
		if (ScoutCommand.handles(args) || "cheats".equals(head)) {
			List<String> options = new ArrayList<String>(ScoutCommand.tabComplete(args));
			if (args.length == 2 && "cheats".equals(head)) {
				for (String report : getListOfStringsMatchingLastWord(args, "party", "team")) {
					if (!options.contains(report)) {
						options.add(report);
					}
				}
			}
			return options;
		}

		if (args.length == 2) {
			if ("requeue".equals(head)) {
				return getListOfStringsMatchingLastWord(args, "cancel");
			}
			if ("list".equals(head)) {
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
			case "refresh": case "status": case "cheats": case "telemetry": case "testkey": case "key":
			case "options": case "hud":
				return true;
			default:
				return false;
		}
	}
}
