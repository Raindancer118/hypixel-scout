package de.raindancer118.hypixelscout;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * {@code /scout} — the mod's whole surface from chat.
 *
 * <p>Client-side commands: typing them never sends anything to the server.
 */
public final class ScoutCommands {
	private ScoutCommands() {
	}

	public static void register(HypixelScout mod) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			var root = dispatcher.register(tree("scout", mod));
			dispatcher.register(ClientCommands.literal("hypixelscout").redirect(root)
					.executes(context -> openTab(mod, ScoutScreen.Page.GAME)));
		});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> tree(String name, HypixelScout mod) {
		return ClientCommands.literal(name)
				.executes(context -> openTab(mod, ScoutScreen.Page.GAME))
				.then(ClientCommands.literal("game").executes(context -> openTab(mod, ScoutScreen.Page.GAME)))
				.then(ClientCommands.literal("teams").executes(context -> openTab(mod, ScoutScreen.Page.TEAMS)))
				.then(ClientCommands.literal("lookup").executes(context -> openTab(mod, ScoutScreen.Page.LOOKUP)))
				.then(ClientCommands.literal("queue").executes(context -> openTab(mod, ScoutScreen.Page.QUEUE)))
				.then(ClientCommands.literal("suspects").executes(context -> openTab(mod, ScoutScreen.Page.CHEATS)))
				.then(ClientCommands.literal("settings").executes(context -> {
					HypixelScout.open(mod.settingsScreen(null));
					return 1;
				}))
				.then(ClientCommands.literal("move").executes(context -> {
					HypixelScout.open(mod.tableEditor(null));
					return 1;
				}))
				.then(ClientCommands.literal("table").executes(context -> {
					boolean open = mod.table().toggle();
					context.getSource().sendFeedback(Chat.prefixed(Component.translatable(
							open ? "message.hypixelscout.table.opened" : "message.hypixelscout.table.closed")));
					return 1;
				}))
				.then(ClientCommands.literal("party").executes(context -> {
					mod.partyReport().send(PartyReport.Channel.PARTY);
					return 1;
				}))
				.then(ClientCommands.literal("team").executes(context -> {
					mod.partyReport().send(PartyReport.Channel.TEAM);
					return 1;
				}))
				.then(ClientCommands.literal("requeue")
						.then(ClientCommands.literal("cancel").executes(context -> {
							context.getSource().sendFeedback(Chat.prefixed(Component.translatable(
									mod.requeue().cancel() ? "message.hypixelscout.requeue.cancelled"
											: "message.hypixelscout.requeue.nothing")));
							return 1;
						})))
				.then(ClientCommands.literal("list")
						.then(ClientCommands.literal("team").executes(context -> {
							mod.partyReport().sendAll(PartyReport.Channel.TEAM);
							return 1;
						}))
						.then(ClientCommands.literal("party").executes(context -> {
							mod.partyReport().sendAll(PartyReport.Channel.PARTY);
							return 1;
						})))
				.then(ClientCommands.literal("refresh").executes(context -> {
					mod.refresh();
					context.getSource().sendFeedback(Chat.prefixed(
							Component.translatable("message.hypixelscout.refreshed")));
					return 1;
				}))
				.then(ClientCommands.literal("status").executes(context -> status(context, mod)))
				.then(ClientCommands.literal("cheats").executes(context -> cheats(context, mod))
						.then(ClientCommands.literal("party").executes(context -> {
							mod.partyReport().sendCheats(PartyReport.Channel.PARTY, mod.cheats().suspicion());
							return 1;
						}))
						.then(ClientCommands.literal("team").executes(context -> {
							mod.partyReport().sendCheats(PartyReport.Channel.TEAM, mod.cheats().suspicion());
							return 1;
						}))
						.then(ClientCommands.literal("wrong")
								.then(ClientCommands.argument("player", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(flaggedPlayers(mod), builder))
										.executes(context -> verdict(context, mod, false, false))
										.then(ClientCommands.argument("check", StringArgumentType.word())
												.suggests((context, builder) -> SharedSuggestionProvider.suggest(checkNames(), builder))
												.executes(context -> verdict(context, mod, false, true)))))
						.then(ClientCommands.literal("right")
								.then(ClientCommands.argument("player", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(flaggedPlayers(mod), builder))
										.executes(context -> verdict(context, mod, true, false)))))
				.then(ClientCommands.literal("testkey").executes(context -> {
					context.getSource().sendFeedback(Chat.prefixed(
							Component.translatable("message.hypixelscout.key.checking")));
					mod.checkKey(result -> Chat.say(describe(result)));
					return 1;
				}))
				.then(ClientCommands.literal("key")
						.then(ClientCommands.argument("key", StringArgumentType.word())
								.executes(context -> setKey(context, mod))))
				.then(ClientCommands.argument("player", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								mod.roster().members().stream().map(Roster.Member::name), builder))
						.executes(context -> {
							String player = StringArgumentType.getString(context, "player");
							HypixelScout.open(mod.profileScreen(player, mod.roster().uuidOf(player), null));
							return 1;
						})
						.then(ClientCommands.literal("team").executes(context -> sendPlayer(context, mod,
								PartyReport.Channel.TEAM)))
						.then(ClientCommands.literal("party").executes(context -> sendPlayer(context, mod,
								PartyReport.Channel.PARTY))));
	}

	/** Somebody in the game straight into chat; anybody else has to be looked up first. */
	private static int sendPlayer(CommandContext<FabricClientCommandSource> context, HypixelScout mod,
			PartyReport.Channel channel) {
		String player = StringArgumentType.getString(context, "player");
		UUID uuid = mod.roster().uuidOf(player);
		mod.partyReport().sendPlayer(channel, player, uuid == null ? null : mod.stats().peek(uuid));
		return 1;
	}

	private static int openTab(HypixelScout mod, ScoutScreen.Page page) {
		ScoutScreen screen = mod.scoutScreen(null);
		screen.showPage(page);
		HypixelScout.open(screen);
		return 1;
	}

	private static int setKey(CommandContext<FabricClientCommandSource> context, HypixelScout mod) {
		String key = StringArgumentType.getString(context, "key").trim();

		try {
			UUID.fromString(key);
		} catch (IllegalArgumentException e) {
			// Hypixel keys are UUIDs; saying so here saves a round trip and a puzzled player.
			context.getSource().sendError(Chat.prefixed(Component.translatable("message.hypixelscout.key.malformed")));
			return 0;
		}

		mod.setApiKey(key);
		context.getSource().sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.key.saved")));
		mod.checkKey(result -> Chat.say(describe(result)));
		return 1;
	}

	private static int status(CommandContext<FabricClientCommandSource> context, HypixelScout mod) {
		HypixelClient client = mod.client();
		var source = context.getSource();

		source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.status.key",
				Component.translatable(client.hasApiKey() ? "message.hypixelscout.status.set"
						: "message.hypixelscout.status.missing"),
				client.getLimiter().remaining(), client.getLimiter().limit())));
		source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.status.game",
				Component.translatable(mod.isModApiPresent() ? "message.hypixelscout.status.connected"
						: "message.hypixelscout.status.missing"),
				mod.roster().isInGame()
						? Component.translatable("message.hypixelscout.status.in_game",
								mod.roster().members().size())
						: Component.translatable("message.hypixelscout.status.not_in_game"))));

		String error = mod.stats().getLastError();
		if (error != null) {
			source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.status.error", error)));
		}

		return 1;
	}

	/** Everybody flagged this round, one line per player with each check, how often and the latest evidence. */
	private static int cheats(CommandContext<FabricClientCommandSource> context, HypixelScout mod) {
		var source = context.getSource();
		if (!mod.settings().cheats.enabled) {
			source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.cheat.off")));
			return 1;
		}
		var flags = mod.cheats().suspicion().flagged();
		if (flags.isEmpty()) {
			source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.cheat.none")));
			return 1;
		}
		java.util.Map<String, java.util.List<de.raindancer118.cheatwatch.Suspicion.Flag>> byPlayer =
				new java.util.LinkedHashMap<>();
		for (var flag : flags) {
			byPlayer.computeIfAbsent(flag.player(), name -> new java.util.ArrayList<>()).add(flag);
		}
		java.util.List<String> players = new java.util.ArrayList<>(byPlayer.keySet());
		players.sort(java.util.Comparator.comparingDouble((String name) -> mod.cheats().confidence(name)).reversed());
		for (String player : players) {
			StringBuilder line = new StringBuilder("\u00a7c\u26a0 \u00a7f").append(player).append(" ")
					.append(de.raindancer118.hypixelscout.ui.Suspects.percent(mod.cheats().confidence(player)))
					.append("\u00a77:");
			for (var flag : byPlayer.get(player)) {
				line.append(" \u00a7c").append(flag.check().label()).append(" \u00a77\u00d7").append(flag.count())
						.append(" \u00a78(").append(flag.detail()).append(", ").append(flag.percent()).append("%)");
			}
			source.sendFeedback(Chat.prefixed(Component.literal(line.toString())));
		}
		source.sendFeedback(Chat.prefixed(de.raindancer118.hypixelscout.game.CheatSensor.reportLinks()));
		return 1;
	}

	private static java.util.stream.Stream<String> flaggedPlayers(HypixelScout mod) {
		return mod.cheats().suspicion().flagged().stream().map(flag -> flag.player()).distinct();
	}

	private static java.util.stream.Stream<String> checkNames() {
		return java.util.Arrays.stream(de.raindancer118.cheatwatch.Check.values())
				.map(check -> check.name().toLowerCase(Locale.ROOT));
	}

	/** {@code /scout cheats wrong <player> [check]} and {@code /scout cheats right <player>}. */
	private static int verdict(CommandContext<FabricClientCommandSource> context, HypixelScout mod, boolean cheating,
			boolean withCheck) {
		var source = context.getSource();
		String player = StringArgumentType.getString(context, "player");
		de.raindancer118.cheatwatch.Check check = null;
		if (withCheck) {
			String name = StringArgumentType.getString(context, "check");
			try {
				check = de.raindancer118.cheatwatch.Check.valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				source.sendError(Chat.prefixed(Component.translatable("message.hypixelscout.cheat.unknown_check", name)));
				return 0;
			}
		}

		var flags = mod.cheats().verdict(player, check, cheating);
		if (flags.isEmpty()) {
			source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.cheat.not_flagged", player)));
		} else {
			String checks = flags.stream().map(flag -> flag.check().label()).collect(java.util.stream.Collectors.joining(", "));
			source.sendFeedback(Chat.prefixed(Component.translatable(cheating
					? "message.hypixelscout.cheat.confirmed" : "message.hypixelscout.cheat.cleared", player, checks)));
		}
		if (!mod.settings().cheats.log) {
			source.sendFeedback(Chat.prefixed(Component.translatable("message.hypixelscout.cheat.not_logged")));
		}
		return 1;
	}

	/** What a key check found, as one line. Shared with the settings screen. */
	public static net.minecraft.network.chat.MutableComponent describe(KeyCheck.Result result) {
		String key = "message.hypixelscout.key.result." + result.outcome().name().toLowerCase(Locale.ROOT);
		return Component.translatable(key, result.detail());
	}
}
