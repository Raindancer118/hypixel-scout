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
			"key.hypixelscout.move_table", "key.hypixelscout.profile_target", "key.hypixelscout.party_report", "key.hypixelscout.team_list", "key.hypixelscout.party_list",
			"key.hypixelscout.queue_1", "key.hypixelscout.queue_9", "key.hypixelscout.queue_random");

	private static final List<String> EXPECTED_SUBCOMMANDS = List.of(
			"game", "teams", "lookup", "queue", "settings", "move", "table", "party", "refresh", "status", "cheats", "suspects",
			"testkey", "key", "player", "list", "requeue");

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
			// The nick was told apart through Mojang and cost no Hypixel request.
			if (stub.asked.contains(UUID.nameUUIDFromBytes("scout-test:Glimmer".getBytes()).toString().replace("-", ""))) {
				throw new AssertionError("Hypixel was asked about a nick Mojang already gave away");
			}
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
				// A teammate right beside the player: no popup for them.
				var mate = new net.minecraft.client.player.RemotePlayer(client.level,
						new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("scout-test:Ashenvale".getBytes()),
								"Ashenvale"));
				mate.snapTo(self.getX() + 2.0, self.getY(), self.getZ() - 2.0, 0.0f, 0.0f);
				mate.setId(424_243);
				client.level.addEntity(mate);
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

			// Sundial, an enemy three and a half blocks away, came into the popup radius.
			context.runOnClient(client -> {
				var shown = mod.proximity().shown();
				if (shown.isEmpty() || !shown.getFirst().name().equals("Sundial")) {
					throw new AssertionError("No proximity popup for Sundial: " + shown);
				}
				if (shown.stream().anyMatch(popup -> popup.name().equals("Ashenvale"))) {
					throw new AssertionError("A popup for a teammate: " + shown);
				}
				mod.settings().proximity.from = de.raindancer118.hypixelscout.core.Threat.INSANE;
				if (mod.proximity().shown().isEmpty()) {
					throw new AssertionError("Sundial is INSANE and still filtered out");
				}
				mod.settings().proximity.from = de.raindancer118.hypixelscout.core.Threat.NONE;
				mod.settings().tooltip.enabled = false;
			});
			context.waitTicks(2);
			context.takeScreenshot("scout_proximity_popup");
			context.runOnClient(client -> mod.settings().tooltip.enabled = true);

			// Auto requeue, party mode: in a party with Ashenvale, nothing until both are finally out.
			String self = context.computeOnClient(client -> client.player.getScoreboardName());
			context.runOnClient(client -> {
				mod.settings().requeue.mode = de.raindancer118.hypixelscout.core.RequeueMode.PARTY;
				mod.settings().requeue.delaySeconds = 15;
				mod.requeue().setPartyForTest(java.util.Set.of(client.player.getUUID(), uuidOf(members, "Ashenvale")),
						client.player.getUUID());
			});
			singleplayer.getServer().runCommand("tellraw @a {\"text\":\"" + self + " fell into the void.\"}");
			singleplayer.getServer().runCommand("tellraw @a {\"text\":\"" + self
					+ " was knocked into the void by Sundial. FINAL KILL!\"}");
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (mod.requeue().isPending()) {
					throw new AssertionError("Requeue although Ashenvale is still in");
				}
			});
			singleplayer.getServer().runCommand("tellraw @a {\"text\":\"Ashenvale fell into the void. FINAL KILL!\"}");
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (!mod.requeue().isPending()) {
					throw new AssertionError("No requeue after the whole party was out");
				}
			});
			context.takeScreenshot("scout_requeue_pending");
			context.runOnClient(client -> client.player.connection.sendCommand("scout requeue cancel"));
			context.runOnClient(client -> {
				if (mod.requeue().isPending()) {
					throw new AssertionError("Requeue not cancelled");
				}
				mod.settings().requeue.mode = de.raindancer118.hypixelscout.core.RequeueMode.OFF;
				mod.settings().requeue.delaySeconds = 3;
				mod.requeue().setPartyForTest(java.util.Set.of(), null);
			});

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

			assertProjectiles(context, singleplayer, mod);
			assertHazards(context, singleplayer, mod);
			assertCallouts(context, mod);
			assertCheats(context, mod);

			// The threat report into team chat: one line per enemy team, most dangerous first.
			context.runOnClient(client -> {
				mod.partyReport().send(de.raindancer118.hypixelscout.game.PartyReport.Channel.TEAM);
				List<String> lines = mod.partyReport().pendingLines();
				if (lines.isEmpty() || !lines.getFirst().equals("YELLOW Sundial 1502* - INSANE - 13.8 FKDR - 104 WS")
						|| lines.stream().anyMatch(line -> line.startsWith("RED"))) {
					throw new AssertionError("Unexpected team report: " + lines);
				}
				if (lines.stream().noneMatch(line -> line.equals("BLUE Glimmer is nicked"))) {
					throw new AssertionError("The nick is missing from the report: " + lines);
				}
			});
			context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
			context.waitTicks(5);
			context.takeScreenshot("scout_team_report_sent");

			// The whole enemy list into party chat: everybody, the harmless ones too, then sent out.
			context.runOnClient(client -> client.player.connection.sendCommand("scout list party"));
			context.runOnClient(client -> {
				List<String> lines = mod.partyReport().pendingLines();
				if (lines.size() != 12 || !lines.getFirst().equals("YELLOW Sundial 1502* - INSANE - 13.8 FKDR - 104 WS")
						|| lines.stream().noneMatch(line -> line.startsWith("GREEN mossy 17* - NONE"))
						|| !lines.getLast().equals("BLUE Glimmer is nicked")
						|| lines.stream().anyMatch(line -> line.startsWith("RED"))) {
					throw new AssertionError("Unexpected enemy list: " + lines);
				}
			});
			context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);

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
				if (lines.size() < 2 || !lines.subList(lines.size() - 2, lines.size()).equals(List.of(
						"Sundial [MVP+] 1502* is INSANE",
						"13.8 FKDR, 5.3 WLR, 104 winstreak, 1.7 beds and 8.5 kills a game"))) {
					throw new AssertionError("Unexpected player line: " + lines);
				}
			});
			context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
			context.setScreen(() -> null);
			context.runOnClient(client -> client.player.connection.sendCommand("scout Brickmason party"));
			context.waitFor(client -> mod.partyReport().pendingLines().stream()
					.anyMatch(line -> line.startsWith("Brickmason [MVP+] 488* is ")), 100);
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
			for (int tab = 0; tab < SettingsScreen.KEYS_TAB; tab++) {
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
				// Blue's bed, seen standing in wool and end stone; Red's was broken in the hazard test.
				mod.hazards().ledger().record("blue", new de.raindancer118.hypixelscout.core.BedDefense.Cell(30_000, 70, 30_000),
						new de.raindancer118.hypixelscout.core.BedDefense.Report(false, List.of(
								new de.raindancer118.hypixelscout.core.BedDefense.Material("Blue Wool", 0.8, 4),
								new de.raindancer118.hypixelscout.core.BedDefense.Material("End Stone", 3.0, 14)), null));
			});
			context.waitTicks(2);
			context.takeScreenshot("scout_teams_beds");
			context.runOnClient(client -> client.gui.screen().mouseScrolled(0, 0, 0, -10));
			context.waitTicks(2);
			context.takeScreenshot("scout_teams_beds_scrolled");
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

	/**
	 * Arrows and fireballs, as client-side entities in front of the camera: one flying at the player
	 * is a warning, one thrown point-blank in view and one passing by are not, and a fire charge in
	 * hand gives the aim line.
	 */
	private static void assertProjectiles(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			HypixelScout mod) {
		java.util.function.BiFunction<net.minecraft.client.Minecraft, double[], net.minecraft.world.entity.Entity> fireball =
				(client, at) -> {
					var entity = new net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball(
							net.minecraft.world.entity.EntityTypes.FIREBALL, client.level);
					entity.snapTo(at[0], at[1], at[2], 0.0f, 0.0f);
					entity.setDeltaMovement(at[3], at[4], at[5]);
					entity.setId((int) at[6]);
					client.level.addEntity(entity);
					return entity;
				};

		// A fireball twenty blocks ahead, flying straight at the player.
		context.runOnClient(client -> {
			var self = client.player;
			self.snapTo(self.getX(), self.getY(), self.getZ(), 0.0f, 0.0f);
			fireball.apply(client, new double[] {self.getX(), self.getEyeY() - 0.5, self.getZ() + 20, 0, 0, -1.0, 525_001});
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			var warning = mod.flights().warning();
			if (warning == null || warning.kind() != de.raindancer118.hypixelscout.flight.ProjectileKind.FIREBALL) {
				throw new AssertionError("No warning for a fireball flying at the player: " + warning);
			}
			if (Math.abs(warning.bearing(de.raindancer118.hypixelscout.game.Flights.vec(client.player.getEyePosition()),
					de.raindancer118.hypixelscout.game.Flights.vec(client.player.getViewVector(1.0f)))) > 20) {
				throw new AssertionError("The fireball ahead is not reported as ahead");
			}
			if (mod.flights().flying(client.level, client.player, 1.0f).isEmpty()) {
				throw new AssertionError("The fireball has no flight path");
			}
			if (mod.flights().toneOn() != de.raindancer118.hypixelscout.flight.MissileAlarm.Tone.LAUNCH) {
				throw new AssertionError("No missile-inbound tone for a fireball flying at the player");
			}
			if (client.getSoundManager().getSoundEvent(de.raindancer118.hypixelscout.game.Flights.toneId(
					de.raindancer118.hypixelscout.flight.MissileAlarm.Tone.LAUNCH)) == null) {
				throw new AssertionError("The missile-inbound sound is not in sounds.json");
			}
		});
		context.takeScreenshot("scout_incoming_fireball");
		cleanShot(context, mod, "scout_incoming_fireball_clean", 1);
		context.runOnClient(client -> client.level.getEntity(525_001).discard());
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (mod.flights().warning() != null) {
				throw new AssertionError("The warning outlived the fireball");
			}
			if (mod.flights().isToneOn()) {
				throw new AssertionError("The missile-inbound tone outlived the fireball");
			}
		});

		// Thrown two blocks in front of the player's face: seen anyway, so no warning.
		context.runOnClient(client -> {
			var self = client.player;
			fireball.apply(client, new double[] {self.getX(), self.getEyeY() - 0.2, self.getZ() + 2, 0, 0, -0.5, 525_002});
		});
		context.waitTick();
		context.runOnClient(client -> {
			if (mod.flights().warning() != null) {
				throw new AssertionError("A warning for a fireball thrown point-blank in view");
			}
			client.level.getEntity(525_002).discard();
		});

		// An arrow crossing ten blocks ahead: a path, but nothing to warn about.
		context.runOnClient(client -> {
			var self = client.player;
			var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(net.minecraft.world.entity.EntityTypes.ARROW,
					client.level);
			arrow.snapTo(self.getX() - 15, self.getEyeY() + 1, self.getZ() + 10, 0.0f, 0.0f);
			arrow.setDeltaMovement(2.5, 0.2, 0);
			arrow.setId(525_003);
			client.level.addEntity(arrow);
		});
		context.waitTick();
		context.runOnClient(client -> {
			if (mod.flights().warning() != null) {
				throw new AssertionError("A warning for an arrow passing by: " + mod.flights().warning());
			}
			if (mod.flights().flying(client.level, client.player, 1.0f).stream()
					.noneMatch(flying -> flying.kind() == de.raindancer118.hypixelscout.flight.ProjectileKind.ARROW)) {
				throw new AssertionError("The arrow has no flight path");
			}
		});
		context.takeScreenshot("scout_arrow_path");
		context.runOnClient(client -> client.level.getEntity(525_003).discard());

		assertTargetLock(context, mod);

		// A fire charge in hand, looking down at the floor ahead: the aim line ends on the ground.
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with fire_charge");
		context.waitTicks(3);
		context.runOnClient(client -> {
			var self = client.player;
			self.snapTo(self.getX(), self.getY(), self.getZ(), 0.0f, 35.0f);
		});
		context.waitTick();
		context.runOnClient(client -> {
			var aim = mod.flights().aim(client.level, client.player, 1.0f);
			if (aim == null || !aim.blocked()) {
				throw new AssertionError("No aim line to the ground with a fire charge in hand: " + aim);
			}
		});
		context.takeScreenshot("scout_fireball_aim");
		cleanShot(context, mod, "scout_fireball_aim_clean", 50);
		singleplayer.getServer().runCommand("item replace entity @a weapon.mainhand with air");
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (mod.flights().aim(client.level, client.player, 1.0f) != null) {
				throw new AssertionError("An aim line without a fire charge");
			}
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
		});
	}

	/**
	 * Pearls, the bow line, TNT, falls, the bed defence and the edge markers — each against real
	 * client entities or blocks placed by command, and each cleaned away again for the tests after.
	 */
	private static void assertHazards(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			HypixelScout mod) {
		var server = singleplayer.getServer();
		int[] home = new int[3];
		double[] homeExact = new double[3];
		context.runOnClient(client -> {
			var self = client.player;
			home[0] = self.getBlockX();
			home[1] = self.getBlockY();
			home[2] = self.getBlockZ();
			homeExact[0] = self.getX();
			homeExact[1] = self.getY();
			homeExact[2] = self.getZ();
		});
		int x = home[0];
		int y = home[1];
		int z = home[2];

		// Somebody's ender pearl, lobbed across in front: a purple path to where they will appear.
		context.runOnClient(client -> {
			var pearl = new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl(
					net.minecraft.world.entity.EntityTypes.ENDER_PEARL, client.level);
			pearl.snapTo(homeExact[0] - 6, homeExact[1] + 4, homeExact[2] + 9, 0.0f, 0.0f);
			pearl.setDeltaMovement(0.6, 0.2, 0);
			pearl.setId(525_010);
			client.level.addEntity(pearl);
		});
		context.waitTick();
		context.runOnClient(client -> {
			var pearl = mod.flights().flying(client.level, client.player, 1.0f).stream()
					.filter(flying -> flying.kind() == de.raindancer118.hypixelscout.flight.ProjectileKind.PEARL)
					.findFirst().orElse(null);
			if (pearl == null || !pearl.path().blocked()) {
				throw new AssertionError("No landing spot for an ender pearl in the air: " + pearl);
			}
			if (mod.flights().warning() != null) {
				throw new AssertionError("A pearl counted as incoming: " + mod.flights().warning());
			}
		});
		context.takeScreenshot("scout_enemy_pearl");
		context.runOnClient(client -> client.level.getEntity(525_010).discard());

		// An ender pearl in hand, looking a little up: its arc comes down on the ground ahead.
		server.runCommand("item replace entity @a weapon.mainhand with ender_pearl");
		context.waitTicks(3);
		context.runOnClient(client -> client.player.snapTo(client.player.getX(), client.player.getY(),
				client.player.getZ(), 0.0f, -15.0f));
		context.waitTick();
		context.runOnClient(client -> {
			var aim = mod.flights().aimAny(client.level, client.player, 1.0f);
			if (aim == null || aim.kind() != de.raindancer118.hypixelscout.flight.ProjectileKind.PEARL || !aim.path().blocked()) {
				throw new AssertionError("No pearl arc to the ground with a pearl in hand: " + aim);
			}
		});
		context.takeScreenshot("scout_pearl_aim");

		// A bow being drawn: the arrow's arc at the current draw.
		server.runCommand("item replace entity @a weapon.mainhand with bow");
		server.runCommand("give @a arrow 4");
		context.waitTicks(3);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(12);
		context.runOnClient(client -> {
			var aim = mod.flights().aimAny(client.level, client.player, 1.0f);
			if (aim == null || aim.kind() != de.raindancer118.hypixelscout.flight.ProjectileKind.ARROW) {
				throw new AssertionError("No arrow arc while drawing a bow: " + aim);
			}
		});
		context.takeScreenshot("scout_bow_aim");
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(2);
		server.runCommand("item replace entity @a weapon.mainhand with air");
		server.runCommand("clear @a arrow");
		context.waitTicks(3);
		context.runOnClient(client -> {
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
			if (mod.flights().aimAny(client.level, client.player, 1.0f) != null) {
				throw new AssertionError("An aim line with empty hands");
			}
		});

		// Primed TNT three blocks to the side with three seconds left: it would throw the player away.
		context.runOnClient(client -> {
			var tnt = new net.minecraft.world.entity.item.PrimedTnt(net.minecraft.world.entity.EntityTypes.TNT, client.level);
			tnt.snapTo(homeExact[0] + 3, homeExact[1], homeExact[2], 0.0f, 0.0f);
			tnt.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			tnt.setFuse(60);
			tnt.setId(525_011);
			client.level.addEntity(tnt);
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			var tnt = mod.hazards().tnt();
			if (tnt.isEmpty() || tnt.getFirst().knock() == null) {
				throw new AssertionError("No knockback from primed TNT three blocks away: " + tnt);
			}
			if (tnt.getFirst().knock().push().x() >= 0) {
				throw new AssertionError("TNT on the +x side does not push towards −x: " + tnt.getFirst().knock());
			}
		});
		context.takeScreenshot("scout_tnt");
		context.runOnClient(client -> client.level.getEntity(525_011).discard());
		context.waitTick();

		// Falls: from ten blocks up onto the ground, a landing spot; over a hole to the bottom, the void.
		server.runCommand("gamemode creative @a");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5", x, y + 10, z));
		context.waitTicks(3);
		context.runOnClient(client -> {
			var fall = mod.hazards().fall();
			if (fall == null || fall.intoTheVoid() || fall.landing() == null || Math.abs(fall.landing().y() - y) > 0.01) {
				throw new AssertionError("No landing on the ground for a fall from ten blocks: " + fall);
			}
		});
		context.takeScreenshot("scout_landing");
		int holeX = x + 20;
		server.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air",
				holeX - 2, -64, z - 2, holeX + 2, y - 1, z + 2));
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5", holeX, y + 6, z));
		context.waitTicks(3);
		context.runOnClient(client -> {
			var fall = mod.hazards().fall();
			if (fall == null || !fall.intoTheVoid()) {
				throw new AssertionError("No NO SAFE LANDING over a hole to the void: " + fall);
			}
		});
		context.takeScreenshot("scout_no_safe_landing");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %s %s %s 0 0", homeExact[0], homeExact[1], homeExact[2]));
		server.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d grass_block",
				holeX - 2, -64, z - 2, holeX + 2, y - 1, z + 2));
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);

		// A red bed in wool under end stone, ten blocks ahead: only the end stone shows.
		server.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d end_stone", x - 2, y, z + 8, x + 3, y + 2, z + 12));
		server.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d white_wool", x - 1, y, z + 9, x + 2, y + 1, z + 11));
		server.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d red_bed[facing=east,part=foot]", x, y, z + 10));
		server.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d red_bed[facing=east,part=head]", x + 1, y, z + 10));
		context.waitTicks(5);
		context.runOnClient(client -> {
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			var bed = mod.hazards().bed();
			if (bed == null || !bed.colour().equals("RED") || bed.report().open()) {
				throw new AssertionError("The defended red bed ahead is not seen: " + bed);
			}
			var outside = bed.report().outside().stream().map(de.raindancer118.hypixelscout.core.BedDefense.Material::name).toList();
			if (outside.size() != 1 || !outside.getFirst().equals("End Stone")) {
				throw new AssertionError("The bed's outside is not just its end stone: " + outside);
			}
		});
		// Somebody breaks the end stone on top: the wool under it is out.
		server.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d air", x, y + 2, z + 10));
		context.waitTicks(3);
		context.runOnClient(client -> {
			var bed = mod.hazards().bed();
			if (bed == null || !bed.report().outside().getFirst().name().equals("White Wool")) {
				throw new AssertionError("The uncovered wool is not the softest exposed block: " + bed);
			}
		});
		context.takeScreenshot("scout_bed_defense");
		// Looking away — and dying — does not forget it: the round's ledger keeps the last look.
		context.runOnClient(client -> {
			mod.hazards().reset();
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 80.0f);
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			var red = mod.hazards().ledger().of("Red").orElse(null);
			if (mod.hazards().bed() != null || red == null || red.gone()
					|| !red.report().outside().getFirst().name().equals("White Wool")) {
				throw new AssertionError("The red bed's last look is not on record: " + red);
			}
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
		});
		server.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air", x - 2, y, z + 8, x + 3, y + 2, z + 12));
		context.waitTicks(25);
		context.runOnClient(client -> {
			var red = mod.hazards().ledger().of("Red").orElse(null);
			if (red == null || !red.gone()) {
				throw new AssertionError("The broken red bed is not on record as broken: " + red);
			}
		});

		// Turned round: Sundial, behind in plain sight, is an edge marker; the teammate is not.
		context.runOnClient(client -> client.player.snapTo(client.player.getX(), client.player.getY(),
				client.player.getZ(), 180.0f, 0.0f));
		context.waitTicks(2);
		context.runOnClient(client -> {
			var names = mod.hazards().offscreen().stream().map(de.raindancer118.hypixelscout.game.Hazards.Offscreen::name).toList();
			if (!names.contains("Sundial")) {
				throw new AssertionError("No edge marker for Sundial behind the player: " + names);
			}
			if (names.contains("Ashenvale")) {
				throw new AssertionError("An edge marker for a teammate");
			}
		});
		context.takeScreenshot("scout_edge_markers");
		// Invisible, he is nobody's marker.
		context.runOnClient(client -> client.level.getEntity(424_242).setInvisible(true));
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (mod.hazards().offscreen().stream().anyMatch(o -> o.name().equals("Sundial"))) {
				throw new AssertionError("An edge marker for an invisible player");
			}
			client.level.getEntity(424_242).setInvisible(false);
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
		});
		context.waitTicks(2);
	}

	/**
	 * Cheat detection on a fight the player is not part of: Sundial hits Ashenvale from 4.2 blocks,
	 * again and again, through the real packet handlers — flagged for Reach, marked on his nametag and
	 * in the lists. Then blocks appear behind him while he looks the other way: Scaffold. Both go
	 * back where they stood, and the blocks go again.
	 */
	private static void assertCheats(ClientGameTestContext context, HypixelScout mod) {
		double[][] before = new double[2][];
		int[] base = new int[3];
		// The sighting log, fresh for this run: everything below should land in it.
		java.nio.file.Path logDir = mod.cheats().log().dir();
		try (var old = java.nio.file.Files.exists(logDir) ? java.nio.file.Files.list(logDir) : java.util.stream.Stream.<java.nio.file.Path>empty()) {
			for (java.nio.file.Path file : old.toList()) {
				java.nio.file.Files.delete(file);
			}
		} catch (java.io.IOException e) {
			throw new AssertionError("Could not empty the log directory of an earlier run", e);
		}
		context.runOnClient(client -> mod.settings().cheats.log = true);
		context.runOnClient(client -> {
			var sundial = (net.minecraft.world.entity.player.Player) client.level.getEntity(424_242);
			var mate = (net.minecraft.world.entity.player.Player) client.level.getEntity(424_243);
			before[0] = new double[] {sundial.getX(), sundial.getY(), sundial.getZ(), sundial.getYRot(), sundial.getXRot()};
			before[1] = new double[] {mate.getX(), mate.getY(), mate.getZ(), mate.getYRot(), mate.getXRot()};
			base[0] = client.player.getBlockX() + 6;
			base[1] = client.player.getBlockY();
			base[2] = client.player.getBlockZ() - 6;
			place(sundial, base[0] + 0.5, base[1], base[2] + 0.5, -90.0f);
			place(mate, base[0] + 5.1, base[1], base[2] + 0.5, 90.0f);
		});
		context.waitTicks(8);

		var attack = new java.util.concurrent.atomic.AtomicReference<net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType>>();
		for (int hit = 0; hit < 4; hit++) {
			context.runOnClient(client -> {
				if (attack.get() == null) {
					attack.set(client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
							.getOrThrow(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK));
				}
				var sundial = client.level.getEntity(424_242);
				client.getConnection().handleAnimate(new net.minecraft.network.protocol.game.ClientboundAnimatePacket(sundial, 0));
				client.getConnection().handleDamageEvent(new net.minecraft.network.protocol.game.ClientboundDamageEventPacket(
						424_243, attack.get(), 424_242, 424_242, java.util.Optional.empty()));
			});
			context.waitTicks(10);
		}
		context.runOnClient(client -> {
			var flags = mod.cheats().flags("Sundial").stream().map(f -> f.check()).toList();
			if (!flags.contains(de.raindancer118.hypixelscout.cheat.Check.REACH)) {
				throw new AssertionError("Hits from 4.2 blocks between two other players are not Reach: " + flags);
			}
			var sundial = (net.minecraft.world.entity.player.Player) client.level.getEntity(424_242);
			String tag = de.raindancer118.hypixelscout.game.Nametags.decorate(sundial,
					net.minecraft.network.chat.Component.literal("Sundial")).getString();
			if (!tag.contains("\u26a0") || !tag.contains("%")) {
				throw new AssertionError("The flagged player's nametag is not marked: " + tag);
			}
			if (!de.raindancer118.hypixelscout.ui.Suspects.mark("Sundial").equals(de.raindancer118.hypixelscout.ui.Suspects.MARK)
					|| !de.raindancer118.hypixelscout.ui.Suspects.mark("Ashenvale").isEmpty()
							&& mod.cheats().flags("Ashenvale").isEmpty()) {
				throw new AssertionError("The lists do not mark exactly the flagged players");
			}
			if (mod.cheats().flags("Ashenvale").stream().anyMatch(f -> f.check() == de.raindancer118.hypixelscout.cheat.Check.REACH)) {
				throw new AssertionError("The victim was flagged for Reach");
			}
		});

		// A swing and a push arriving together off the network name the attacker without any damage
		// event: sent from another thread, the way the network thread hands packets to the client.
		int reachBefore = mod.cheats().suspicion().count("Sundial", de.raindancer118.hypixelscout.cheat.Check.REACH);
		context.runOnClient(client -> {
			var connection = client.getConnection();
			var sundial = client.level.getEntity(424_242);
			Thread network = new Thread(() -> {
				for (Runnable packet : new Runnable[] {
						() -> connection.handleAnimate(new net.minecraft.network.protocol.game.ClientboundAnimatePacket(sundial, 0)),
						() -> connection.handleSetEntityMotion(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(
								424_243, new net.minecraft.world.phys.Vec3(0.4, 0.36, 0)))}) {
					try {
						packet.run();
					} catch (RuntimeException rescheduled) {
						// The handler re-queues itself for the render thread and throws; that is its job.
					}
				}
			});
			network.start();
			try {
				network.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		context.waitTicks(4);
		context.runOnClient(client -> {
			int reachAfter = mod.cheats().suspicion().count("Sundial", de.raindancer118.hypixelscout.cheat.Check.REACH);
			if (reachAfter <= reachBefore) {
				throw new AssertionError("A swing and push arriving together did not count as Sundial's hit: " + reachAfter);
			}
		});

		// Blocks appearing behind him, one a tick, while he looks straight ahead.
		for (int i = 0; i < 8; i++) {
			int z = base[2] - 3 + i;
			context.runOnClient(client -> {
				var sundial = client.level.getEntity(424_242);
				client.getConnection().handleAnimate(new net.minecraft.network.protocol.game.ClientboundAnimatePacket(sundial, 0));
				client.getConnection().handleBlockUpdate(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
						new net.minecraft.core.BlockPos(base[0] - 2, base[1] + 3, z),
						net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState()));
			});
			context.waitTicks(2);
		}
		context.runOnClient(client -> {
			var flags = mod.cheats().flags("Sundial").stream().map(f -> f.check()).toList();
			if (!flags.contains(de.raindancer118.hypixelscout.cheat.Check.SCAFFOLD)) {
				throw new AssertionError("Blocks placed behind somebody looking away are not Scaffold: " + flags);
			}
			// A check switched off forgets what it saw, flags and all; on again, it watches afresh.
			mod.settings().cheats.set(de.raindancer118.hypixelscout.cheat.Check.SCAFFOLD, false);
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (mod.cheats().flags("Sundial").stream().anyMatch(f -> f.check() == de.raindancer118.hypixelscout.cheat.Check.SCAFFOLD)) {
				throw new AssertionError("A switched-off check still shows its flag");
			}
			mod.settings().cheats.set(de.raindancer118.hypixelscout.cheat.Check.SCAFFOLD, true);
		});
		context.setScreen(() -> new de.raindancer118.hypixelscout.ui.screen.CheatChecksScreen(mod, null));
		context.waitTicks(3);
		context.takeScreenshot("scout_cheat_checks");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			client.player.connection.sendCommand("scout cheats");

			// Reported to the party only when asked: one plain line per flagged player, surest first.
			mod.partyReport().sendCheats(de.raindancer118.hypixelscout.game.PartyReport.Channel.PARTY, mod.cheats().suspicion());
			var lines = mod.partyReport().pendingLines();
			int sure = (int) Math.round(mod.cheats().confidence("Sundial") * 100);
			if (lines.stream().noneMatch(line -> line.startsWith("CHEATER? YELLOW Sundial " + sure + "% sure - ")
					&& line.contains("Reach x"))) {
				throw new AssertionError("No cheat report line for Sundial: " + lines);
			}
			if (sure <= 50 || sure >= 100) {
				throw new AssertionError("Implausible confidence for Sundial: " + sure);
			}
			mod.partyReport().cancel();
		});
		context.waitTicks(3);
		context.takeScreenshot("scout_cheat_flags");

		// The cheats tab lists the suspects with the surest chosen; the HUD card shows them in game.
		context.setScreen(() -> {
			var screen = mod.scoutScreen(null);
			screen.showPage(ScoutScreen.Page.CHEATS);
			return screen;
		});
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (((ScoutScreen) client.gui.screen()).shownSuspects() < 1) {
				throw new AssertionError("The cheats tab lists nobody although Sundial is flagged");
			}
		});
		context.takeScreenshot("scout_page_cheats");
		context.setScreen(() -> null);
		context.waitTicks(3);
		context.runOnClient(client -> {
			var shown = de.raindancer118.hypixelscout.ui.hud.SuspectsHud.shown(mod.cheats().suspects(), mod.settings().cheats.hud);
			if (shown.stream().noneMatch(suspect -> suspect.player().equals("Sundial"))) {
				throw new AssertionError("The suspects card does not show Sundial: " + shown);
			}
		});
		context.takeScreenshot("scout_suspects_hud");
		context.setScreen(() -> new de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen(mod::settings, mod.cheats(),
				mod::saveSettings, null));
		context.waitTicks(3);
		context.takeScreenshot("scout_suspects_editor");
		context.setScreen(() -> null);

		// A wrong flag, cleared by the player: gone from the mark, written down as a false one.
		context.runOnClient(client -> client.player.connection.sendCommand("scout cheats wrong Ashenvale velocity"));
		context.waitTicks(10);
		context.runOnClient(client -> {
			if (mod.cheats().flags("Ashenvale").stream().anyMatch(f -> f.check() == de.raindancer118.hypixelscout.cheat.Check.VELOCITY)) {
				throw new AssertionError("/scout cheats wrong did not clear the flag");
			}
			String written;
			try (var files = java.nio.file.Files.list(logDir)) {
				StringBuilder all = new StringBuilder();
				for (java.nio.file.Path file : files.toList()) {
					all.append(java.nio.file.Files.readString(file));
				}
				written = all.toString();
			} catch (java.io.IOException e) {
				throw new AssertionError("The sighting log was not written", e);
			}
			for (String expected : new String[] {"\"event\":\"round\"", "\"event\":\"sighting\",", "\"player\":\"Sundial\",\"check\":\"REACH\"",
					"\"event\":\"flag\"", "\"event\":\"verdict\"", "\"player\":\"Ashenvale\",\"check\":\"VELOCITY\",\"cheating\":false"}) {
				if (!written.contains(expected)) {
					throw new AssertionError("The sighting log lacks " + expected + ":\n" + written);
				}
			}
			mod.settings().cheats.log = false;
		});

		context.runOnClient(client -> {
			for (int i = 0; i < 8; i++) {
				client.getConnection().handleBlockUpdate(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
						new net.minecraft.core.BlockPos(base[0] - 2, base[1] + 3, base[2] - 3 + i),
						net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
			}
			place(client.level.getEntity(424_242), before[0][0], before[0][1], before[0][2], (float) before[0][3]);
			place(client.level.getEntity(424_243), before[1][0], before[1][1], before[1][2], (float) before[1][3]);
		});
		context.waitTicks(2);
	}

	/**
	 * The same moment once more with nothing but the element itself: chat cleared, the look tooltip,
	 * the proximity popup, the table and the decorated nametags off, the players in front out of
	 * sight — then all back. {@code settle} ticks let vanilla's item-name popup fade where there is time.
	 */
	private static void cleanShot(ClientGameTestContext context, HypixelScout mod, String name, int settle) {
		boolean[] before = new boolean[4];
		context.runOnClient(client -> {
			before[0] = mod.settings().tooltip.enabled;
			before[1] = mod.settings().nametag.stars;
			before[2] = mod.table().isOpen();
			before[3] = mod.settings().proximity.enabled;
			mod.settings().proximity.enabled = false;
			mod.settings().tooltip.enabled = false;
			mod.settings().nametag.stars = false;
			mod.table().close();
			client.gui.hud.getChat().clearMessages(false);
			for (int id : new int[] {424_242, 424_243}) {
				var entity = client.level.getEntity(id);
				if (entity != null) {
					entity.setInvisible(true);
				}
			}
		});
		context.waitTicks(settle);
		context.takeScreenshot(name);
		context.runOnClient(client -> {
			mod.settings().tooltip.enabled = before[0];
			mod.settings().nametag.stars = before[1];
			mod.settings().proximity.enabled = before[3];
			if (before[2]) {
				mod.table().toggle();
			}
			for (int id : new int[] {424_242, 424_243}) {
				var entity = client.level.getEntity(id);
				if (entity != null) {
					entity.setInvisible(false);
				}
			}
		});
	}

	private static void place(net.minecraft.world.entity.Entity entity, double x, double y, double z, float yaw) {
		entity.snapTo(x, y, z, yaw, 0.0f);
		entity.setYHeadRot(yaw);
		entity.setOldPosAndRot();
	}

	/**
	 * Target lock: Sundial, an enemy twelve blocks ahead, aims a fire charge at the player — a lock
	 * with its own tone and the spot marked on the player; turned away, or without the fire charge,
	 * none. A teammate aiming one is never a lock. Sundial goes back where the callouts expect him.
	 */
	private static void assertTargetLock(ClientGameTestContext context, HypixelScout mod) {
		java.util.function.BiConsumer<net.minecraft.world.entity.Entity, float[]> face = (entity, rotation) -> {
			entity.snapTo(entity.getX(), entity.getY(), entity.getZ(), rotation[0], rotation[1]);
			entity.setYHeadRot(rotation[0]);
			entity.setOldPosAndRot();
		};

		context.runOnClient(client -> {
			var self = client.player;
			var sundial = (net.minecraft.world.entity.player.Player) client.level.getEntity(424_242);
			sundial.snapTo(self.getX(), self.getY(), self.getZ() + 12, 180.0f, 0.0f);
			// From his eyes down to the player's chest.
			float pitch = (float) Math.toDegrees(Math.atan2(sundial.getEyeY() - (self.getY() + 1.0), 12));
			face.accept(sundial, new float[] {180.0f, pitch});
			sundial.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIRE_CHARGE));
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			var lock = mod.flights().lock();
			if (lock == null || !lock.name().equals("Sundial")) {
				throw new AssertionError("No target lock for Sundial aiming a fire charge at the player: " + lock);
			}
			if (!de.raindancer118.hypixelscout.game.Flights.hitbox(client.player).inflate(1.0).contains(lock.point())) {
				throw new AssertionError("The lock does not mark a spot on the player: " + lock.point());
			}
			if (mod.flights().toneOn() != de.raindancer118.hypixelscout.flight.MissileAlarm.Tone.LOCK) {
				throw new AssertionError("No lock tone while locked: " + mod.flights().toneOn());
			}
			if (client.getSoundManager().getSoundEvent(de.raindancer118.hypixelscout.game.Flights.toneId(
					de.raindancer118.hypixelscout.flight.MissileAlarm.Tone.LOCK)) == null) {
				throw new AssertionError("The lock sound is not in sounds.json");
			}
			if (mod.flights().warning() != null) {
				throw new AssertionError("A missile-inbound warning with nothing thrown");
			}
		});
		context.takeScreenshot("scout_target_lock");
		cleanShot(context, mod, "scout_target_lock_clean", 1);

		// He turns aside: the lock and its tone are gone.
		context.runOnClient(client -> face.accept(client.level.getEntity(424_242), new float[] {120.0f, 0.0f}));
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (mod.flights().lock() != null) {
				throw new AssertionError("A lock with Sundial looking away: " + mod.flights().lock());
			}
			if (mod.flights().isToneOn()) {
				throw new AssertionError("The lock tone outlived the lock");
			}
		});

		// Aimed again, but empty-handed: nothing to throw, no lock.
		context.runOnClient(client -> {
			var sundial = (net.minecraft.world.entity.player.Player) client.level.getEntity(424_242);
			float pitch = (float) Math.toDegrees(Math.atan2(sundial.getEyeY() - (client.player.getY() + 1.0), 12));
			face.accept(sundial, new float[] {180.0f, pitch});
			sundial.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (mod.flights().lock() != null) {
				throw new AssertionError("A lock without a fire charge");
			}
		});

		// The teammate beside the player, fire charge aimed at the player: a friend, never a lock.
		context.runOnClient(client -> {
			var self = client.player;
			var mate = (net.minecraft.world.entity.player.Player) client.level.getEntity(424_243);
			mate.snapTo(self.getX(), self.getY(), self.getZ() - 10, 0.0f, 0.0f);
			float pitch = (float) Math.toDegrees(Math.atan2(mate.getEyeY() - (self.getY() + 1.0), 10));
			face.accept(mate, new float[] {0.0f, pitch});
			mate.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIRE_CHARGE));
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (mod.flights().lock() != null) {
				throw new AssertionError("A teammate's fire charge counted as a lock: " + mod.flights().lock());
			}
			var self = client.player;
			var mate = client.level.getEntity(424_243);
			((net.minecraft.world.entity.player.Player) mate).setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
					net.minecraft.world.item.ItemStack.EMPTY);
			mate.snapTo(self.getX() + 2.0, self.getY(), self.getZ() - 2.0, 0.0f, 0.0f);
			var sundial = client.level.getEntity(424_242);
			sundial.snapTo(self.getX(), self.getY(), self.getZ() + 3.5, 180.0f, 0.0f);
			((net.minecraft.world.entity.player.Player) sundial).setYHeadRot(180.0f);
		});
		context.waitTick();
	}

	/**
	 * The callout hotkeys: aimed at Sundial (Yellow), "{team} inc" is "YELLOW inc"; with nobody aimed
	 * at it is refused; a message without placeholders goes out anyway; a second press while the first
	 * is still waiting sends nothing twice.
	 */
	private static void assertCallouts(ClientGameTestContext context, HypixelScout mod) {
		context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
		context.runOnClient(client -> {
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
			mod.settings().callouts.messages[2] = "Going mid";
		});
		context.waitTicks(2);
		context.runOnClient(client -> {
			mod.callouts().fire(0);
			mod.callouts().fire(0);
			mod.callouts().fire(1);
			List<String> lines = mod.partyReport().pendingLines();
			if (lines.size() != 2 || !lines.get(0).equals("YELLOW inc")
					|| !lines.get(1).equals("YELLOW Sundial inc - 1502* INSANE")) {
				throw new AssertionError("Unexpected callouts: " + lines);
			}
		});
		context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);

		context.runOnClient(client -> client.player.snapTo(client.player.getX(), client.player.getY(),
				client.player.getZ(), 180.0f, 0.0f));
		context.waitTicks(2);
		context.runOnClient(client -> {
			mod.callouts().fire(0);
			if (!mod.partyReport().pendingLines().isEmpty()) {
				throw new AssertionError("A callout about nobody: " + mod.partyReport().pendingLines());
			}
			mod.callouts().fire(2);
			if (!mod.partyReport().pendingLines().equals(List.of("Going mid"))) {
				throw new AssertionError("A callout without placeholders was not sent: " + mod.partyReport().pendingLines());
			}
			mod.settings().callouts.messages[2] = "{team} is rushing us";
			client.player.snapTo(client.player.getX(), client.player.getY(), client.player.getZ(), 0.0f, 0.0f);
		});
		context.waitFor(client -> mod.partyReport().pendingLines().isEmpty(), 400);
		context.waitTicks(3);
		context.takeScreenshot("scout_callouts_sent");
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
	 * Orchard (index about 770) is MED on the fixed bands but NONE against this team, which has a
	 * 1123-star carry in it; Brickmason is HIGH either way.
	 */
	private static void assertRelativeThreat(HypixelScout mod, List<Roster.Member> members) {
		var orchard = mod.stats().peek(uuidOf(members, "Orchard"));
		var brickmason = mod.stats().peek(uuidOf(members, "Brickmason"));

		if (!mod.threatScale().isRelative()) {
			throw new AssertionError("No relative threat scale although the own stats are known");
		}
		if (de.raindancer118.hypixelscout.ui.Threats.of(orchard) != de.raindancer118.hypixelscout.core.Threat.NONE
				|| de.raindancer118.hypixelscout.core.Threat.of(orchard) != de.raindancer118.hypixelscout.core.Threat.MEDIUM) {
			throw new AssertionError("Orchard: relative " + de.raindancer118.hypixelscout.ui.Threats.of(orchard)
					+ ", absolute " + de.raindancer118.hypixelscout.core.Threat.of(orchard));
		}
		if (de.raindancer118.hypixelscout.ui.Threats.of(brickmason) != de.raindancer118.hypixelscout.core.Threat.HIGH) {
			throw new AssertionError("Brickmason: " + de.raindancer118.hypixelscout.ui.Threats.of(brickmason));
		}

		// The sensitivity slider takes effect on the next read, without waiting for the rescan.
		mod.settings().threatSensitivity = 300;
		try {
			if (de.raindancer118.hypixelscout.ui.Threats.of(brickmason).compareTo(de.raindancer118.hypixelscout.core.Threat.VERY_HIGH) < 0) {
				throw new AssertionError("Brickmason at 300 %: " + de.raindancer118.hypixelscout.ui.Threats.of(brickmason));
			}
		} finally {
			mod.settings().threatSensitivity = 100;
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
