package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.FlightPath;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.LockWatch;
import de.raindancer118.hypixelscout.flight.MissileAlarm;
import de.raindancer118.hypixelscout.flight.ProjectileKind;
import de.raindancer118.hypixelscout.flight.Throw;
import de.raindancer118.hypixelscout.flight.Vec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
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
 * player — or whether somebody is aiming a fire charge at the player, before anything is thrown.
 *
 * <p>Everything here is read from what the client already has — the entities it was sent and the
 * blocks around them — and fed through the vanilla flight rules in {@link FlightPath}. The warning is
 * decided once per tick; the lines are drawn every frame from the same paths at the frame's
 * position, so they do not stutter.
 */
public final class Flights {
	/** How far ahead a path is followed: four seconds, longer than any arrow stays in the air. */
	private static final int PATH_TICKS = 80;
	/** A pearl thrown off a high ledge stays up longer than an arrow: six seconds. */
	private static final int PEARL_TICKS = 120;
	/** Speeds below this are a projectile lying still (an arrow in a wall) or not yet sent. */
	private static final double STILL = 1e-3;

	/** Where something the player holds would fly if thrown or shot now. */
	public record Aim(ProjectileKind kind, FlightPath path) {
	}

	/** One projectile in flight and where it goes. */
	public record Flying(Entity entity, ProjectileKind kind, FlightPath path, boolean mine) {
	}

	private final Roster roster;
	private final Supplier<ScoutSettings> settings;
	private final IncomingWatch watch = new IncomingWatch();
	private final LockWatch lockWatch = new LockWatch();
	private IncomingWatch.Warning warning;
	private LockWatch.Lock lock;
	private MissileTone tone;

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
			reset();
			return;
		}

		LocalPlayer player = client.player;
		List<IncomingWatch.Seen> seen = new ArrayList<>();
		for (Flying flying : flying(client.level, player, 1.0f)) {
			seen.add(new IncomingWatch.Seen(flying.entity().getId(), flying.kind(), vec(flying.entity().position()),
					flying.path(), flying.mine()));
		}

		Box self = box(player.getBoundingBox());
		Vec eye = vec(player.getEyePosition());
		Vec look = vec(player.getViewVector(1.0f));
		double viewCos = Math.cos(halfViewAngle(client));
		warning = watch.update(seen, self, eye, look, viewCos).orElse(null);
		lock = options.lock && options.fireballs
				? lockWatch.update(aimers(client.level, player), self, eye, look, viewCos).orElse(null)
				: null;

		MissileAlarm.Tone wanted = wantedTone();
		if (isToneOn() && tone.kind() != wanted) {
			// The tone stops itself too, but only while the sound engine ticks it.
			tone.end();
		}
		if (wanted != MissileAlarm.Tone.NONE && !isToneOn()) {
			// Looped until its warning is over or the other one takes over, see MissileTone.
			tone = new MissileTone(wanted, this::wantedTone);
			client.getSoundManager().play(tone);
		}
		if (warning != null && warning.fresh() && warning.kind() == ProjectileKind.ARROW && options.sound) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 0.9f));
		}
	}

	/** The looping tone the current warning and lock call for, with the sound setting applied. */
	private MissileAlarm.Tone wantedTone() {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		return options.sound && options.alarm ? MissileAlarm.tone(warning, lock) : MissileAlarm.Tone.NONE;
	}

	/**
	 * Everybody who could have the player locked: another player, not on the player's team, holding
	 * a fire charge — with where a fireball of theirs would fly.
	 */
	private List<LockWatch.Aimer> aimers(ClientLevel level, LocalPlayer self) {
		List<LockWatch.Aimer> aimers = new ArrayList<>();
		for (Player other : level.players()) {
			if (other == self || other.isSpectator() || !other.isAlive() || !holdsFireCharge(other)
					|| Teams.isOwnTeam(other.getScoreboardName())) {
				continue;
			}
			aimers.add(new LockWatch.Aimer(other.getId(), other.getScoreboardName(), vec(other.getEyePosition()),
					fireballFrom(level, other, 1.0f)));
		}
		return aimers;
	}

	/** The projectile about to hit the player, or {@code null}. */
	public IncomingWatch.Warning warning() {
		return warning;
	}

	/** Who is aiming a fire charge at the player, and at which spot; {@code null} for nobody. */
	public LockWatch.Lock lock() {
		return lock;
	}

	public void reset() {
		warning = null;
		lock = null;
		watch.reset();
		if (tone != null) {
			tone.end();
			tone = null;
		}
	}

	/** Whether a looping warning tone is sounding, for the client game test. */
	public boolean isToneOn() {
		return tone != null && !tone.isStopped();
	}

	/** Which looping tone is sounding, for the client game test. */
	public MissileAlarm.Tone toneOn() {
		return isToneOn() ? tone.kind() : MissileAlarm.Tone.NONE;
	}

	/** A tone's sound, for the client game test to check it resolves. */
	public static net.minecraft.resources.Identifier toneId(MissileAlarm.Tone kind) {
		return MissileTone.id(kind);
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
					|| (kind == ProjectileKind.FIREBALL && !options.fireballs)
					|| (kind == ProjectileKind.PEARL && !options.pearls)) {
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
					kind == ProjectileKind.PEARL ? PEARL_TICKS : PATH_TICKS, obstacle(level, entity));
			result.add(new Flying(entity, kind, path, isMine(entity, player)));
		}
		return result;
	}

	/**
	 * Where a fireball thrown right now would fly, or {@code null} when the player holds no fire
	 * charge. Straight along the view, from the eyes, until the first block.
	 */
	public FlightPath aim(ClientLevel level, LocalPlayer player, float partialTick) {
		Aim aim = aimAny(level, player, partialTick);
		return aim != null && aim.kind() == ProjectileKind.FIREBALL ? aim.path() : null;
	}

	/**
	 * Where whatever the player has ready would fly: a fire charge in hand, an ender pearl in hand, or
	 * a bow being drawn — at the current draw. {@code null} for none of them, or where the setting for
	 * it is off. A drawn bow comes first: it is what the player is about to let go of.
	 */
	public Aim aimAny(ClientLevel level, LocalPlayer player, float partialTick) {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		Vec3 look = player.getViewVector(partialTick);
		Vec eye = vec(player.getEyePosition(partialTick));

		if (options.bowAim && player.isUsingItem() && player.getUseItem().is(Items.BOW)
				&& Throw.bowShoots(player.getTicksUsingItem())) {
			Vec velocity = Throw.velocity(vec(look), Throw.ARROW_SPEED * Throw.bowPower(player.getTicksUsingItem()),
					vec(player.getKnownMovement()), player.onGround());
			return new Aim(ProjectileKind.ARROW, FlightPath.predict(ProjectileKind.ARROW, Throw.start(eye), velocity, 0,
					PATH_TICKS, obstacle(level, player)));
		}
		if (options.aim && holdsFireCharge(player)) {
			return new Aim(ProjectileKind.FIREBALL, fireballFrom(level, player, partialTick));
		}
		if (options.pearlAim && holds(player, Items.ENDER_PEARL)) {
			Vec velocity = Throw.velocity(vec(look), Throw.PEARL_SPEED, vec(player.getKnownMovement()), player.onGround());
			return new Aim(ProjectileKind.PEARL, FlightPath.predict(ProjectileKind.PEARL, Throw.start(eye), velocity, 0,
					PEARL_TICKS, obstacle(level, player)));
		}
		return null;
	}

	private static boolean holds(LivingEntity player, net.minecraft.world.item.Item item) {
		return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
	}

	/** A fireball thrown by {@code thrower} right now: straight along their view, from their eyes. */
	private static FlightPath fireballFrom(ClientLevel level, LivingEntity thrower, float partialTick) {
		Vec3 look = thrower.getViewVector(partialTick);
		return FlightPath.predict(ProjectileKind.FIREBALL, vec(thrower.getEyePosition(partialTick)), vec(look), 0.1,
				PATH_TICKS, obstacle(level, thrower));
	}

	public static boolean holdsFireCharge(LivingEntity player) {
		return holds(player, Items.FIRE_CHARGE);
	}

	private static ProjectileKind kindOf(Entity entity) {
		if (entity instanceof AbstractArrow) {
			return ProjectileKind.ARROW;
		}
		if (entity instanceof Fireball) {
			return ProjectileKind.FIREBALL;
		}
		if (entity instanceof ThrownEnderpearl) {
			return ProjectileKind.PEARL;
		}
		return null;
	}

	private static boolean isMine(Entity entity, LocalPlayer player) {
		return entity instanceof net.minecraft.world.entity.projectile.Projectile projectile
				&& projectile.getOwner() == player;
	}

	/** The blocks the projectile collides with, as vanilla's own flight code asks for them. */
	public static FlightPath.Obstacle obstacle(ClientLevel level, Entity entity) {
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
	public static double halfViewAngle(Minecraft client) {
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

	public static Box box(AABB aabb) {
		return new Box(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
	}

	/** The player's hitbox, for telling which drawn path is the dangerous one. */
	public static Box hitbox(LocalPlayer player) {
		return box(player.getBoundingBox());
	}
}
