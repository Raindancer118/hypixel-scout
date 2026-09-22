package de.raindancer118.hypixelscout.gametest;

import com.mojang.brigadier.tree.CommandNode;
import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.compat.ModMenuIntegration;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.TabListReader;
import de.raindancer118.hypixelscout.ui.screen.ProfileScreen;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
import de.raindancer118.hypixelscout.ui.screen.SettingsScreen;
import de.raindancer118.hypixelscout.ui.hud.TableEditorScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Boots a real client, joins a world, stages a sixteen-player Bedwars game and drives every
 * surface of the mod — failing the build if any of it does not come up.
 *
 * <p>The stats come from {@link HypixelStub}, so the whole path from the tab list through the HTTP
 * client, the parser and the cache to the screen is the real one. Each surface is photographed into
 * {@code build/run/clientGameTest/screenshots} for a human to look at.
 */
public class HypixelScoutStartupTest implements FabricClientGameTest {
	private static final List<String> EXPECTED_KEYS = List.of(
			"key.hypixelscout.open", "key.hypixelscout.table", "key.hypixelscout.peek", "key.hypixelscout.settings",
			"key.hypixelscout.move_table", "key.hypixelscout.profile_target", "key.hypixelscout.party_report",
			"key.hypixelscout.queue_1", "key.hypixelscout.queue_9", "key.hypixelscout.queue_random");

	private static final List<String> EXPECTED_SUBCOMMANDS = List.of(
			"game", "teams", "lookup", "queue", "settings", "move", "table", "party", "refresh", "status",
			"testkey", "key", "player");

	private record Seat(String team, HypixelStub.Player player) {
	}

	private static final List<Seat> GAME = List.of(
			new Seat("red", new HypixelStub.Player("Ashenvale", 1123, 24_310, 3_020, 4_820, 1_310, 38, "MVP_PLUS", false)),
			new Seat("red", new HypixelStub.Player("quietfox", 212, 2_120, 1_700, 610, 540, null, "VIP_PLUS", false)),
			new Seat("red", new HypixelStub.Player("Kettle_Drum", 88, 400, 390, 120, 150, 2, null, false)),
			new Seat("blue", new HypixelStub.Player("Brickmason", 488, 6_900, 1_150, 1_730, 690, 12, "MVP_PLUS", false)),
			new Seat("blue", new HypixelStub.Player("Nimbus_07", 64, 180, 310, 55, 120, 0, null, false)),
			new Seat("blue", new HypixelStub.Player("Wavecrest", 356, 4_100, 1_600, 1_200, 700, 7, "MVP", false)),
			new Seat("blue", new HypixelStub.Player("Glimmer", 0, 0, 0, 0, 0, null, null, true)),
			new Seat("green", new HypixelStub.Player("Lanternfish", 731, 15_200, 1_380, 3_120, 890, 61, "MVP_PLUS", false)),
			new Seat("green", new HypixelStub.Player("mossy", 17, 30, 70, 8, 30, 1, null, false)),
			new Seat("green", new HypixelStub.Player("Thornback", 402, 5_300, 1_900, 1_500, 800, null, "VIP", false)),
			new Seat("green", new HypixelStub.Player("Cinderling", 150, 1_200, 900, 400, 380, 3, "VIP_PLUS", false)),
			new Seat("yellow", new HypixelStub.Player("Orchard", 305, 3_900, 2_450, 980, 760, 4, "MVP", false)),
			new Seat("yellow", new HypixelStub.Player("Sundial", 1502, 40_100, 2_900, 7_900, 1_500, 104, "MVP_PLUS", false)),
			new Seat("yellow", new HypixelStub.Player("PaperCrane", 44, 90, 160, 30, 60, 0, null, false)),
			new Seat("yellow", new HypixelStub.Player("Driftwood", 260, 2_600, 2_000, 700, 650, null, null, false)));

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitTick();

		context.runOnClient(client -> {
			if (HypixelScout.get() == null) {
				throw new AssertionError("Client initialiser did not run");
			}
			assertKeyMappingsRegistered(client);
			if (!(new ModMenuIntegration().getModConfigScreenFactory().create(null) instanceof SettingsScreen)) {
				throw new AssertionError("Mod Menu does not open the settings screen");
			}
		});

		try (HypixelStub stub = new HypixelStub();
				TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			HypixelScout mod = HypixelScout.get();
			mod.client().setBaseUrl(stub.url());
			mod.mojang().setBaseUrl(stub.url());

			List<Roster.Member> members = new ArrayList<>();
			for (Seat seat : GAME) {
				UUID uuid = UUID.nameUUIDFromBytes(("scout-test:" + seat.player().name()).getBytes());
				stub.add(uuid, seat.player());
				members.add(new Roster.Member(seat.player().name(), uuid));
			}
			stub.add(UUID.nameUUIDFromBytes("scout-test:Technoblade".getBytes()),
					new HypixelStub.Player("Technoblade", 1799, 55_000, 1_800, 9_200, 1_100, 212, "MVP_PLUS", false));

			context.waitTick();
			context.runOnClient(client -> assertCommandRegistered());

			// Settings first, the way a player would: no key → the table says so.
			context.runOnClient(client -> {
				mod.settings().table.mode = HudMode.TOGGLE;
				mod.setApiKey("");
				members.add(new Roster.Member(client.player.getScoreboardName(), client.player.getUUID()));
				stub.add(client.player.getUUID(), new HypixelStub.Player(client.player.getScoreboardName(),
						333, 3_000, 1_000, 900, 400, 9, "MVP", false));
				TabListReader.replaceForTest(() -> members);
				mod.startGameForTest("BEDWARS_FOUR_FOUR", "Lighthouse");
				mod.table().toggle();
			});
			context.waitTicks(5);
			context.takeScreenshot("scout_table_no_key");

			context.setScreen(() -> mod.scoutScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("scout_game_no_key");

			context.setScreen(() -> mod.settingsScreen(null));
			context.waitTicks(3);
			context.runOnClient(client -> ((SettingsScreen) client.gui.screen()).typeKeyForTest("not a key"));
			context.waitTicks(2);
			context.takeScreenshot("scout_settings_bad_key");
			context.runOnClient(client -> ((SettingsScreen) client.gui.screen()).typeKeyForTest(HypixelStub.KEY));

			AtomicReference<KeyCheck.Result> check = new AtomicReference<>();
			context.runOnClient(client -> mod.checkKey(check::set));
			context.waitFor(client -> check.get() != null);
			if (check.get().outcome() != KeyCheck.Outcome.OK) {
				throw new AssertionError("Key check against the stub failed: " + check.get());
			}
			context.setScreen(() -> null);

			// The waiting lobby: a key, a full tab list, no teams yet — and nobody may be looked up.
			int beforeLobby = stub.playerRequests.get();
			context.waitTicks(50);
			context.runOnClient(client -> {
				if (mod.roster().hasStarted()) {
					throw new AssertionError("The waiting lobby was taken for the match");
				}
				if (stub.playerRequests.get() != beforeLobby) {
					throw new AssertionError((stub.playerRequests.get() - beforeLobby) + " lookups in the waiting lobby");
				}
			});
			context.setScreen(() -> mod.scoutScreen(null));
			context.waitTicks(3);
			context.takeScreenshot("scout_page_game_lobby");
			context.setScreen(() -> null);

			// The match starts: the scoreboard puts everybody into teams, and the mod sees it by itself.
			setUpTeams(context, singleplayer);
			context.waitFor(client -> mod.roster().hasStarted(), 100);

			// Everybody in the game gets looked up, once, through the real HTTP client.
			context.waitFor(client -> members.stream().allMatch(member -> mod.stats().peek(member.uuid()) != null), 400);
			context.waitTicks(25);
			context.runOnClient(client -> assertRelativeThreat(mod, members));
			context.runOnClient(client -> assertStats(mod, members));
			context.runOnClient(client -> {
				if (mod.client().getLimiter().limit() != 600) {
					throw new AssertionError("The production key's limit was not taken from the answer: "
							+ mod.client().getLimiter().limit());
				}
			});
			int requests = stub.playerRequests.get();

			context.waitTicks(10);
			context.takeScreenshot("scout_table");

			context.runOnClient(client -> {
				mod.settings().table.showBeds = true;
				mod.settings().table.showAccountAge = true;
				mod.settings().table.hideOwnTeam = true;
				mod.settings().table.scale = 0.8;
			});
			context.waitTicks(2);
			context.takeScreenshot("scout_table_all_columns_scaled");
			context.runOnClient(client -> {
				mod.settings().table.showBeds = false;
				mod.settings().table.showAccountAge = false;
				mod.settings().table.hideOwnTeam = false;
				mod.settings().table.scale = 1.0;
			});

			// The tab list, replaced while the key is held.
			context.runOnClient(client -> mod.settings().tab.enabled = true);
			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(3);
			context.takeScreenshot("scout_tab_list");
			context.getInput().releaseKey(options -> options.keyPlayerList);

			// Somebody in front of the camera: the look tooltip and the decorated nametag.
			context.runOnClient(client -> {
				mod.table().close();
				mod.settings().nametag.stars = true;
				var self = client.player;
				self.snapTo(self.getX(), self.getY(), self.getZ(), 0.0f, 0.0f);
				var other = new net.minecraft.client.player.RemotePlayer(client.level,
						new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("scout-test:Sundial".getBytes()),
								"Sundial"));
				other.snapTo(self.getX(), self.getY(), self.getZ() + 3.5, 180.0f, 0.0f);
				other.setId(424_242);
				client.level.addEntity(other);
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				var target = de.raindancer118.hypixelscout.game.LookTarget.pick(mod.settings().tooltip.cosine(), false, 1.0f);
				if (target == null || !target.getScoreboardName().equals("Sundial")) {
					throw new AssertionError("Look target not picked: " + target);
				}
				var tag = de.raindancer118.hypixelscout.game.Nametags.decorate(target, Component.literal("Sundial"));
				if (!tag.getString().replaceAll("§.", "").startsWith("[1502")) {
					throw new AssertionError("Nametag not decorated: " + tag.getString());
				}
			});
			context.takeScreenshot("scout_look_tooltip_nametag");

			// Holding the peek key: the full profile of whoever is aimed at, gone on release.
			context.getInput().holdKey(mod.keys().peekMapping());
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (mod.peek().showing() != de.raindancer118.hypixelscout.ui.hud.PeekElement.Showing.PLAYER
						|| !"Sundial".equals(mod.peek().shownPlayer())) {
					throw new AssertionError("Peek did not show the aimed-at player: " + mod.peek().showing());
				}
			});
			context.takeScreenshot("scout_peek_player");

			context.runOnClient(client -> client.player.snapTo(client.player.getX(), client.player.getY(),
					client.player.getZ(), 180.0f, 0.0f));
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (mod.peek().showing() != de.raindancer118.hypixelscout.ui.hud.PeekElement.Showing.TABLE) {
					throw new AssertionError("Peek without a target did not show the table: " + mod.peek().showing());
				}
			});
			context.takeScreenshot("scout_peek_table");

			context.getInput().releaseKey(mod.keys().peekMapping());
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (mod.peek().isHeld() || mod.peek().showing() != de.raindancer118.hypixelscout.ui.hud.PeekElement.Showing.NOTHING) {
					throw new AssertionError("Peek stayed up after the key was released");
				}
				client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
			});
			context.takeScreenshot("scout_peek_released");
			context.runOnClient(client -> mod.settings().nametag.stars = false);

			// The threat report into team chat: one line per enemy team, most dangerous first.
			context.runOnClient(client -> {
				mod.partyReport().send(de.raindancer118.hypixelscout.game.PartyReport.Channel.TEAM);
				List<String> lines = mod.partyReport().pendingLines();
				if (lines.size() != 3 || !lines.getFirst().startsWith("Yellow EXTREME: Sundial 1502* 13.8 WS104")
						|| lines.stream().anyMatch(line -> line.startsWith("Red"))) {
					throw new AssertionError("Unexpected team report: " + lines);
				}
				if (lines.stream().noneMatch(line -> line.contains("Glimmer NICK"))) {
					throw new AssertionError("The nick is missing from the report: " + lines);
				}
			});
			context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
			context.waitTicks(5);
			context.takeScreenshot("scout_team_report_sent");

			// Chat: the names in a line gain the stats on hover, and nothing else changes.
			context.runOnClient(client -> assertChatHover(mod));
			singleplayer.getServer().runCommand("tellraw @a [{\"text\":\"Ashenvale\",\"color\":\"red\"},"
					+ "{\"text\":\" was knocked into the void by \",\"color\":\"gray\"},"
					+ "{\"text\":\"Lanternfish\",\"color\":\"green\"},{\"text\":\". FINAL KILL!\",\"color\":\"aqua\",\"bold\":true}]");
			context.waitTicks(5);

			context.runOnClient(client -> {
				mod.lookups().add("Technoblade");
				mod.lookups().add("Sundial");
				mod.lookups().add("Brickmason");
			});

			// The main screen, every tab.
			for (ScoutScreen.Page page : ScoutScreen.Page.values()) {
				context.setScreen(() -> {
					ScoutScreen screen = mod.scoutScreen(null);
					screen.showPage(page);
					return screen;
				});
				context.waitTicks(4);
				context.takeScreenshot("scout_page_" + page.name().toLowerCase());
			}

			context.runOnClient(client -> {
				ScoutScreen screen = mod.scoutScreen(null);
				client.gui.setScreen(screen);
				if (screen.shownRows() != members.size()) {
					throw new AssertionError("Game tab lists " + screen.shownRows() + " of " + members.size());
				}
			});

			// A profile from the list, and a stranger looked up by name through Mojang.
			context.setScreen(() -> mod.profileScreen("Sundial",
					UUID.nameUUIDFromBytes("scout-test:Sundial".getBytes()), null));
			context.waitTicks(4);
			context.takeScreenshot("scout_profile");

			// One player's stats into team chat, from the profile, with one click.
			context.clickScreenButton("message.hypixelscout.profile.send.team");
			context.runOnClient(client -> {
				List<String> lines = mod.partyReport().pendingLines();
				if (lines.isEmpty() || !lines.getLast().equals(
						"Sundial [MVP+] 1502* EXTREME | FKDR 13.83 | WLR 5.27 | WS 104 | Beds/g 1.7 | Kills/g 8.5")) {
					throw new AssertionError("Unexpected player line: " + lines);
				}
			});
			context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
			context.setScreen(() -> null);
			context.runOnClient(client -> client.player.connection.sendCommand("scout Brickmason party"));
			context.waitFor(client -> mod.partyReport().pendingLines().stream()
					.anyMatch(line -> line.startsWith("Brickmason [MVP+] 488*")), 100);
			context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
			context.waitTicks(3);
			context.takeScreenshot("scout_player_sent");

			context.setScreen(() -> mod.profileScreen("Technoblade", null, null));
			context.waitFor(client -> client.gui.screen() instanceof ProfileScreen profile && profile.uuid() != null
					&& mod.stats().peek(profile.uuid()) != null, 200);
			context.waitTicks(4);
			context.takeScreenshot("scout_profile_stranger");

			context.setScreen(() -> mod.profileScreen("Glimmer",
					UUID.nameUUIDFromBytes("scout-test:Glimmer".getBytes()), null));
			context.waitTicks(3);
			context.takeScreenshot("scout_profile_nick");

			// Rebinding the peek key from the mod's own settings, the way a player would: click the
			// binding, press the new key.
			context.setScreen(() -> mod.settingsScreen(null).onTab(SettingsScreen.KEYS_TAB));
			context.waitTicks(3);
			context.takeScreenshot("scout_settings_keys");
			clickKeyBinding(context, "Peek");
			context.waitTicks(1);
			context.takeScreenshot("scout_settings_keys_capturing");
			context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_H);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (!mod.keys().peekMapping().saveString().equals("key.keyboard.h")) {
					throw new AssertionError("Peek key not rebound: " + mod.keys().peekMapping().saveString());
				}
			});
			context.takeScreenshot("scout_settings_keys_rebound");
			// Escape while capturing unbinds, as in vanilla's controls; then the default comes back.
			clickKeyBinding(context, "Peek");
			context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (!mod.keys().peekMapping().isUnbound()) {
					throw new AssertionError("Escape did not unbind the peek key");
				}
				if (!(client.gui.screen() instanceof SettingsScreen)) {
					throw new AssertionError("Escape while capturing closed the settings");
				}
			});
			context.clickScreenButton("message.hypixelscout.settings.keys.reset");
			context.waitTicks(2);
			context.runOnClient(client -> {
				if (!mod.keys().peekMapping().isDefault()) {
					throw new AssertionError("Reset did not bring the default peek key back");
				}
			});

			// Every settings tab, and the editor.
			for (int tab = 0; tab < 4; tab++) {
				int index = tab;
				context.setScreen(() -> mod.settingsScreen(null).onTab(index));
				context.waitTicks(3);
				context.takeScreenshot("scout_settings_" + tab);
			}

			context.setScreen(() -> mod.tableEditor(null));
			context.waitTicks(3);
			context.takeScreenshot("scout_table_editor");
			context.runOnClient(client -> {
				if (!(client.gui.screen() instanceof TableEditorScreen)) {
					throw new AssertionError("Table editor did not open");
				}
			});
			context.setScreen(() -> null);

			// Commands open what they say.
			context.runOnClient(client -> client.player.connection.sendCommand("scout teams"));
			context.waitForScreen(ScoutScreen.class);
			context.runOnClient(client -> {
				if (((ScoutScreen) client.gui.screen()).page() != ScoutScreen.Page.TEAMS) {
					throw new AssertionError("/scout teams opened the wrong tab");
				}
			});
			context.setScreen(() -> null);
			context.runOnClient(client -> client.player.connection.sendCommand("scout Brickmason"));
			context.waitForScreen(ProfileScreen.class);
			context.setScreen(() -> null);

			// Opening all of that must not have looked anybody up a second time.
			if (stub.playerRequests.get() > requests + 1) {
				throw new AssertionError("Screens caused " + (stub.playerRequests.get() - requests) + " extra requests");
			}

			// Leaving the game empties everything.
			context.runOnClient(client -> {
				TabListReader.replaceForTest(null);
				mod.roster().onLocationChanged(false, null, null);
				if (!mod.roster().members().isEmpty()) {
					throw new AssertionError("Roster survived leaving the game");
				}
			});
			context.waitTicks(3);
			context.takeScreenshot("scout_after_game");

			// Outside a game the editor shows a made-up lobby, so the table can still be placed.
			context.runOnClient(client -> mod.settings().table.placement =
					de.raindancer118.hypixelscout.config.TablePlacement.fromTopLeft(427, 240, 200, 150, 220, 90));
			context.setScreen(() -> mod.tableEditor(null));
			context.waitTicks(3);
			context.takeScreenshot("scout_table_editor_sample");
			context.setScreen(() -> null);
		} catch (java.io.IOException e) {
			throw new AssertionError("Could not start the Hypixel stub", e);
		}
	}

	/** Moves the real mouse onto the binding whose label starts with {@code label} and clicks it. */
	private static void clickKeyBinding(ClientGameTestContext context, String label) {
		double[] at = context.computeOnClient(client -> {
			double scale = client.getWindow().getGuiScale();
			for (var child : client.gui.screen().children()) {
				if (child instanceof de.raindancer118.hypixelscout.ui.widget.KeyBindButton button
						&& button.getMessage().getString().startsWith(label)) {
					return new double[] {(button.getX() + button.getWidth() / 2.0) * scale,
							(button.getY() + button.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("No key binding labelled " + label);
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static void setUpTeams(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		for (String team : List.of("red", "blue", "green", "yellow")) {
			singleplayer.getServer().runCommand("team add " + team);
			singleplayer.getServer().runCommand("team modify " + team + " color " + team);
		}
		for (Seat seat : GAME) {
			singleplayer.getServer().runCommand("team join " + seat.team() + " " + seat.player().name());
		}
		singleplayer.getServer().runCommand("team join red @a");
		context.waitTicks(5);
	}

	private static void assertKeyMappingsRegistered(Minecraft client) {
		List<String> registered = Arrays.stream(client.options.keyMappings).map(KeyMapping::getName).toList();
		for (String expected : EXPECTED_KEYS) {
			if (!registered.contains(expected)) {
				throw new AssertionError("Keybind missing from the controls screen: " + expected);
			}
		}

		// No default of ours may land on a key vanilla (or another mod) already uses by default.
		for (KeyMapping ours : client.options.keyMappings) {
			if (!ours.getName().startsWith("key.hypixelscout.") || ours.getDefaultKey().getValue() == -1) {
				continue;
			}
			for (KeyMapping other : client.options.keyMappings) {
				if (other != ours && !other.getName().startsWith("key.debug.")
						&& other.getDefaultKey().equals(ours.getDefaultKey())) {
					throw new AssertionError(ours.getName() + " defaults to the same key as " + other.getName());
				}
			}
		}
	}

	private static void assertCommandRegistered() {
		CommandNode<FabricClientCommandSource> scout = ClientCommands.getActiveDispatcher().getRoot().getChild("scout");
		if (scout == null) {
			throw new AssertionError("/scout is not registered");
		}
		for (String sub : EXPECTED_SUBCOMMANDS) {
			if (scout.getChild(sub) == null) {
				throw new AssertionError("/scout " + sub + " is missing");
			}
		}
		if (ClientCommands.getActiveDispatcher().getRoot().getChild("hypixelscout") == null) {
			throw new AssertionError("/hypixelscout alias is missing");
		}
	}

	/**
	 * Orchard (index about 770) is MED on the fixed bands but LOW against this team, which has a
	 * 1123-star carry in it; Brickmason is HIGH either way.
	 */
	private static void assertRelativeThreat(HypixelScout mod, List<Roster.Member> members) {
		var orchard = mod.stats().peek(uuidOf(members, "Orchard"));
		var brickmason = mod.stats().peek(uuidOf(members, "Brickmason"));

		if (!mod.threatScale().isRelative()) {
			throw new AssertionError("No relative threat scale although the own stats are known");
		}
		if (de.raindancer118.hypixelscout.ui.Threats.of(orchard) != de.raindancer118.hypixelscout.core.Threat.LOW
				|| de.raindancer118.hypixelscout.core.Threat.of(orchard) != de.raindancer118.hypixelscout.core.Threat.MEDIUM) {
			throw new AssertionError("Orchard: relative " + de.raindancer118.hypixelscout.ui.Threats.of(orchard)
					+ ", absolute " + de.raindancer118.hypixelscout.core.Threat.of(orchard));
		}
		if (de.raindancer118.hypixelscout.ui.Threats.of(brickmason) != de.raindancer118.hypixelscout.core.Threat.HIGH) {
			throw new AssertionError("Brickmason: " + de.raindancer118.hypixelscout.ui.Threats.of(brickmason));
		}
	}

	private static UUID uuidOf(List<Roster.Member> members, String name) {
		return members.stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow().uuid();
	}

	private static void assertStats(HypixelScout mod, List<Roster.Member> members) {
		PlayerStats sundial = mod.stats().peek(members.stream().filter(m -> m.name().equals("Sundial")).findFirst()
				.orElseThrow().uuid());
		if (sundial.getStars() != 1502 || sundial.getWinstreak() != 104) {
			throw new AssertionError("Sundial parsed wrong: " + sundial.getStars() + " / " + sundial.getWinstreak());
		}

		PlayerStats glimmer = mod.stats().peek(members.stream().filter(m -> m.name().equals("Glimmer")).findFirst()
				.orElseThrow().uuid());
		if (!glimmer.isNicked()) {
			throw new AssertionError("A player Hypixel does not know was not treated as a nick");
		}
	}

	private static void assertChatHover(HypixelScout mod) {
		Component line = Component.literal("Ashenvale was killed by Brickmason.");
		Component modified = mod.chatHover().modify(line, false);

		List<HoverEvent> hovers = new ArrayList<>();
		modified.visit((style, text) -> {
			if (style.getHoverEvent() != null) {
				hovers.add(style.getHoverEvent());
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);

		if (hovers.size() != 2 || !modified.getString().equals(line.getString())) {
			throw new AssertionError("Chat hover produced " + hovers.size() + " hovers: " + modified.getString());
		}

		if (mod.chatHover().modify(line, true) != line) {
			throw new AssertionError("The action bar was modified");
		}
	}
}
