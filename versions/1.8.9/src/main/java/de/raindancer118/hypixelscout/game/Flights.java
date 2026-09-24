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
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Arrows and fireballs in the air: where each will fly, and whether one of them is about to hit the
 * player — or whether somebody is aiming a fire charge at the player, before anything is thrown.
 *
 * <p>Ported from 26.2's {@code game.Flights}. What changes on legacy Forge: entities come from
 * {@code WorldClient.loadedEntityList} rather than {@code level.entitiesForRendering()}; a
 * fireball's push along its own direction is read straight off the public
 * {@code accelerationX/Y/Z} fields (1.8.9 stores a baked vector rather than modern MC's scalar
 * {@code accelerationPower} — its length is an equivalent scalar for a fireball thrown in a
 * straight line, which is the only case this mod ever predicts); there is no ender pearl offhand
 * or bow-drawing partial tick to speak of beyond {@link EntityPlayer#getItemInUseDuration()}; and
 * the view-cone half-angle comes from {@code GameSettings.fovSetting} and the raw
 * {@code Minecraft.displayWidth/displayHeight} instead of a window handle.
 */
public final class Flights {
	/** How far ahead a path is followed: four seconds, longer than any arrow stays in the air. */
	private static final int PATH_TICKS = 80;
	/** A pearl thrown off a high ledge stays up longer than an arrow: six seconds. */
	private static final int PEARL_TICKS = 120;
	/** Speeds below this are a projectile lying still (an arrow in a wall) or not yet sent. */
	private static final double STILL = 1e-3;
	/** 1.8.9 has no negative build height: the floor of the world is the closest thing to a void floor. */
	private static final double VOID_Y = 0.0;

	/** Where something the player holds would fly if thrown or shot now. */
	public static final class Aim {
		private final ProjectileKind kind;
		private final FlightPath path;

		Aim(ProjectileKind kind, FlightPath path) {
			this.kind = kind;
			this.path = path;
		}

		public ProjectileKind kind() {
			return kind;
		}

		public FlightPath path() {
			return path;
		}
	}

	/** One projectile in flight and where it goes. */
	public static final class Flying {
		private final Entity entity;
		private final ProjectileKind kind;
		private final FlightPath path;
		private final boolean mine;

		Flying(Entity entity, ProjectileKind kind, FlightPath path, boolean mine) {
			this.entity = entity;
			this.kind = kind;
			this.path = path;
			this.mine = mine;
		}

		public Entity entity() {
			return entity;
		}

		public ProjectileKind kind() {
			return kind;
		}

		public FlightPath path() {
			return path;
		}

		public boolean mine() {
			return mine;
		}
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
		return client.thePlayer != null && client.theWorld != null
				&& (!settings.get().projectiles.onlyInGame || roster.isInGame());
	}

	public void tick(Minecraft client) {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		if (!options.alarm || !active(client)) {
			reset();
			return;
		}

		EntityPlayerSP player = client.thePlayer;
		List<IncomingWatch.Seen> seen = new ArrayList<IncomingWatch.Seen>();
		for (Flying flying : flying(client.theWorld, player, 1.0f)) {
			seen.add(new IncomingWatch.Seen(flying.entity().getEntityId(), flying.kind(), vec(flying.entity().getPositionVector()),
					flying.path(), flying.mine()));
		}

		Box self = box(player.getEntityBoundingBox());
		Vec eye = vec(player.getPositionEyes(1.0f));
		Vec look = vec(player.getLook(1.0f));
		double viewCos = Math.cos(halfViewAngle(client));
		IncomingWatch.Warning newWarning = watch.update(seen, self, eye, look, viewCos).orElse(null);
		warning = newWarning;
		lock = options.lock && options.fireballs
				? lockWatch.update(aimers(client.theWorld, player), self, eye, look, viewCos).orElse(null)
				: null;

		MissileAlarm.Tone wanted = wantedTone();
		if (isToneOn() && tone.kind() != wanted) {
			// The tone stops itself too, but only while the sound engine ticks it.
			tone.end();
		}
		if (wanted != MissileAlarm.Tone.NONE && !isToneOn()) {
			// Looped until its warning is over or the other one takes over, see MissileTone.
			tone = new MissileTone(wanted, wantedToneSupplier());
			client.getSoundHandler().playSound(tone);
		}
		if (warning != null && warning.fresh() && warning.kind() == ProjectileKind.ARROW && options.sound) {
			client.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("note.pling"), 0.9f));
		}
	}

	/** The looping tone the current warning and lock call for, with the sound setting applied. */
	private MissileAlarm.Tone wantedTone() {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		return options.sound && options.alarm ? MissileAlarm.tone(warning, lock) : MissileAlarm.Tone.NONE;
	}

	private Supplier<MissileAlarm.Tone> wantedToneSupplier() {
		return new Supplier<MissileAlarm.Tone>() {
			@Override
			public MissileAlarm.Tone get() {
				return wantedTone();
			}
		};
	}

	/**
	 * Everybody who could have the player locked: another player, not on the player's team, holding
	 * a fire charge — with where a fireball of theirs would fly.
	 */
	private List<LockWatch.Aimer> aimers(WorldClient level, EntityPlayerSP self) {
		List<LockWatch.Aimer> aimers = new ArrayList<LockWatch.Aimer>();
		for (EntityPlayer other : level.playerEntities) {
			if (other == self || other.isSpectator() || !other.isEntityAlive() || !holdsFireCharge(other)
					|| Teams.isOwnTeam(other.getName())) {
				continue;
			}
			aimers.add(new LockWatch.Aimer(other.getEntityId(), other.getName(), vec(other.getPositionEyes(1.0f)),
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
		return tone != null && Minecraft.getMinecraft().getSoundHandler().isSoundPlaying(tone);
	}

	/** Which looping tone is sounding, for the client game test. */
	public MissileAlarm.Tone toneOn() {
		return isToneOn() ? tone.kind() : MissileAlarm.Tone.NONE;
	}

	/** A tone's sound, for the client game test to check it resolves. */
	public static ResourceLocation toneId(MissileAlarm.Tone kind) {
		return MissileTone.id(kind);
	}

	/**
	 * Every arrow and fireball in flight the settings ask for, with its path from where it is at
	 * {@code partialTick} — 1 for the end of the tick, the frame's fraction for drawing.
	 */
	public List<Flying> flying(WorldClient level, EntityPlayerSP player, float partialTick) {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		List<Flying> result = new ArrayList<Flying>();

		for (Entity entity : level.loadedEntityList) {
			ProjectileKind kind = kindOf(entity);
			if (kind == null || (kind == ProjectileKind.ARROW && !options.arrows)
					|| (kind == ProjectileKind.FIREBALL && !options.fireballs)
					|| (kind == ProjectileKind.PEARL && !options.pearls)) {
				continue;
			}

			Vec3 motion = new Vec3(entity.motionX, entity.motionY, entity.motionZ);
			if (motion.dotProduct(motion) < STILL * STILL) {
				// Not sent any motion yet: what it moved since the last tick says the same.
				motion = new Vec3(entity.posX - entity.lastTickPosX, entity.posY - entity.lastTickPosY,
						entity.posZ - entity.lastTickPosZ);
				if (motion.dotProduct(motion) < STILL * STILL) {
					continue;
				}
			}

			double acceleration = entity instanceof EntityFireball ? fireballAcceleration((EntityFireball) entity) : 0.0;
			FlightPath path = FlightPath.predict(kind, vec(interpolated(entity, partialTick)), vec(motion), acceleration,
					kind == ProjectileKind.PEARL ? PEARL_TICKS : PATH_TICKS, obstacle(level, entity));
			result.add(new Flying(entity, kind, path, isMine(entity, player)));
		}
		return result;
	}

	private static Vec3 interpolated(Entity entity, float partialTick) {
		double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTick;
		double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTick;
		double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTick;
		return new Vec3(x, y, z);
	}

	/** The length of the baked acceleration vector: the equivalent of modern MC's scalar push. */
	private static double fireballAcceleration(EntityFireball fireball) {
		return Math.sqrt(fireball.accelerationX * fireball.accelerationX + fireball.accelerationY * fireball.accelerationY
				+ fireball.accelerationZ * fireball.accelerationZ);
	}

	/**
	 * Where a fireball thrown right now would fly, or {@code null} when the player holds no fire
	 * charge. Straight along the view, from the eyes, until the first block.
	 */
	public FlightPath aim(WorldClient level, EntityPlayerSP player, float partialTick) {
		Aim aim = aimAny(level, player, partialTick);
		return aim != null && aim.kind() == ProjectileKind.FIREBALL ? aim.path() : null;
	}

	/**
	 * Where whatever the player has ready would fly: a fire charge in hand, an ender pearl in hand, or
	 * a bow being drawn — at the current draw. {@code null} for none of them, or where the setting for
	 * it is off. A drawn bow comes first: it is what the player is about to let go of.
	 */
	public Aim aimAny(WorldClient level, EntityPlayerSP player, float partialTick) {
		ScoutSettings.Projectiles options = settings.get().projectiles;
		Vec3 look = player.getLook(partialTick);
		Vec eye = vec(player.getPositionEyes(partialTick));

		if (options.bowAim && player.isUsingItem() && isItem(player.getItemInUse(), Items.bow)
				&& Throw.bowShoots(player.getItemInUseDuration())) {
			Vec velocity = Throw.velocity(vec(look), Throw.ARROW_SPEED * Throw.bowPower(player.getItemInUseDuration()),
					knownMovement(player), player.onGround);
			return new Aim(ProjectileKind.ARROW, FlightPath.predict(ProjectileKind.ARROW, Throw.start(eye), velocity, 0,
					PATH_TICKS, obstacle(level, player)));
		}
		if (options.aim && holdsFireCharge(player)) {
			return new Aim(ProjectileKind.FIREBALL, fireballFrom(level, player, partialTick));
		}
		if (options.pearlAim && holds(player, Items.ender_pearl)) {
			Vec velocity = Throw.velocity(vec(look), Throw.PEARL_SPEED, knownMovement(player), player.onGround);
			return new Aim(ProjectileKind.PEARL, FlightPath.predict(ProjectileKind.PEARL, Throw.start(eye), velocity, 0,
					PEARL_TICKS, obstacle(level, player)));
		}
		return null;
	}

	private static Vec knownMovement(EntityLivingBase entity) {
		return new Vec(entity.motionX, entity.motionY, entity.motionZ);
	}

	private static boolean isItem(ItemStack stack, Item item) {
		return stack != null && stack.getItem() == item;
	}

	private static boolean holds(EntityLivingBase player, Item item) {
		return isItem(player.getHeldItem(), item);
	}

	/** A fireball thrown by {@code thrower} right now: straight along their view, from their eyes. */
	private static FlightPath fireballFrom(WorldClient level, EntityLivingBase thrower, float partialTick) {
		Vec3 look = thrower.getLook(partialTick);
		return FlightPath.predict(ProjectileKind.FIREBALL, vec(thrower.getPositionEyes(partialTick)), vec(look), 0.1,
				PATH_TICKS, obstacle(level, thrower));
	}

	public static boolean holdsFireCharge(EntityLivingBase player) {
		return isItem(player.getHeldItem(), Items.fire_charge);
	}

	private static ProjectileKind kindOf(Entity entity) {
		if (entity instanceof EntityArrow) {
			return ProjectileKind.ARROW;
		}
		if (entity instanceof EntityFireball) {
			return ProjectileKind.FIREBALL;
		}
		if (entity instanceof EntityEnderPearl) {
			return ProjectileKind.PEARL;
		}
		return null;
	}

	private static boolean isMine(Entity entity, EntityPlayerSP player) {
		if (entity instanceof EntityFireball) {
			return ((EntityFireball) entity).shootingEntity == player;
		}
		if (entity instanceof EntityArrow) {
			return ((EntityArrow) entity).shootingEntity == player;
		}
		if (entity instanceof EntityEnderPearl) {
			return ((EntityEnderPearl) entity).getThrower() == player;
		}
		return false;
	}

	/** The blocks the projectile collides with, as vanilla's own flight code asks for them. */
	public static FlightPath.Obstacle obstacle(final WorldClient level, final Entity entity) {
		return new FlightPath.Obstacle() {
			@Override
			public Vec clip(Vec from, Vec to) {
				MovingObjectPosition hit = level.rayTraceBlocks(vec3(from), vec3(to));
				return hit == null ? null : vec(hit.hitVec);
			}
		};
	}

	/**
	 * Half the horizontal field of view, in radians: the options hold the vertical one, the window's
	 * shape gives the rest.
	 */
	public static double halfViewAngle(Minecraft client) {
		double vertical = Math.toRadians(client.gameSettings.fovSetting);
		double aspect = (double) client.displayWidth / Math.max(1, client.displayHeight);
		return Math.atan(Math.tan(vertical / 2) * aspect);
	}

	public static Vec vec(Vec3 value) {
		return new Vec(value.xCoord, value.yCoord, value.zCoord);
	}

	public static Vec3 vec3(Vec value) {
		return new Vec3(value.x(), value.y(), value.z());
	}

	public static Box box(AxisAlignedBB aabb) {
		return new Box(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
	}

	/** The player's hitbox, for telling which drawn path is the dangerous one. */
	public static Box hitbox(EntityPlayerSP player) {
		return box(player.getEntityBoundingBox());
	}

	static double voidY() {
		return VOID_Y;
	}
}
