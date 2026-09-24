package de.raindancer118.hypixelscout.startup;

import com.mojang.authlib.GameProfile;
import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Phase 2b of this branch's port (see {@code Project.md}): projectile awareness ({@code
 * game.Flights}), hazard awareness ({@code game.Hazards}, the bed ledger) and cheat detection
 * ({@code game.CheatSensor}), proven the same way {@link HudScreenshotCheck} proves the HUD — real
 * entities and real blocks in the singleplayer world {@link WorldEntryCheck} already entered, ticked
 * by the mod's own real {@code TickEvent.ClientTickEvent} handler, not by calling any of this
 * module's {@code tick()} methods directly.
 *
 * <p>Runs after {@link HudScreenshotCheck} so it is free to move the fake players that check staged
 * without disturbing the HUD screenshots' framing.
 */
public final class WorldAndCheatsCheck implements StartupCheck {
	private enum Phase {
		SPAWN_ARROW, CHECK_FLIGHT, PLACE_LOOKED_AT_BED, CHECK_BED, BUILD_NUKER_ROOM, BREAK_NUKER_BED, CHECK_NUKER,
		WORLD_LINES_SCREENSHOT, DONE
	}

	/** Far enough from spawn, and from each other, that none of these three scenes overlap. */
	/**
	 * Where the sealed bed goes, relative to the player: inside the loaded chunks (the client
	 * ignores blocks and entities placed in chunks it does not have) and more than the bed reach
	 * away from everybody else staged so far, so the breaker is the only one near it.
	 */
	private static final int NUKER_OFFSET = -16;

	private Phase phase = Phase.SPAWN_ARROW;
	private int ticksInPhase;
	private BlockPos nukerHead;

	@Override
	public String name() {
		return "world-and-cheats";
	}

	@Override
	public boolean tick() throws IOException {
		HypixelScout mod = HypixelScout.get();
		Minecraft client = Minecraft.getMinecraft();
		ticksInPhase++;

		switch (phase) {
			case SPAWN_ARROW:
				spawnIncomingArrow(client);
				return advance();

			case CHECK_FLIGHT:
				if (ticksInPhase < 5) {
					return false;
				}
				if (mod.flights().warning() == null) {
					throw new IllegalStateException("Flights produced no warning for an arrow flying straight at the player");
				}
				return advance();

			case PLACE_LOOKED_AT_BED:
				placeLookedAtBed(client);
				return advance();

			case CHECK_BED:
				if (ticksInPhase < 3) {
					return false;
				}
				if (mod.hazards().bed() == null) {
					throw new IllegalStateException("Hazards did not pick up the bed the player was made to look at");
				}
				if (mod.hazards().ledger().entries().isEmpty()) {
					throw new IllegalStateException("looking at a bed did not add an entry to the bed ledger");
				}
				return advance();

			case BUILD_NUKER_ROOM:
				buildNukerRoom(client);
				return advance();

			case BREAK_NUKER_BED:
				if (ticksInPhase < 3) {
					// Let CheatSensor.tick() record a couple of frames for the breaker standing next to
					// the bed before the "break" packet arrives, the way a real player's presence would.
					return false;
				}
				breakNukerBed(client);
				return advance();

			case CHECK_NUKER:
				if (ticksInPhase < 5) {
					return false;
				}
				if (mod.cheats().suspicion().flagged().isEmpty()) {
					Minecraft mc = Minecraft.getMinecraft();
					StringBuilder around = new StringBuilder();
					for (BlockPos pos : BlockPos.getAllInBox(nukerHead.add(-1, -1, -1), nukerHead.add(1, 1, 2))) {
						around.append(mc.theWorld.getBlockState(pos).getBlock().getLocalizedName().charAt(0));
					}
					throw new IllegalStateException("breaking a fully enclosed bed next to a tracked player "
							+ "raised no CheatSensor flag (expected a NUKER flag); roster=" + mod.roster().members()
							+ " players=" + mc.theWorld.playerEntities + " around=" + around
							+ " seen=" + mod.cheats().suspicion().suspects());
				}
				boolean nukerFlag = false;
				for (de.raindancer118.hypixelscout.cheat.Suspicion.Flag flag : mod.cheats().suspicion().flagged()) {
					if (flag.check() == de.raindancer118.hypixelscout.cheat.Check.NUKER) {
						nukerFlag = true;
					}
				}
				if (!nukerFlag) {
					throw new IllegalStateException("expected a NUKER flag, got: " + mod.cheats().suspicion().flagged());
				}
				return advance();

			case WORLD_LINES_SCREENSHOT:
				if (ticksInPhase < 3) {
					return false;
				}
				screenshot(client, "hypixelscout-world-lines.png");
				return advance();

			default:
				return true;
		}
	}

	private boolean advance() {
		Phase[] values = Phase.values();
		phase = values[phase.ordinal() + 1];
		ticksInPhase = 0;
		return phase == Phase.DONE;
	}

	/** A real arrow, well outside the excused "just seen, very close" radius, aimed straight at the eyes. */
	private static void spawnIncomingArrow(Minecraft client) {
		net.minecraft.util.Vec3 eyes = client.thePlayer.getPositionEyes(1.0f);
		double x = eyes.xCoord + 15;
		double y = eyes.yCoord;
		double z = eyes.zCoord;
		EntityArrow arrow = new EntityArrow(client.theWorld, x, y, z);
		double dx = eyes.xCoord - x;
		double dy = eyes.yCoord - y;
		double dz = eyes.zCoord - z;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double speed = 3.0;
		arrow.motionX = dx / length * speed;
		arrow.motionY = dy / length * speed;
		arrow.motionZ = dz / length * speed;
		client.theWorld.spawnEntityInWorld(arrow);
	}

	/** A bed placed exactly where the player is turned to look, so {@code Hazards.bed()} picks it up. */
	private static void placeLookedAtBed(Minecraft client) {
		// Next to where the player already stands: a client-side jump of hundreds of blocks lands
		// in chunks the client does not have, and the server pulls the player straight back.
		BlockPos foot = new BlockPos(client.thePlayer).add(3, 0, 3);
		BlockPos head = foot.north();
		IBlockState bedState = Blocks.bed.getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.NORTH);
		client.theWorld.setBlockState(foot, bedState.withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT));
		client.theWorld.setBlockState(head, bedState.withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));

		// Stand the player right beside it, looking straight down onto the foot of the bed.
		double eyeX = foot.getX() + 0.5;
		double eyeZ = foot.getZ() + 0.5;
		client.thePlayer.setPositionAndRotation(eyeX, foot.getY() + 2.0, eyeZ, 0f, 90f);
		client.thePlayer.prevRotationYaw = client.thePlayer.rotationYaw;
		client.thePlayer.prevRotationPitch = client.thePlayer.rotationPitch;
	}

	/**
	 * A bed sealed on every side by stone (so {@code BedDefense.analyse} reports it defended, not
	 * open) with a tracked fake player standing right beside it — everything {@code CheatWatch}'s
	 * bed-nuker check needs to blame that one player once the bed disappears.
	 */
	private void buildNukerRoom(Minecraft client) {
		BlockPos foot = new BlockPos(client.thePlayer).add(NUKER_OFFSET, 0, NUKER_OFFSET);
		BlockPos head = foot.north();
		nukerHead = head;
		IBlockState bedState = Blocks.bed.getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.NORTH);
		client.theWorld.setBlockState(foot, bedState.withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT));
		client.theWorld.setBlockState(head, bedState.withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));

		// A solid stone shell one block out on every side of both bed cells (wider than
		// BedDefense.RADIUS is not needed — analyse only looks at the cells right around the bed).
		// head is north of foot (smaller z), so the box runs from head's corner to foot's.
		for (BlockPos pos : BlockPos.getAllInBox(head.add(-1, -1, -1), foot.add(1, 1, 1))) {
			if (pos.equals(foot) || pos.equals(head)) {
				continue;
			}
			client.theWorld.setBlockState(pos, Blocks.stone.getDefaultState());
		}
		// Fully sealed: a bed with an open face is fair game, only one behind its defence is a nuker.

		UUID breakerUuid = UUID.nameUUIDFromBytes("hypixelscout-startup-test:Duskrunner".getBytes());
		GameProfile profile = new GameProfile(breakerUuid, "Duskrunner");
		EntityOtherPlayerMP breaker = new EntityOtherPlayerMP(client.theWorld, profile);
		// Just outside the shell, well within reach of the bed.
		breaker.setPositionAndRotation(foot.getX() + 0.5, foot.getY(), foot.getZ() + 2.5, 180f, 0f);
		client.theWorld.spawnEntityInWorld(breaker);

		// Duskrunner joins the roster CheatSensor.watched() already trusts, alongside everybody
		// HudSetupCheck staged earlier — replaceForTest swaps the whole tab list, so the existing
		// members are carried over rather than dropped.
		final java.util.List<de.raindancer118.hypixelscout.core.Roster.Member> members =
				new java.util.ArrayList<de.raindancer118.hypixelscout.core.Roster.Member>(HypixelScout.get().roster().members());
		members.add(new de.raindancer118.hypixelscout.core.Roster.Member("Duskrunner", breakerUuid));
		de.raindancer118.hypixelscout.game.TabListReader.replaceForTest(
				new java.util.function.Supplier<java.util.Collection<de.raindancer118.hypixelscout.core.Roster.Member>>() {
					@Override
					public java.util.Collection<de.raindancer118.hypixelscout.core.Roster.Member> get() {
						return members;
					}
				});
		HypixelScout.get().roster().refresh(de.raindancer118.hypixelscout.game.TabListReader.current());
	}

	/** The synthetic event: {@code CheatSensor.onBlock} sees the bed vanish, exactly as the packet mixin would. */
	private void breakNukerBed(Minecraft client) {
		Block airBlock = Blocks.air;
		de.raindancer118.hypixelscout.game.CheatSensor.onBlock(nukerHead, airBlock.getDefaultState());
	}

	private static void screenshot(Minecraft client, String filename) throws IOException {
		ScreenShotHelper.saveScreenshot(client.mcDataDir, filename, client.displayWidth, client.displayHeight,
				client.getFramebuffer());
		File screenshotsDir = new File(client.mcDataDir, "screenshots");
		if (!new File(screenshotsDir, filename).isFile()) {
			throw new IOException("no screenshot appeared at " + new File(screenshotsDir, filename));
		}
	}
}
