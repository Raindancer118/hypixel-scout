package de.raindancer118.hypixelscout.startup;

import net.minecraft.scoreboard.IScoreObjectiveCriteria;

import com.mojang.authlib.GameProfile;
import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.game.TabListReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stages a fake Bedwars round for the HUD screenshots (Phase 2a, this branch — see
 * {@code Project.md}): four fake enemies/teammates as real {@link EntityOtherPlayerMP} entities
 * (so {@code game.LookTarget} and {@code game.ProximityAlerts}, which both read {@code
 * theWorld.playerEntities}, see them), scoreboard teams for {@code game.Teams}, a fixed roster via
 * {@link TabListReader#replaceForTest} (so nothing depends on a real tab list packet), and a local
 * {@link HypixelStub} the mod's real {@code HypixelClient}/{@code MojangClient} are pointed at —
 * the same idea 26.2's {@code gametest.HypixelStub}-backed client game test uses.
 *
 * <p>One-shot: {@link #tick()} does the whole thing and returns {@code true} at once. Runs after
 * {@link WorldEntryCheck}, so a world and a player already exist to place the fakes around.
 */
public final class HudSetupCheck implements StartupCheck {
	private static final String MODE = "BEDWARS_FOUR_FOUR";
	private static final String MAP = "Startup Test Island";

	private HypixelStub stub;

	@Override
	public String name() {
		return "hud-setup";
	}

	@Override
	public boolean tick() throws IOException {
		HypixelScout mod = HypixelScout.get();
		if (mod == null) {
			throw new IllegalStateException("HypixelScout.get() is null — onInit never ran");
		}

		Minecraft client = Minecraft.getMinecraft();
		// A known, level look direction: south, horizontal — so a fake player placed along it is
		// guaranteed to be under the crosshair for the look-tooltip screenshot.
		client.thePlayer.rotationYaw = 0f;
		// The virtual display never has focus: without this the game pauses into its menu, which
		// covers the whole HUD. Daylight, so the screenshots are readable.
		client.gameSettings.pauseOnLostFocus = false;
		client.displayGuiScreen(null);
		client.getIntegratedServer().worldServers[0].setWorldTime(6000L);
		client.thePlayer.rotationPitch = 0f;
		client.thePlayer.prevRotationYaw = 0f;
		client.thePlayer.prevRotationPitch = 0f;

		Vec3 eyes = client.thePlayer.getPositionEyes(1.0F);
		Vec3 look = client.thePlayer.getLook(1.0F);

		UUID teammateUuid = fakeUuid("Ashenvale");
		UUID lookedAtUuid = fakeUuid("Brickmason");
		UUID nearbyUuid = fakeUuid("Lanternfish");
		UUID nickUuid = fakeUuid("Shadowfox");

		// Directly under the crosshair, five blocks out — the look tooltip's target.
		spawn(client, "Brickmason", lookedAtUuid,
				eyes.xCoord + look.xCoord * 5, eyes.yCoord + look.yCoord * 5 - client.thePlayer.getEyeHeight(),
				eyes.zCoord + look.zCoord * 5);
		// Off to the side but still inside the proximity radius (12 blocks, see ScoutSettings.Proximity).
		spawn(client, "Lanternfish", nearbyUuid, client.thePlayer.posX + 6, client.thePlayer.posY,
				client.thePlayer.posZ - 6);
		// The player's own teammate: shown in the table, never in a proximity popup.
		spawn(client, "Ashenvale", teammateUuid, client.thePlayer.posX - 4, client.thePlayer.posY,
				client.thePlayer.posZ - 4);
		// Far outside the proximity radius and never given a Hypixel/Mojang account: a nick row.
		spawn(client, "Shadowfox", nickUuid, client.thePlayer.posX + 60, client.thePlayer.posY,
				client.thePlayer.posZ + 60);

		Scoreboard scoreboard = client.theWorld.getScoreboard();
		ScorePlayerTeam red = scoreboard.createTeam("Red");
		red.setChatFormat(EnumChatFormatting.RED);
		scoreboard.addPlayerToTeam(client.thePlayer.getName(), "Red");
		scoreboard.addPlayerToTeam("Ashenvale", "Red");

		// Vanilla draws the tab list in singleplayer only with an objective in its slot (0); on a real
		// server there are simply other players. Without it the tab list step would photograph nothing.
		scoreboard.setObjectiveInDisplaySlot(0, scoreboard.addScoreObjective("hs_list", IScoreObjectiveCriteria.DUMMY));

		ScorePlayerTeam blue = scoreboard.createTeam("Blue");
		blue.setChatFormat(EnumChatFormatting.BLUE);
		scoreboard.addPlayerToTeam("Brickmason", "Blue");
		scoreboard.addPlayerToTeam("Lanternfish", "Blue");
		scoreboard.addPlayerToTeam("Shadowfox", "Blue");

		final List<Roster.Member> roster = new ArrayList<Roster.Member>();
		roster.add(new Roster.Member("Ashenvale", teammateUuid));
		roster.add(new Roster.Member("Brickmason", lookedAtUuid));
		roster.add(new Roster.Member("Lanternfish", nearbyUuid));
		roster.add(new Roster.Member("Shadowfox", nickUuid));
		TabListReader.replaceForTest(new Supplier<Collection<Roster.Member>>() {
			@Override
			public Collection<Roster.Member> get() {
				return roster;
			}
		});

		stub = new HypixelStub();
		stub.add(teammateUuid, new HypixelStub.Player("Ashenvale", 731, 15_200, 1_380, 3_120, 890, 61,
				"MVP_PLUS", false));
		stub.add(lookedAtUuid, new HypixelStub.Player("Brickmason", 488, 6_900, 1_150, 1_730, 690, 12,
				"MVP_PLUS", false));
		stub.add(nearbyUuid, new HypixelStub.Player("Lanternfish", 212, 2_120, 1_700, 610, 540, null,
				"VIP_PLUS", false));
		// Shadowfox is never added: no Hypixel entry and no Mojang account, exactly what a nick is.

		mod.client().setBaseUrl(stub.url());
		mod.client().setApiKey(HypixelStub.KEY);
		mod.mojang().setBaseUrl(stub.url());
		mod.settings().tab.enabled = true;

		mod.startGameForTest(MODE, MAP);
		// Deterministic rather than waiting on the periodic scoreboard heuristic in HypixelScout.tick():
		// every member above is looked up at once.
		mod.roster().markStarted();

		return true;
	}

	private static void spawn(Minecraft client, String name, UUID uuid, double x, double y, double z) {
		GameProfile profile = new GameProfile(uuid, name);
		EntityOtherPlayerMP entity = new EntityOtherPlayerMP(client.theWorld, profile);
		entity.setPositionAndRotation(x, y, z, 180f, 0f);
		client.theWorld.spawnEntityInWorld(entity);
	}

	private static UUID fakeUuid(String name) {
		return UUID.nameUUIDFromBytes(("hypixelscout-startup-test:" + name).getBytes());
	}

	/** So the stub's HTTP server is not left bound past the end of the test. */
	HypixelStub stub() {
		return stub;
	}
}
