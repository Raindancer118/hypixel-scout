package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.FlightPath;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.ProjectileKind;
import de.raindancer118.hypixelscout.flight.Vec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Arrows and fireballs in the air: where each will fly, and whether one of them is about to hit the
 * player.
 *
 * <p>Everything here is read from what the client already has — the entities it was sent and the
 * blocks around them — and fed through the vanilla flight rules in {@link FlightPath}. The warning is
 * decided once per tick; the lines are drawn every frame from the same paths at the frame's
 * position, so they do not stutter.
 */
public final class Flights {
	/** How far ahead a path is followed: four seconds, longer than any arrow stays in the air. */
	private static final int PATH_TICKS = 80;
	/** Speeds below this are a projectile lying still (an arrow in a wall) or not yet sent. */
	private static final double STILL = 1e-3;

	/** One projectile in flight and where it goes. */
	public record Flying(Entity entity, ProjectileKind kind, FlightPath path, boolean mine) {
	}

	private final Roster roster;
	private final Supplier<ScoutSettings> settings;
	private final IncomingWatch watch = new IncomingWatch();
	private IncomingWatch.Warning warning;

	public Flights(Roster roster, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.settings = settings;
	}

	/** Whether anything is looked at here: a world, a player, and a game if only games are wanted. */
	public boolean active(Minecraft client) {
		return client.player != null && client.level != null
				&& (!settings.get().projectiles.onlyInGame || roster.isInGame());
	}

	public void tick(Minecraft client) {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		if (!options.alarm || !active(client)) {
			warning = null;
			watch.reset();
			return;
		}

		LocalPlayer player = client.player;
		List<IncomingWatch.Seen> seen = new ArrayList<>();
		for (Flying flying : flying(client.level, player, 1.0f)) {
			seen.add(new IncomingWatch.Seen(flying.entity().getId(), flying.kind(), vec(flying.entity().position()),
					flying.path(), flying.mine()));
		}

		warning = watch.update(seen, box(player.getBoundingBox()), vec(player.getEyePosition()),
				vec(player.getViewVector(1.0f)), Math.cos(halfViewAngle(client))).orElse(null);

		if (warning != null && warning.fresh() && options.sound) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 0.9f));
		}
	}

	/** The projectile about to hit the player, or {@code null}. */
	public IncomingWatch.Warning warning() {
		return warning;
	}

	public void reset() {
		warning = null;
		watch.reset();
	}

	/**
	 * Every arrow and fireball in flight the settings ask for, with its path from where it is at
	 * {@code partialTick} — 1 for the end of the tick, the frame's fraction for drawing.
	 */
	public List<Flying> flying(ClientLevel level, LocalPlayer player, float partialTick) {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		List<Flying> result = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			ProjectileKind kind = kindOf(entity);
			if (kind == null || (kind == ProjectileKind.ARROW && !options.arrows)
					|| (kind == ProjectileKind.FIREBALL && !options.fireballs)) {
				continue;
			}

			Vec3 motion = entity.getDeltaMovement();
			if (motion.lengthSqr() < STILL * STILL) {
				// Not sent any motion yet: what it moved since the last tick says the same.
				motion = entity.position().subtract(entity.oldPosition());
				if (motion.lengthSqr() < STILL * STILL) {
					continue;
				}
			}

			double acceleration = entity instanceof Fireball fireball ? fireball.accelerationPower : 0.0;
			FlightPath path = FlightPath.predict(kind, vec(entity.getPosition(partialTick)), vec(motion), acceleration,
					PATH_TICKS, obstacle(level, entity));
			result.add(new Flying(entity, kind, path, isMine(entity, player)));
		}
		return result;
	}

	/**
	 * Where a fireball thrown right now would fly, or {@code null} when the player holds no fire
	 * charge. Straight along the view, from the eyes, until the first block.
	 */
	public FlightPath aim(ClientLevel level, LocalPlayer player, float partialTick) {
		if (!settings.get().projectiles.aim || !holdsFireCharge(player)) {
			return null;
		}

		Vec3 look = player.getViewVector(partialTick);
		return FlightPath.predict(ProjectileKind.FIREBALL, vec(player.getEyePosition(partialTick)), vec(look), 0.1,
				PATH_TICKS, obstacle(level, player));
	}

	public static boolean holdsFireCharge(LocalPlayer player) {
		return player.getMainHandItem().is(Items.FIRE_CHARGE) || player.getOffhandItem().is(Items.FIRE_CHARGE);
	}

	private static ProjectileKind kindOf(Entity entity) {
		if (entity instanceof AbstractArrow) {
			return ProjectileKind.ARROW;
		}
		if (entity instanceof Fireball) {
			return ProjectileKind.FIREBALL;
		}
		return null;
	}

	private static boolean isMine(Entity entity, LocalPlayer player) {
		return entity instanceof net.minecraft.world.entity.projectile.Projectile projectile
				&& projectile.getOwner() == player;
	}

	/** The blocks the projectile collides with, as vanilla's own flight code asks for them. */
	private static FlightPath.Obstacle obstacle(ClientLevel level, Entity entity) {
		return (from, to) -> {
			BlockHitResult hit = level.clip(new ClipContext(vec3(from), vec3(to), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, entity));
			return hit.getType() == HitResult.Type.MISS ? null : vec(hit.getLocation());
		};
	}

	/**
	 * Half the horizontal field of view, in radians: the options hold the vertical one, the window's
	 * shape gives the rest.
	 */
	private static double halfViewAngle(Minecraft client) {
		double vertical = Math.toRadians(client.options.fov().get());
		double aspect = (double) client.getWindow().getWidth() / Math.max(1, client.getWindow().getHeight());
		return Math.atan(Math.tan(vertical / 2) * aspect);
	}

	public static Vec vec(Vec3 value) {
		return new Vec(value.x, value.y, value.z);
	}

	public static Vec3 vec3(Vec value) {
		return new Vec3(value.x(), value.y(), value.z());
	}

	private static Box box(AABB aabb) {
		return new Box(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
	}

	/** The player's hitbox, for telling which drawn path is the dangerous one. */
	public static Box hitbox(LocalPlayer player) {
		return box(player.getBoundingBox());
	}
}
