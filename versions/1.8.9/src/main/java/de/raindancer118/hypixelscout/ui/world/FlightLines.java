package de.raindancer118.hypixelscout.ui.world;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.FlightPath;
import de.raindancer118.hypixelscout.flight.LockWatch;
import de.raindancer118.hypixelscout.flight.ProjectileKind;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the flight paths into the world, by hand with GL lines ({@link WorldLines}) — 1.8.9's
 * equivalent of 26.2's vanilla-gizmo drawing in {@code ui.world.FlightLines}, same colours and
 * toggles.
 */
public final class FlightLines {
	private static final int ARROW = 0xC8FFFFFF;
	private static final int FIREBALL = 0xD0FF8C1A;
	private static final int AT_ME = 0xF0FF3B3B;
	private static final int LOCK = 0xF0FFB020;
	private static final int PEARL = 0xE0B060FF;
	private static final float WIDTH = 2.5f;

	private final Flights flights;
	private final Supplier<ScoutSettings> settings;

	public FlightLines(Flights flights, Supplier<ScoutSettings> settings) {
		this.flights = flights;
		this.settings = settings;
	}

	public void register() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onRenderWorldLast(RenderWorldLastEvent event) {
		Minecraft client = Minecraft.getMinecraft();
		ScoutSettings.Projectiles options = settings.get().projectiles;
		LockWatch.Lock lock = flights.lock();
		if ((!options.paths && !options.aim && !options.pearlAim && !options.bowAim && lock == null)
				|| !flights.active(client)) {
			return;
		}

		EntityPlayerSP player = client.thePlayer;
		float partialTick = event.partialTicks;
		double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTick;
		double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTick;
		double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTick;

		WorldLines.begin(camX, camY, camZ);
		try {
			if (options.paths) {
				Box self = Flights.hitbox(player);
				for (Flights.Flying flying : flights.flying(client.theWorld, player, partialTick)) {
					boolean atMe = !flying.mine() && flying.path().ticksUntil(self.inflate(flying.kind().reach())) >= 0;
					int colour;
					if (atMe) {
						colour = AT_ME;
					} else {
						switch (flying.kind()) {
							case FIREBALL:
								colour = FIREBALL;
								break;
							case PEARL:
								colour = PEARL;
								break;
							default:
								colour = ARROW;
						}
					}
					drawPath(flying.path(), colour);
				}
			}

			if (lock != null) {
				drawLock(lock);
			}

			Flights.Aim aim = flights.aimAny(client.theWorld, player, partialTick);
			if (aim != null) {
				drawAim(aim, player, options.blastPreview);
			}
		} finally {
			WorldLines.end();
		}
	}

	private static void drawPath(FlightPath path, int colour) {
		List<Vec> points = path.points();
		for (int i = 1; i < points.size(); i++) {
			WorldLines.line(Flights.vec3(points.get(i - 1)), Flights.vec3(points.get(i)), colour, WIDTH);
		}
		if (path.blocked()) {
			marker(Flights.vec3(path.end()), colour);
		}
	}

	/**
	 * The own throw: from a little in front of the eyes — a line starting in the eye would be a dot —
	 * along the path to where it would hit, with the spot marked. A fireball's spot gets its blast's
	 * reach around it, red when an enemy stands in it; a pearl's is where the player would appear.
	 */
	private static void drawAim(Flights.Aim aim, EntityPlayerSP player, boolean blastPreview) {
		List<Vec> points = aim.path().points();
		if (points.size() < 2) {
			return;
		}

		int colour = aim.kind() == ProjectileKind.PEARL ? PEARL : ScoutTheme.accent(0xE0);
		Vec3 look = player.getLook(1.0f);
		Vec3 previous = Flights.vec3(points.get(0)).addVector(look.xCoord * 0.6, look.yCoord * 0.6 - 0.12,
				look.zCoord * 0.6);
		for (int i = 1; i < points.size(); i++) {
			Vec3 next = Flights.vec3(points.get(i));
			if (next.squareDistanceTo(Flights.vec3(points.get(0))) > 0.36) {
				WorldLines.line(previous, next, colour, WIDTH);
				previous = next;
			}
		}

		if (!aim.path().blocked()) {
			return;
		}
		Vec3 end = Flights.vec3(aim.path().end());
		marker(end, colour);
		if (aim.kind() == ProjectileKind.FIREBALL && blastPreview) {
			float radius = (float) ProjectileKind.FIREBALL.blast();
			int ring = enemyInBlast(player, aim.path().end()) ? AT_ME : colour;
			WorldLines.circle(end, radius, ring, WIDTH);
		}
	}

	/** Whether an enemy's hitbox is within a fireball blast's reach of {@code at}. */
	private static boolean enemyInBlast(EntityPlayerSP player, Vec at) {
		for (EntityPlayer other : player.worldObj.playerEntities) {
			if (other != player && !other.isSpectator() && !Teams.isOwnTeam(other.getName())
					&& Flights.box(other.getEntityBoundingBox()).distanceTo(at) <= ProjectileKind.FIREBALL.blast()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The lock: a line from the one aiming to the spot their fireball would reach — on the player's
	 * body, or the block it would blow up on — and that spot marked, larger than a path's end.
	 */
	private static void drawLock(LockWatch.Lock lock) {
		Vec3 from = Flights.vec3(lock.from());
		Vec3 point = Flights.vec3(lock.point());
		Vec3 direction = point.subtract(from).normalize();
		Vec3 start = from.addVector(direction.xCoord * 0.6, direction.yCoord * 0.6 - 0.12, direction.zCoord * 0.6);
		WorldLines.line(start, point, LOCK, WIDTH);
		WorldLines.box(new AxisAlignedBB(point.xCoord, point.yCoord, point.zCoord, point.xCoord, point.yCoord,
				point.zCoord).expand(0.35, 0.35, 0.35), LOCK, WIDTH);
	}

	private static void marker(Vec3 at, int colour) {
		WorldLines.box(new AxisAlignedBB(at.xCoord, at.yCoord, at.zCoord, at.xCoord, at.yCoord, at.zCoord)
				.expand(0.25, 0.25, 0.25), colour, WIDTH);
	}
}
