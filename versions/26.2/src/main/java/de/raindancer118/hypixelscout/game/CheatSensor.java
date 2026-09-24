package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.cheat.CheatWatch;
import de.raindancer118.hypixelscout.cheat.Frame;
import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.cheat.Violation;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.flight.Vec;
import de.raindancer118.hypixelscout.mixin.LivingEntityAccessor;
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
import net.minecraft.world.phys.shapes.VoxelShape;

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

	/** A swing and a push that arrived together on the network thread: entity ids, attacker first. */
	private final java.util.concurrent.ConcurrentLinkedQueue<int[]> arrivedAttacks = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private final Object network = new Object();
	private int networkSwinger = -1;
	private long networkSwingAt;
	private long lastGameTime = Long.MIN_VALUE;
	/** Every swing's real arrival time, entity id to nanoTime — the only clock fine enough for
	 * ClickStats' robot/stDev/kurtosis signals (a client tick is too coarse). Read once, on the
	 * render thread, by {@link #onSwing}; written on the network thread by {@link #arrived}. */
	private final Map<Integer, Long> swingNanoTime = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * A server sends one melee hit as the attacker's swing followed at once by the victim's push,
	 * hurt or crit; on the network thread they arrive within a millisecond or two of each other.
	 * Packets further apart than this belong to different things.
	 */
	private static final long TOGETHER_NANOS = 2_000_000;

	/** Where the sighting log goes when it is on; writes on its own thread. */
	private final de.raindancer118.hypixelscout.cheat.CheatLog log = new de.raindancer118.hypixelscout.cheat.CheatLog(
			net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs").resolve("hypixelscout"),
			de.raindancer118.hypixelscout.core.Clock.SYSTEM,
			java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
				Thread thread = new Thread(task, "Hypixel Scout cheat log");
				thread.setDaemon(true);
				return thread;
			}),
			java.time.ZoneId.systemDefault(), de.raindancer118.hypixelscout.cheat.CheatLog.DEFAULT_MAX_BYTES);
	/** Whether this round's start is in the log yet: the log may be switched on halfway through. */
	private boolean roundLogged;

	public CheatSensor(Roster roster, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.settings = settings;
		watch.setEnabled(check -> settings.get().cheats.isOn(check));
		instance = this;
	}

	/**
	 * A swing ({@code swing}) or a push, hurt or crit on an entity, as it comes off the network —
	 * before the render thread gets to it and while its arrival time still means something. Pairs a
	 * swing with whatever lands on somebody else right after it: that is who hit whom.
	 */
	public static void arrived(boolean swing, int entityId) {
		CheatSensor sensor = instance;
		if (sensor == null) {
			return;
		}
		long now = System.nanoTime();
		if (swing) {
			sensor.swingNanoTime.put(entityId, now);
		}
		synchronized (sensor.network) {
			if (swing) {
				sensor.networkSwinger = entityId;
				sensor.networkSwingAt = now;
			} else if (sensor.networkSwinger >= 0 && entityId != sensor.networkSwinger
					&& now - sensor.networkSwingAt < TOGETHER_NANOS) {
				sensor.arrivedAttacks.add(new int[] {sensor.networkSwinger, entityId});
			}
		}
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

	/** The round is over, however it ended: its summary into the log. */
	public void endRound() {
		if (roundLogged) {
			log.roundEnded();
			roundLogged = false;
		}
	}

	public de.raindancer118.hypixelscout.cheat.CheatLog log() {
		return log;
	}

	/** The log, with this round's start in it, when it is switched on; else {@code null}. */
	private de.raindancer118.hypixelscout.cheat.CheatLog logging() {
		if (!settings.get().cheats.log) {
			return null;
		}
		if (!roundLogged) {
			log.roundStarted(roster.mode(), roster.map());
			roundLogged = true;
		}
		return log;
	}

	/**
	 * The player's verdict on a flag: wrong ({@code cheating} false) clears it and logs it as a false
	 * one, right logs it as confirmed. {@code check} {@code null} for all of the player's flags.
	 *
	 * @return the flags the verdict was about; empty when there were none
	 */
	public List<Suspicion.Flag> verdict(String player, de.raindancer118.hypixelscout.cheat.Check check, boolean cheating) {
		List<Suspicion.Flag> flags = suspicion.flags(player).stream()
				.filter(flag -> check == null || flag.check() == check).toList();
		var logging = logging();
		if (logging != null) {
			logging.verdict(player, check, cheating, flags, suspicion.confidence(player));
		}
		if (!cheating) {
			suspicion.forget(player, check);
		}
		return flags;
	}

	/** Everybody seen doing anything suspicious this round, the surest first; nobody while detection is off. */
	public List<Suspicion.Suspect> suspects() {
		return settings.get().cheats.enabled ? suspicion.suspects() : List.of();
	}

	/** The tick being played, for how long ago a sighting was. */
	public long tick() {
		return tick;
	}

	/** How sure the mod is that this player cheats, 0 to 1; 0 when detection is off. */
	public double confidence(String name) {
		return settings.get().cheats.enabled ? suspicion.confidence(name) : 0;
	}

	/** A new game: nobody has done anything yet. */
	public void newRound() {
		endRound();
		watch.clear();
		suspicion.clear();
		hurtAt.clear();
		arrivedAttacks.clear();
		swingNanoTime.clear();
		lastGameTime = Long.MIN_VALUE;
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
				Long nanoTime = sensor.swingNanoTime.remove(entityId);
				sensor.watch.swing(name, sensor.tick + 1, nanoTime != null ? nanoTime : System.nanoTime());
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
		Minecraft client = Minecraft.getInstance();
		if (client.level != null && client.player != null && client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty()) {
			de.raindancer118.hypixelscout.HypixelScout.get().hazards().placed(client.level, client.player, pos, next);
		}
		CheatSensor sensor = instance;
		if (sensor == null || !sensor.active()) {
			return;
		}
		ClientLevel level = client.level;
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
			arrivedAttacks.clear();
			swingNanoTime.clear();
			return;
		}
		ClientLevel level = client.level;
		watch.setSelf(client.player.getScoreboardName());
		for (var check : settings.get().cheats.off) {
			suspicion.forget(check);
		}
		takeArrivedAttacks(level);
		noticeServerLag(level);
		tick++;

		for (Player player : level.players()) {
			String name = watched(player);
			if (name != null && player.isAlive()) {
				watch.frame(name, frame(level, player));
			}
		}

		double sensitivity = settings.get().cheats.sensitivity / 100.0;
		watch.setTuning(settings.get().cheats.tuning());
		for (Violation violation : watch.endTick(tick, new LevelTerrain(level))) {
			var flag = suspicion.record(violation, sensitivity * settings.get().cheats.sensitivityOf(violation.check()) / 100.0);
			var logging = logging();
			if (logging != null) {
				logging.record(violation, suspicion.confidence(violation.player()), settings.get().cheats.sensitivity);
				flag.ifPresent(raised -> logging.flagged(raised, suspicion.confidence(raised.player())));
			}
			flag.ifPresent(this::announce);
		}
		hurtAt.values().removeIf(at -> at < tick - 20);
	}

	/** The swing-and-push pairs from the network, as attacks between players of different teams. */
	private void takeArrivedAttacks(ClientLevel level) {
		int[] pair;
		while ((pair = arrivedAttacks.poll()) != null) {
			String attacker = watched(level.getEntity(pair[0]));
			String victim = watched(level.getEntity(pair[1]));
			if (attacker == null || victim == null) {
				continue;
			}
			Teams.Team team = Teams.of(attacker);
			// Teammates cannot hurt each other in Bedwars: a swing beside a push on one is chance.
			if (team != Teams.NONE && team.equals(Teams.of(victim))) {
				continue;
			}
			watch.attack(attacker, victim, tick + 1);
		}
	}

	/**
	 * The world's clock runs on with the client and is corrected by the server: a correction by
	 * more than a tick either way means the server fell behind or caught up in a burst.
	 */
	private void noticeServerLag(ClientLevel level) {
		long gameTime = level.getGameTime();
		if (lastGameTime != Long.MIN_VALUE) {
			long step = gameTime - lastGameTime;
			if (step <= -2 || step >= 3) {
				watch.serverLag(tick + 1);
			}
		}
		lastGameTime = gameTime;
	}

	private Frame frame(ClientLevel level, Player player) {
		Vec3 feet = player.position();
		double pitch = player.getXRot();
		double yaw = player.getYHeadRot();
		InterpolationHandler interpolation = player.getInterpolation();
		if (interpolation != null && interpolation.hasActiveInterpolation()) {
			// Where the server last put them, not the smoothed position drawn on its way there.
			feet = interpolation.position();
			pitch = interpolation.xRot();
		}
		if (player instanceof LivingEntityAccessor accessor && accessor.hypixelscout$lerpHeadSteps() > 0) {
			// getYHeadRot() is still lerping towards the last rotation packet for every other player;
			// the target it is lerping to, not the smoothed value on its way there, is where they
			// actually looked — the same reasoning the position and pitch interpolation above already
			// use.
			yaw = accessor.hypixelscout$lerpYHeadRot();
		}
		AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
		AABB below = new AABB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ);
		boolean assisted = player.onClimbable() || player.isInWater() || player.isInLava() || player.isFallFlying()
				|| inWeb(level, box);

		return new Frame(tick, Flights.vec(feet), Flights.vec(feet.add(0, player.getEyeHeight(), 0)), Flights.box(box),
				yaw, pitch, player.onGround(), !level.noCollision(below), player.isSprinting(),
				player.isUsingItem(), assisted, player.isPassenger(), player.isShiftKeyDown(), held(player));
	}

	private static Frame.Held held(Player player) {
		net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
		if (stack.isEmpty()) {
			return Frame.Held.NOTHING;
		}
		if (stack.is(net.minecraft.tags.ItemTags.SWORDS)) {
			return Frame.Held.SWORD;
		}
		return stack.getItem() instanceof net.minecraft.world.item.BlockItem ? Frame.Held.BLOCK : Frame.Held.OTHER;
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

	/**
	 * {@link CheatWatch.Terrain} over the real world: {@link #solid} keeps treating any collision
	 * shape as a full cube, for the callers that only need that coarse answer (knockback's wall and
	 * ceiling, the bed check); {@link #solidAt} looks at the shape's actual boxes, so a slab, a stair,
	 * a bed, a carpet, a fence or a snow layer does not block a sight line through the rest of its
	 * cell.
	 */
	private record LevelTerrain(ClientLevel level) implements CheatWatch.Terrain {
		@Override
		public boolean solid(int x, int y, int z) {
			return CheatSensor.solid(level, new BlockPos(x, y, z));
		}

		@Override
		public boolean solidAt(double x, double y, double z) {
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!level.isLoaded(pos)) {
				return false;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				return false;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				return false;
			}
			for (AABB box : shape.move(pos.getX(), pos.getY(), pos.getZ()).toAabbs()) {
				if (box.contains(x, y, z)) {
					return true;
				}
			}
			return false;
		}
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
				.append(" ").append(reportLinks())
				.append(" ").append(link("message.hypixelscout.cheat.wrong",
						"/scout cheats wrong " + flag.player() + " " + flag.check().name().toLowerCase(java.util.Locale.ROOT))));
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
