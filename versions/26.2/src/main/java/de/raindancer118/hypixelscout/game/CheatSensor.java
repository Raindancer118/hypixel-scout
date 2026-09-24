package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.cheat.CheatWatch;
import de.raindancer118.hypixelscout.cheat.Frame;
import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.cheat.Violation;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.Suspects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The game half of {@link CheatWatch}: turns what the client is told into its events and frames,
 * and its sightings into flags and chat lines.
 *
 * <p>The packet side comes from {@code ClientPacketListenerMixin}, on the render thread before the
 * packet changes anything — so a block update still sees what was there before. Every player in the
 * game is watched, whoever they fight: the player running the client is only one more witness.
 * Nothing here sends anything; a flag is a line in the player's own chat and a mark on the name.
 */
public final class CheatSensor {
	private static volatile CheatSensor instance;

	private final Roster roster;
	private final Supplier<ScoutSettings> settings;
	private final CheatWatch watch = new CheatWatch();
	private final Suspicion suspicion = new Suspicion();
	/** The last finished tick; everything arriving before the next one's end belongs to {@code tick + 1}. */
	private long tick;
	private final Map<Integer, Long> hurtAt = new HashMap<>();

	public CheatSensor(Roster roster, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.settings = settings;
		instance = this;
	}

	public static CheatSensor get() {
		return instance;
	}

	public Suspicion suspicion() {
		return suspicion;
	}

	/** This player's flags, the most often seen first; none when detection is off. */
	public List<Suspicion.Flag> flags(String name) {
		return settings.get().cheats.enabled ? suspicion.flags(name) : List.of();
	}

	/** How sure the mod is that this player cheats, 0 to 1; 0 when detection is off. */
	public double confidence(String name) {
		return settings.get().cheats.enabled ? suspicion.confidence(name) : 0;
	}

	/** A new game: nobody has done anything yet. */
	public void newRound() {
		watch.clear();
		suspicion.clear();
		hurtAt.clear();
	}

	private boolean active() {
		Minecraft client = Minecraft.getInstance();
		return settings.get().cheats.enabled && roster.isInGame() && client.level != null && client.player != null;
	}

	/** A player in this game — not an NPC, not somebody in another world. */
	private String watched(Entity entity) {
		if (!(entity instanceof Player player) || player.isSpectator()) {
			return null;
		}
		String name = player.getScoreboardName();
		return player == Minecraft.getInstance().player || roster.contains(name) ? name : null;
	}

	// --- packets ----------------------------------------------------------------------------------

	public static void onSwing(int entityId) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			String name = sensor.watched(Minecraft.getInstance().level.getEntity(entityId));
			if (name != null) {
				sensor.watch.swing(name, sensor.tick + 1);
			}
		}
	}

	public static void onDamage(ClientboundDamageEventPacket packet) {
		CheatSensor sensor = instance;
		if (sensor == null || !sensor.active()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		Entity victim = level.getEntity(packet.entityId());
		Entity cause = packet.sourceCauseId() >= 0 ? level.getEntity(packet.sourceCauseId()) : null;
		Entity direct = packet.sourceDirectId() >= 0 ? level.getEntity(packet.sourceDirectId()) : null;

		CheatWatch.Hit hit;
		String causeName = null;
		if (packet.sourceType().is(DamageTypes.PLAYER_ATTACK) && (direct == null || direct == cause)) {
			hit = cause instanceof Player ? CheatWatch.Hit.MELEE : CheatWatch.Hit.UNKNOWN;
			causeName = cause == null ? null : sensor.watched(cause);
		} else if (packet.sourceType().is(DamageTypes.GENERIC)) {
			// What a server that does not say sends: judged by who was swinging nearby.
			hit = CheatWatch.Hit.UNKNOWN;
		} else {
			hit = CheatWatch.Hit.OTHER;
		}
		sensor.hurt(victim, hit, causeName);
	}

	public static void onHurtAnimation(int entityId) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			sensor.hurt(Minecraft.getInstance().level.getEntity(entityId), CheatWatch.Hit.UNKNOWN, null);
		}
	}

	private void hurt(Entity victim, CheatWatch.Hit hit, String causeName) {
		String name = watched(victim);
		if (name == null) {
			return;
		}
		// Servers may send both a damage event and a hurt animation for one hit: the first one counts.
		Long last = hurtAt.put(victim.getId(), tick + 1);
		if (last != null && last == tick + 1) {
			return;
		}
		if (hit == CheatWatch.Hit.UNKNOWN && otherCauseNear(victim)) {
			hit = CheatWatch.Hit.OTHER;
		}
		watch.hurt(name, tick + 1, hit, causeName);
	}

	/** An arrow, a snowball, a golem or a silverfish beside the victim explains a hurt as well as a fist. */
	private static boolean otherCauseNear(Entity victim) {
		AABB around = victim.getBoundingBox().inflate(3.5);
		return !victim.level().getEntities(victim, around, entity -> entity instanceof Projectile
				|| entity instanceof LivingEntity living && !(living instanceof Player) && !(living instanceof ArmorStand)).isEmpty();
	}

	public static void onMotion(int entityId, Vec3 movement) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			String name = sensor.watched(Minecraft.getInstance().level.getEntity(entityId));
			if (name != null) {
				sensor.watch.motion(name, sensor.tick + 1, Flights.vec(movement));
			}
		}
	}

	public static void onExplosion(Vec3 centre) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			sensor.watch.explosion(Flights.vec(centre), sensor.tick + 1);
		}
	}

	/** A block about to change from what is there now to {@code next}. */
	public static void onBlock(BlockPos pos, BlockState next) {
		CheatSensor sensor = instance;
		if (sensor == null || !sensor.active()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		BlockState before = level.getBlockState(pos);
		BedDefense.Cell cell = new BedDefense.Cell(pos.getX(), pos.getY(), pos.getZ());

		if (before.getBlock() instanceof BedBlock && !(next.getBlock() instanceof BedBlock)) {
			sensor.watch.bedBroken(cell, sensor.tick + 1);
		} else if (before.getCollisionShape(level, pos).isEmpty() && !next.getCollisionShape(level, pos).isEmpty()
				&& !(next.getBlock() instanceof BedBlock)) {
			boolean thrown = !level.getEntitiesOfClass(ThrownEgg.class, new AABB(pos).inflate(3.0)).isEmpty();
			sensor.watch.placed(cell, sensor.tick + 1, thrown);
		}
	}

	// --- ticks ------------------------------------------------------------------------------------

	public void tick(Minecraft client) {
		if (!active()) {
			return;
		}
		tick++;
		ClientLevel level = client.level;
		watch.setSelf(client.player.getScoreboardName());

		for (Player player : level.players()) {
			String name = watched(player);
			if (name != null && player.isAlive()) {
				watch.frame(name, frame(level, player));
			}
		}

		double sensitivity = settings.get().cheats.sensitivity / 100.0;
		for (Violation violation : watch.endTick(tick, (x, y, z) -> solid(level, new BlockPos(x, y, z)))) {
			suspicion.record(violation, sensitivity).ifPresent(this::announce);
		}
		hurtAt.values().removeIf(at -> at < tick - 20);
	}

	private Frame frame(ClientLevel level, Player player) {
		Vec3 feet = player.position();
		double pitch = player.getXRot();
		InterpolationHandler interpolation = player.getInterpolation();
		if (interpolation != null && interpolation.hasActiveInterpolation()) {
			// Where the server last put them, not the smoothed position drawn on its way there.
			feet = interpolation.position();
			pitch = interpolation.xRot();
		}
		AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
		AABB below = new AABB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ);
		boolean assisted = player.onClimbable() || player.isInWater() || player.isInLava() || player.isFallFlying()
				|| inWeb(level, box);

		return new Frame(tick, Flights.vec(feet), Flights.vec(feet.add(0, player.getEyeHeight(), 0)), Flights.box(box),
				player.getYHeadRot(), pitch, player.onGround(), !level.noCollision(below), player.isSprinting(),
				player.isUsingItem(), assisted, player.isPassenger());
	}

	private static boolean inWeb(ClientLevel level, AABB box) {
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
				BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (level.getBlockState(pos).getBlock() instanceof WebBlock) {
				return true;
			}
		}
		return false;
	}

	private static boolean solid(ClientLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
	}

	private void announce(Suspicion.Flag flag) {
		if (!settings.get().cheats.chatAlerts) {
			return;
		}
		Teams.Team team = Teams.of(flag.player());
		Chat.say(Component.translatable("message.hypixelscout.cheat.flagged",
						Component.literal(flag.player()).withColor(team == Teams.NONE ? 0xFFFFFF : team.rgb()),
						Component.literal(flag.check().label()).withColor(0xFF5555),
						flag.detail(),
						Component.literal(Suspects.percent(suspicion.confidence(flag.player()))))
				.append(" ").append(reportLinks()));
	}

	/** {@code [→ Party] [→ Team]}: a click sends every flag of the round, the way the command does. */
	public static Component reportLinks() {
		return Component.empty()
				.append(link("message.hypixelscout.cheat.to_party", "/scout cheats party"))
				.append(" ")
				.append(link("message.hypixelscout.cheat.to_team", "/scout cheats team"));
	}

	private static Component link(String key, String command) {
		return Component.translatable(key).withStyle(style -> style.withColor(0x55FFFF)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
				.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
						Component.translatable(key + ".hover"))));
	}
}
