package de.raindancer118.hypixelscout.startup;

import de.raindancer118.hypixelscout.game.LookTarget;
import net.minecraft.entity.player.EntityPlayer;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ScreenShotHelper;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Screenshots the table, the tab list, the look tooltip, the proximity popup and the peek overlay
 * once {@link HudSetupCheck}'s fake round has everybody looked up — and checks a handful of things
 * a screenshot alone would not catch, the way 26.2's client game test does: row counts, and that
 * nobody was asked about twice.
 *
 * <p>A small tick-driven state machine, since Forge 1.8.9 has no {@code TestHelper} to {@code
 * runAtTickTime} with: each phase either waits for a condition or holds a key down (via {@link
 * KeyBinding#setKeyBindState}, the standard headless way to simulate a held key without real LWJGL
 * input) for long enough that the next frame's HUD render sees it.
 */
public final class HudScreenshotCheck implements StartupCheck {
	/** 4 seconds at 20 ticks/s: long enough for the periodic scoreboard scan (every 20 ticks) to
	 * flip {@code teamsReady} and for every stub-backed lookup to answer. */
	private static final int WAIT_TICKS = 80;
	private static final int HOLD_TICKS = 3;

	private enum Phase {
		WAIT_STATS, TABLE, TABLE_SHOT, OPEN_TAB, TAB_LIST, CLOSE_TAB, TOOLTIP, PROXIMITY, OPEN_PEEK, PEEK, CLOSE_PEEK, DONE
	}

	private final HudSetupCheck setup;
	private Phase phase = Phase.WAIT_STATS;
	private int ticksInPhase;

	public HudScreenshotCheck(HudSetupCheck setup) {
		this.setup = setup;
	}

	@Override
	public String name() {
		return "hud-screenshots";
	}

	@Override
	public boolean tick() throws IOException {
		HypixelScout mod = HypixelScout.get();
		Minecraft client = Minecraft.getMinecraft();
		ticksInPhase++;

		switch (phase) {
			case WAIT_STATS:
				if (ticksInPhase < WAIT_TICKS && !everybodyAnswered(mod)) {
					return false;
				}
				checkRowsAndRequests(mod);
				return advance();

			case TABLE:
				mod.table().toggle();
				return advance();

			case TABLE_SHOT:
				if (ticksInPhase < HOLD_TICKS) {
					return false;
				}
				screenshot(client, "hypixelscout-hud-table.png");
				mod.table().toggle();
				return advance();

			case OPEN_TAB:
				KeyBinding.setKeyBindState(client.gameSettings.keyBindPlayerList.getKeyCode(), true);
				return advance();

			case TAB_LIST:
				if (ticksInPhase < HOLD_TICKS) {
					return false;
				}
				screenshot(client, "hypixelscout-hud-tab-list.png");
				return advance();

			case CLOSE_TAB:
				KeyBinding.setKeyBindState(client.gameSettings.keyBindPlayerList.getKeyCode(), false);
				return advance();

			case TOOLTIP:
				// Aimed now rather than trusting HudSetupCheck's rotation: the server's own position
				// and look packets after joining may have turned the player since.
				if (ticksInPhase == 1) {
					aimAt(client, "Brickmason");
				}
				if (ticksInPhase < HOLD_TICKS) {
					return false;
				}
				EntityPlayer target = LookTarget.pick(mod.settings().tooltip.cosine(),
						mod.settings().tooltip.throughWalls);
				if (target == null || !"Brickmason".equals(target.getName())) {
					throw new IllegalStateException("the crosshair should pick Brickmason for the look tooltip, "
							+ "picked " + (target == null ? "nobody" : target.getName()));
				}
				screenshot(client, "hypixelscout-hud-look-tooltip.png");
				return advance();

			case PROXIMITY:
				if (ticksInPhase < HOLD_TICKS || mod.proximityElement().shown().isEmpty()) {
					if (ticksInPhase > WAIT_TICKS) {
						throw new IllegalStateException("no proximity popup appeared for the nearby enemies");
					}
					return false;
				}
				screenshot(client, "hypixelscout-hud-proximity.png");
				return advance();

			case OPEN_PEEK:
				KeyBinding.setKeyBindState(mod.keys().peekBinding().getKeyCode(), true);
				return advance();

			case PEEK:
				if (ticksInPhase < HOLD_TICKS) {
					return false;
				}
				screenshot(client, "hypixelscout-hud-peek.png");
				return advance();

			case CLOSE_PEEK:
				KeyBinding.setKeyBindState(mod.keys().peekBinding().getKeyCode(), false);
				if (setup.stub() != null) {
					setup.stub().close();
				}
				phase = Phase.DONE;
				return true;

			default:
				return true;
		}
	}

	private boolean advance() {
		Phase[] values = Phase.values();
		phase = values[phase.ordinal() + 1];
		ticksInPhase = 0;
		return false;
	}

	private static boolean everybodyAnswered(HypixelScout mod) {
		for (Roster.Member member : mod.roster().members()) {
			if (mod.stats().isPending(member.uuid())) {
				return false;
			}
		}
		return true;
	}

	/** Row counts, and that the stub was never asked about the same player twice. */
	private void checkRowsAndRequests(HypixelScout mod) {
		List<Roster.Member> members = mod.roster().members();
		if (members.size() != 4) {
			throw new IllegalStateException("expected 4 roster members from the staged tab list, got "
					+ members.size() + ": " + members);
		}

		int withStats = 0;
		for (Roster.Member member : members) {
			PlayerStats stats = mod.stats().peek(member.uuid());
			if (stats != null && !stats.isNicked()) {
				withStats++;
			}
		}
		// Ashenvale, Brickmason and Lanternfish are in the stub; Shadowfox, the nick, is not.
		if (withStats != 3) {
			throw new IllegalStateException("expected 3 members with real stats (every stub-seeded "
					+ "player, not the nick), got " + withStats);
		}

		HypixelStub stub = setup.stub();
		if (stub == null) {
			throw new IllegalStateException("HudSetupCheck never created its stub");
		}
		// Every stub-backed uuid asked about exactly once: markStarted() looked everybody up right
		// away, and nothing here should have asked Hypixel again since.
		int expectedRequests = 3; // Ashenvale, Brickmason, Lanternfish — Shadowfox is a nick, never asked.
		if (stub.asked.size() != expectedRequests || stub.playerRequests.get() != expectedRequests) {
			throw new IllegalStateException("expected exactly " + expectedRequests + " distinct/total "
					+ "Hypixel requests (no duplicates), got " + stub.asked.size() + " distinct, "
					+ stub.playerRequests.get() + " total: " + stub.asked);
		}
	}

	private static void aimAt(Minecraft client, String name) {
		EntityPlayer target = client.theWorld.getPlayerEntityByName(name);
		if (target == null) {
			throw new IllegalStateException(name + " is not in the world");
		}
		double dx = target.posX - client.thePlayer.posX;
		double dz = target.posZ - client.thePlayer.posZ;
		double dy = target.posY + target.getEyeHeight() - (client.thePlayer.posY + client.thePlayer.getEyeHeight());
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		client.thePlayer.rotationYaw = client.thePlayer.prevRotationYaw = yaw;
		client.thePlayer.rotationPitch = client.thePlayer.prevRotationPitch = pitch;
	}

	private static void screenshot(Minecraft client, String filename) throws IOException {
		// A menu on top hides every HUD element; a picture of it would prove nothing.
		if (client.currentScreen != null) {
			throw new IllegalStateException("a screen is open (" + client.currentScreen.getClass().getSimpleName()
					+ ") instead of the HUD for " + filename);
		}
		ScreenShotHelper.saveScreenshot(client.mcDataDir, filename, client.displayWidth, client.displayHeight,
				client.getFramebuffer());

		File screenshotsDir = new File(client.mcDataDir, "screenshots");
		if (!new File(screenshotsDir, filename).isFile()) {
			throw new IOException("no screenshot appeared at " + new File(screenshotsDir, filename));
		}
	}
}
