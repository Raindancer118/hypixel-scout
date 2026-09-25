package de.raindancer118.hypixelscout;

import de.raindancer118.cheatwatch.Check;
import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.screen.ProfileScreen;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
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
 * <p>Every subcommand is fully implemented as of Phase 3 of this branch's port (see {@code
 * Project.md}): the ones whose only job in 26.2 is opening a screen ({@code game}/{@code teams}/
 * {@code lookup}/{@code queue}/{@code suspects}/bare {@code /scout}, {@code settings}, {@code move},
 * a bare player name) now do, on top of {@code cheats} and its children ({@code game.CheatSensor}'s
 * cheat-detection UI, Phase 2b) and {@code table} (Phase 2a, the HUD), which toggles
 * {@link HypixelScout#table()} exactly as 26.2's own {@code table} subcommand does.
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
			openScout(ScoutScreen.Page.GAME);
			return;
		}

		String head = args[0].toLowerCase(Locale.ROOT);
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
			case "suspects":
				openScout(ScoutScreen.Page.CHEATS);
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
				cheats(sender, args);
				return;
			case "telemetry":
				telemetry(sender, args);
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

	/**
	 * {@code /scout cheats} (list), {@code /scout cheats party|team} (report) and
	 * {@code /scout cheats wrong|right <player> [check]} (verdict). Ported from 26.2's own
	 * {@code cheats}/{@code verdict} command handlers, dispatched by hand the way every subcommand
	 * here is on 1.8.9's pre-Brigadier command API.
	 */
	private void cheats(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			listFlags(sender);
			return;
		}
		String sub = args[1].toLowerCase(Locale.ROOT);
		if ("party".equals(sub)) {
			mod.partyReport().sendCheats(PartyReport.Channel.PARTY, mod.cheats().suspicion());
		} else if ("team".equals(sub)) {
			mod.partyReport().sendCheats(PartyReport.Channel.TEAM, mod.cheats().suspicion());
		} else if ("wrong".equals(sub) || "right".equals(sub)) {
			verdict(sender, args, "right".equals(sub));
		} else {
			throw new WrongUsageException("/scout cheats [party|team|wrong <player> [check]|right <player>]");
		}
	}

	/** Everybody flagged this round, one line per player with each check, how often and the latest evidence. */
	private void listFlags(ICommandSender sender) {
		if (!mod.settings().cheats.enabled) {
			feedback(sender, translated("message.hypixelscout.cheat.off"));
			return;
		}
		List<Suspicion.Flag> flags = mod.cheats().suspicion().flagged();
		if (flags.isEmpty()) {
			feedback(sender, translated("message.hypixelscout.cheat.none"));
			return;
		}
		java.util.Map<String, List<Suspicion.Flag>> byPlayer = new java.util.LinkedHashMap<String, List<Suspicion.Flag>>();
		for (Suspicion.Flag flag : flags) {
			List<Suspicion.Flag> forPlayer = byPlayer.get(flag.player());
			if (forPlayer == null) {
				forPlayer = new ArrayList<Suspicion.Flag>();
				byPlayer.put(flag.player(), forPlayer);
			}
			forPlayer.add(flag);
		}
		List<String> players = new ArrayList<String>(byPlayer.keySet());
		java.util.Collections.sort(players, new java.util.Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				return Double.compare(mod.cheats().confidence(b), mod.cheats().confidence(a));
			}
		});
		for (String player : players) {
			StringBuilder line = new StringBuilder("§c⚠ §f").append(player).append(" ")
					.append(Suspects.percent(mod.cheats().confidence(player))).append("§7:");
			for (Suspicion.Flag flag : byPlayer.get(player)) {
				line.append(" §c").append(flag.check().label()).append(" §7×").append(flag.count())
						.append(" §8(").append(flag.detail()).append(", ").append(flag.percent()).append("%)");
			}
			feedback(sender, new ChatComponentText(line.toString()));
		}
		sender.addChatMessage(de.raindancer118.hypixelscout.game.CheatSensor.reportLinks());
	}

	/** {@code /scout cheats wrong <player> [check]} and {@code /scout cheats right <player>}. */
	private void verdict(ICommandSender sender, String[] args, boolean cheating) throws CommandException {
		if (args.length < 3) {
			throw new WrongUsageException("/scout cheats " + args[1] + " <player> [check]");
		}
		String player = args[2];
		Check check = null;
		if (args.length >= 4) {
			try {
				check = Check.valueOf(args[3].toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				feedback(sender, translated("message.hypixelscout.cheat.unknown_check", args[3]));
				return;
			}
		}

		List<Suspicion.Flag> flags = mod.cheats().verdict(player, check, cheating);
		if (flags.isEmpty()) {
			feedback(sender, translated("message.hypixelscout.cheat.not_flagged", player));
		} else {
			StringBuilder checks = new StringBuilder();
			for (Suspicion.Flag flag : flags) {
				if (checks.length() > 0) {
					checks.append(", ");
				}
				checks.append(flag.check().label());
			}
			feedback(sender, translated(cheating ? "message.hypixelscout.cheat.confirmed"
					: "message.hypixelscout.cheat.cleared", player, checks.toString()));
		}
		if (!mod.settings().cheats.log) {
			feedback(sender, translated("message.hypixelscout.cheat.not_logged"));
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

	/** {@code /scout telemetry [show|on|off]}: the switch, or its state and the file with exactly what goes out. */
	private void telemetry(ICommandSender sender, String[] args) {
		String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
		if ("on".equals(what) || "off".equals(what)) {
			boolean on = "on".equals(what);
			mod.settings().telemetry.enabled = on;
			mod.saveSettings();
			mod.telemetry().client().setEnabled(on);
			feedback(sender, translated(on ? "message.hypixelscout.telemetry.on" : "message.hypixelscout.telemetry.off"));
			return;
		}
		de.raindancer118.hypixelscout.config.ScoutSettings.Telemetry settings = mod.settings().telemetry;
		de.raindancer118.cheatwatch.telemetry.TelemetryClient.Stats stats = mod.telemetry().client().stats();
		String state = !settings.enabled ? "off" : settings.endpoint.isEmpty() ? "idle" : "on";
		feedback(sender, translated("message.hypixelscout.telemetry.state." + state, stats.sentBatches(),
				mod.telemetry().client().pending(), stats.droppedEvents()));
		java.nio.file.Path file = mod.telemetry().writePreview();
		if (file != null) {
			IChatComponent name = new net.minecraft.util.ChatComponentText(file.getFileName().toString());
			net.minecraft.util.ChatStyle style = new net.minecraft.util.ChatStyle();
			style.setUnderlined(true);
			style.setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.OPEN_FILE,
					file.toAbsolutePath().toString()));
			name.setChatStyle(style);
			feedback(sender, translated("message.hypixelscout.telemetry.preview", name));
		}
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
					"suspects", "settings", "move", "table", "party", "team", "requeue", "list", "refresh",
					"status", "cheats", "telemetry", "testkey", "key"));
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
			if ("list".equals(head)) {
				return getListOfStringsMatchingLastWord(args, "team", "party");
			}
			if ("cheats".equals(head)) {
				return getListOfStringsMatchingLastWord(args, "team", "party", "wrong", "right");
			}
			if ("telemetry".equals(head)) {
				return getListOfStringsMatchingLastWord(args, "show", "on", "off");
			}
			if (!isKnownSubcommand(head)) {
				// A player name in the first slot: the second slot is which channel to send to.
				return getListOfStringsMatchingLastWord(args, "team", "party");
			}
		}

		if (args.length == 3 && "cheats".equals(args[0].toLowerCase(Locale.ROOT))
				&& ("wrong".equalsIgnoreCase(args[1]) || "right".equalsIgnoreCase(args[1]))) {
			List<String> flagged = new ArrayList<String>();
			for (Suspicion.Flag flag : mod.cheats().suspicion().flagged()) {
				if (!flagged.contains(flag.player())) {
					flagged.add(flag.player());
				}
			}
			return getListOfStringsMatchingLastWord(args, flagged.toArray(new String[0]));
		}

		if (args.length == 4 && "cheats".equals(args[0].toLowerCase(Locale.ROOT)) && "wrong".equalsIgnoreCase(args[1])) {
			List<String> names = new ArrayList<String>();
			for (Check check : Check.values()) {
				names.add(check.name().toLowerCase(Locale.ROOT));
			}
			return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
		}

		return super.addTabCompletionOptions(sender, args, pos);
	}

	private static boolean isKnownSubcommand(String head) {
		switch (head) {
			case "game": case "teams": case "lookup": case "queue": case "suspects": case "settings":
			case "move": case "table": case "party": case "team": case "requeue": case "list":
			case "refresh": case "status": case "cheats": case "telemetry": case "testkey": case "key":
				return true;
			default:
				return false;
		}
	}
}
