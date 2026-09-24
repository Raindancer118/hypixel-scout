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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the flight paths into the world, as vanilla's own debug lines ("gizmos").
 *
 * <p>Handed over at the end of the frame's extraction, collected into the level renderer's per-frame
 * gizmos and drawn with the rest of the level — occluded by blocks like anything else, so nothing is
 * shown through walls that the player could not see.
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
		LevelExtractionEvents.END_EXTRACTION.register(this::extract);
	}

	private void extract(LevelExtractionContext context) {
		Minecraft client = Minecraft.getInstance();
		ScoutSettings.Projectiles options = settings.get().projectiles;
		LockWatch.Lock lock = flights.lock();
		if ((!options.paths && !options.aim && !options.pearlAim && !options.bowAim && lock == null)
				|| !flights.active(client)) {
			return;
		}

		LocalPlayer player = client.player;
		float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);

		try (Gizmos.TemporaryCollection ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
			if (options.paths) {
				Box self = Flights.hitbox(player);
				for (Flights.Flying flying : flights.flying(context.level(), player, partialTick)) {
					boolean atMe = !flying.mine() && flying.path().ticksUntil(self.inflate(flying.kind().reach())) >= 0;
					int colour = atMe ? AT_ME : switch (flying.kind()) {
						case FIREBALL -> FIREBALL;
						case PEARL -> PEARL;
						case ARROW -> ARROW;
					};
					drawPath(flying.path(), colour);
				}
			}

			if (lock != null) {
				drawLock(lock);
			}

			Flights.Aim aim = flights.aimAny(context.level(), player, partialTick);
			if (aim != null) {
				drawAim(aim, player, options.blastPreview);
			}
		}
	}

	private static void drawPath(FlightPath path, int colour) {
		List<Vec> points = path.points();
		for (int i = 1; i < points.size(); i++) {
			Gizmos.line(Flights.vec3(points.get(i - 1)), Flights.vec3(points.get(i)), colour, WIDTH);
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
	private static void drawAim(Flights.Aim aim, LocalPlayer player, boolean blastPreview) {
		List<Vec> points = aim.path().points();
		if (points.size() < 2) {
			return;
		}

		int colour = aim.kind() == ProjectileKind.PEARL ? PEARL : ScoutTheme.accent(0xE0);
		Vec3 previous = Flights.vec3(points.getFirst()).add(player.getViewVector(1.0f).scale(0.6)).add(0, -0.12, 0);
		for (int i = 1; i < points.size(); i++) {
			Vec3 next = Flights.vec3(points.get(i));
			if (next.distanceToSqr(Flights.vec3(points.getFirst())) > 0.36) {
				Gizmos.line(previous, next, colour, WIDTH);
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
			Gizmos.circle(end, radius, GizmoStyle.strokeAndFill(ring, WIDTH, (ring & 0xFFFFFF) | 0x28000000));
		}
	}

	/** Whether an enemy's hitbox is within a fireball blast's reach of {@code at}. */
	private static boolean enemyInBlast(LocalPlayer player, Vec at) {
		for (var other : player.level().players()) {
			if (other != player && !other.isSpectator() && !Teams.isOwnTeam(other.getScoreboardName())
					&& Flights.box(other.getBoundingBox()).distanceTo(at) <= ProjectileKind.FIREBALL.blast()) {
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
		Vec3 start = from.add(point.subtract(from).normalize().scale(0.6)).add(0, -0.12, 0);
		Gizmos.line(start, point, LOCK, WIDTH);
		Gizmos.cuboid(new AABB(point, point).inflate(0.35), GizmoStyle.strokeAndFill(LOCK, WIDTH, 0x50FFB020));
	}

	private static void marker(Vec3 at, int colour) {
		Gizmos.cuboid(new AABB(at, at).inflate(0.25), GizmoStyle.strokeAndFill(colour, WIDTH, (colour & 0xFFFFFF) | 0x40000000));
	}
}
