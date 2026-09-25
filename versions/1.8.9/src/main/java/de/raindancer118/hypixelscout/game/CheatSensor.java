package de.raindancer118.hypixelscout.game;

import de.raindancer118.cheatwatch.CheatWatch;
import de.raindancer118.cheatwatch.Check;
import de.raindancer118.cheatwatch.Clock;
import de.raindancer118.cheatwatch.Detector;
import de.raindancer118.cheatwatch.Enclosure;
import de.raindancer118.cheatwatch.Frame;
import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.cheatwatch.Violation;
import de.raindancer118.cheatwatch.math.Box;
import de.raindancer118.cheatwatch.math.Cell;
import de.raindancer118.cheatwatch.math.Vec;
import de.raindancer118.cheatwatch.record.Recorder;
import de.raindancer118.cheatwatch.record.RecorderOptions;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.mixin.EntityOtherPlayerMPAccessor;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.Suspects;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The game half of {@link CheatWatch}: turns what the client is told into its events and frames,
 * and its sightings into flags and chat lines.
 *
 * <p>Ported from 26.2's {@code game.CheatSensor}. The packet side comes from
 * {@code mixin.NetHandlerPlayClientMixin}, on the render thread before the packet changes anything
 * — so a block update still sees what was there before. Every player in the game is watched,
 * whoever they fight: the player running the client is only one more witness. Nothing here sends
 * anything; a flag is a line in the player's own chat and a mark on the name.
 *
 * <p>1.8.9 differences from 26.2: no damage-event packet carries a cause, so every hurt is fed as
 * {@link CheatWatch.Hit#UNKNOWN} (the same as a server that "does not say" in 26.2 — {@code
 * CheatWatch} already handles that case); no offhand, so only the main hand is checked for a sword
 * or a block; and a remote player's target position/rotation is read off
 * {@link EntityOtherPlayerMPAccessor} rather than an interpolation handler, while head yaw
 * ({@code rotationYawHead}) has no lerp at all here and is read straight (see that accessor's own
 * documentation).
 */
public final class CheatSensor {
	private static volatile CheatSensor instance;

	private final Roster roster;
	private final Supplier<ScoutSettings> settings;
	private final CheatWatch engine = new CheatWatch();
	/** What every event goes to: the engine itself, or the recorder wrapped around it this round. */
	private Detector watch = engine;
	/** This round's recording, while {@code cheats.record} is on; {@code null} otherwise. */
	private Recorder recorder;
	/** Where sightings, flags and verdicts also go, anonymised; {@code null} in a test without it. */
	private Telemetry telemetry;
	/** Whether a round is on for telemetry, from which tick, and everybody watched in it. */
	private boolean roundOn;
	private long roundStartTick;
	private final java.util.Set<String> watchedThisRound = new java.util.HashSet<String>();
	private final java.util.function.Predicate<Check> enabled = new java.util.function.Predicate<Check>() {
		@Override
		public boolean test(Check check) {
			return settings.get().cheats.isOn(check);
		}
	};
	private final Suspicion suspicion = new Suspicion();
	/** The last finished tick; everything arriving before the next one's end belongs to {@code tick + 1}. */
	private long tick;
	private final Map<Integer, Long> hurtAt = new HashMap<Integer, Long>();

	/** A swing and a push that arrived together on the network thread: entity ids, attacker first. */
	private final ConcurrentLinkedQueue<int[]> arrivedAttacks = new ConcurrentLinkedQueue<int[]>();
	private final Object network = new Object();
	/** Every swing's real arrival time, entity id to nanoTime — the only clock fine enough for the
	 * AutoClicker check's robot/stDev/kurtosis signals (a client tick is too coarse). Written on the
	 * network thread by {@link #arrived}, read once on the render thread by {@link #onSwing}. */
	private final Map<Integer, Long> swingNanoTime = new java.util.concurrent.ConcurrentHashMap<Integer, Long>();
	private int networkSwinger = -1;
	private long networkSwingAt;
	private long lastGameTime = Long.MIN_VALUE;

	/**
	 * A server sends one melee hit as the attacker's swing followed at once by the victim's push,
	 * hurt or crit; on the network thread they arrive within a millisecond or two of each other.
	 * Packets further apart than this belong to different things.
	 */
	private static final long TOGETHER_NANOS = 2_000_000;

	/** Where the sighting log goes when it is on; writes on its own thread. */
	private final de.raindancer118.cheatwatch.CheatLog log = new de.raindancer118.cheatwatch.CheatLog(
			new File(Minecraft.getMinecraft().mcDataDir, "logs" + File.separator + "hypixelscout").toPath(),
			Clock.SYSTEM,
			Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
				@Override
				public Thread newThread(Runnable task) {
					Thread thread = new Thread(task, "Hypixel Scout cheat log");
					thread.setDaemon(true);
					return thread;
				}
			}),
			java.time.ZoneId.systemDefault(), de.raindancer118.cheatwatch.CheatLog.DEFAULT_MAX_BYTES);
	/** Whether this round's start is in the log yet: the log may be switched on halfway through. */
	private boolean roundLogged;

	public CheatSensor(Roster roster, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.settings = settings;
		engine.setEnabled(enabled);
		engine.setEnclosure(DEFENCE);
		instance = this;
	}

	/**
	 * A swing ({@code swing}) or a push/hurt on an entity, as it comes off the network — before the
	 * render thread gets to it and while its arrival time still means something. Pairs a swing with
	 * whatever lands on somebody else right after it: that is who hit whom.
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
		return settings.get().cheats.enabled ? suspicion.flags(name) : java.util.Collections.<Suspicion.Flag>emptyList();
	}

	/**
	 * NUKER's question — was the bed still wrapped in its defence — answered by the same {@link
	 * BedDefense} the bed ledger shows, from nothing but the terrain CheatWatch hands over, so a
	 * recording of that terrain still replays the answer.
	 */
	static final Enclosure DEFENCE = new Enclosure() {
		@Override
		public boolean enclosed(List<Cell> bed, final CheatWatch.Terrain terrain) {
			List<BedDefense.Cell> cells = new ArrayList<BedDefense.Cell>(bed.size());
			for (Cell cell : bed) {
				cells.add(new BedDefense.Cell(cell.x(), cell.y(), cell.z()));
			}
			final BedDefense.Block solid = new BedDefense.Block("solid", 1);
			return !BedDefense.analyse(cells, new BedDefense.World() {
				@Override
				public BedDefense.Block at(BedDefense.Cell at) {
					return terrain.solid(at.x(), at.y(), at.z()) ? solid : null;
				}
			}).open();
		}
	};

	public void telemetry(Telemetry sink) {
		telemetry = sink;
	}

	/** The round is over, however it ended: its summary into the log, its recording closed. */
	public void endRound() {
		if (roundOn && telemetry != null) {
			telemetry.roundEnded(tick - roundStartTick, watchedThisRound.size());
		}
		roundOn = false;
		watchedThisRound.clear();
		if (roundLogged) {
			log.roundEnded();
			roundLogged = false;
		}
		stopRecording();
	}

	/**
	 * Starts this round's recording when {@code cheats.record} is on. Only ever at the start of a
	 * round, right before the engine forgets everybody: a recording begun halfway would replay
	 * without the history the live engine had.
	 */
	private void startRecording() {
		if (!settings.get().cheats.record) {
			return;
		}
		try {
			java.nio.file.Path dir = log.dir();
			java.nio.file.Files.createDirectories(dir);
			String name = "cheatwatch-" + java.time.LocalDateTime.now().format(STAMP) + ".cwrec";
			recorder = new Recorder(engine, dir.resolve(name), RecorderOptions.defaults());
			recorder.setEnabled(enabled);
			recorder.setEnclosure(DEFENCE);
			recorder.setTuning(settings.get().cheats.tuning());
			watch = recorder;
		} catch (java.io.IOException e) {
			// Recording is a help for tuning, never a reason for the game to stumble.
			recorder = null;
			watch = engine;
		}
	}

	private void stopRecording() {
		if (recorder != null) {
			recorder.close();
			recorder = null;
		}
		watch = engine;
	}

	/** This round's recording, or {@code null} when none is running. */
	public Recorder recorder() {
		return recorder;
	}

	private static final java.time.format.DateTimeFormatter STAMP =
			java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", java.util.Locale.ROOT);

	public de.raindancer118.cheatwatch.CheatLog log() {
		return log;
	}

	/** The log, with this round's start in it, when it is switched on; else {@code null}. */
	private de.raindancer118.cheatwatch.CheatLog logging() {
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
	public List<Suspicion.Flag> verdict(String player, Check check, boolean cheating) {
		List<Suspicion.Flag> flags = new ArrayList<Suspicion.Flag>();
		for (Suspicion.Flag flag : suspicion.flags(player)) {
			if (check == null || flag.check() == check) {
				flags.add(flag);
			}
		}
		de.raindancer118.cheatwatch.CheatLog logging = logging();
		if (logging != null) {
			logging.verdict(player, check, cheating, flags, suspicion.confidence(player));
		}
		if (telemetry != null && !flags.isEmpty()) {
			telemetry.verdict(player, check, cheating);
		}
		if (!cheating) {
			suspicion.forget(player, check);
		}
		return flags;
	}

	/** Everybody seen doing anything suspicious this round, the surest first; nobody while detection is off. */
	public List<Suspicion.Suspect> suspects() {
		return settings.get().cheats.enabled ? suspicion.suspects() : java.util.Collections.<Suspicion.Suspect>emptyList();
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
		startRecording();
		watch.clear();
		roundOn = true;
		roundStartTick = tick;
		if (telemetry != null) {
			telemetry.roundStarted(roster.mode());
		}
		suspicion.clear();
		hurtAt.clear();
		arrivedAttacks.clear();
		swingNanoTime.clear();
		lastGameTime = Long.MIN_VALUE;
	}

	private boolean active() {
		Minecraft client = Minecraft.getMinecraft();
		return settings.get().cheats.enabled && roster.isInGame() && client.theWorld != null && client.thePlayer != null;
	}

	/** A player in this game — not an NPC, not somebody in another world. */
	private String watched(Entity entity) {
		if (!(entity instanceof EntityPlayer) || ((EntityPlayer) entity).isSpectator()) {
			return null;
		}
		EntityPlayer player = (EntityPlayer) entity;
		String name = player.getName();
		return player == Minecraft.getMinecraft().thePlayer || roster.contains(name) ? name : null;
	}

	// --- packets ----------------------------------------------------------------------------------

	public static void onSwing(int entityId) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			String name = sensor.watched(Minecraft.getMinecraft().theWorld.getEntityByID(entityId));
			if (name != null) {
				Long nanoTime = sensor.swingNanoTime.remove(entityId);
				sensor.watch.swing(name, sensor.tick + 1, nanoTime != null ? nanoTime : System.nanoTime());
			}
		}
	}

	/** {@code S19PacketEntityStatus} opcode 2: 1.8.9's only "you got hit" signal, with no cause at all. */
	public static void onHurt(int entityId) {
		CheatSensor sensor = instance;
		if (sensor == null || !sensor.active() || entityId < 0) {
			return;
		}
		Entity victim = Minecraft.getMinecraft().theWorld.getEntityByID(entityId);
		if (victim == null) {
			return;
		}
		CheatWatch.Hit hit = otherCauseNear(victim) ? CheatWatch.Hit.OTHER : CheatWatch.Hit.UNKNOWN;
		sensor.hurt(victim, hit, null);
	}

	private void hurt(Entity victim, CheatWatch.Hit hit, String causeName) {
		String name = watched(victim);
		if (name == null) {
			return;
		}
		// A double-send (rare) should not count as two hits: the first one counts.
		Long last = hurtAt.put(victim.getEntityId(), tick + 1);
		if (last != null && last == tick + 1) {
			return;
		}
		watch.hurt(name, tick + 1, hit, causeName);
	}

	/** An arrow, a snowball, a golem or a silverfish beside the victim explains a hurt as well as a fist. */
	private static boolean otherCauseNear(Entity victim) {
		AxisAlignedBB around = victim.getEntityBoundingBox().expand(3.5, 3.5, 3.5);
		List<Entity> nearby = victim.worldObj.getEntitiesWithinAABBExcludingEntity(victim, around);
		for (Entity entity : nearby) {
			if (entity instanceof net.minecraft.entity.projectile.EntityArrow || entity instanceof EntityFireball
					|| (entity instanceof EntityLivingBase && !(entity instanceof EntityPlayer)
							&& !(entity instanceof EntityArmorStand))) {
				return true;
			}
		}
		return false;
	}

	public static void onMotion(int entityId, Vec3 movement) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			String name = sensor.watched(Minecraft.getMinecraft().theWorld.getEntityByID(entityId));
			if (name != null) {
				sensor.watch.motion(name, sensor.tick + 1, new Vec(movement.xCoord, movement.yCoord, movement.zCoord));
			}
		}
	}

	public static void onExplosion(Vec3 centre) {
		CheatSensor sensor = instance;
		if (sensor != null && sensor.active()) {
			sensor.watch.explosion(new Vec(centre.xCoord, centre.yCoord, centre.zCoord), sensor.tick + 1);
		}
	}

	/** A block about to change from what is there now to {@code next}. */
	public static void onBlock(BlockPos pos, IBlockState next) {
		Minecraft client = Minecraft.getMinecraft();
		if (client.theWorld != null && client.thePlayer != null
				&& client.theWorld.getBlockState(pos).getBlock().getCollisionBoundingBox(client.theWorld, pos,
						client.theWorld.getBlockState(pos)) == null) {
			de.raindancer118.hypixelscout.HypixelScout.get().hazards().placed(client.theWorld, client.thePlayer, pos, next);
		}
		CheatSensor sensor = instance;
		if (sensor == null || !sensor.active()) {
			return;
		}
		WorldClient level = client.theWorld;
		IBlockState before = level.getBlockState(pos);
		Cell cell = new Cell(pos.getX(), pos.getY(), pos.getZ());

		boolean beforeSolid = before.getBlock().getCollisionBoundingBox(level, pos, before) != null;
		boolean nextSolid = next.getBlock().getCollisionBoundingBox(level, pos, next) != null;
		if (before.getBlock() instanceof BlockBed && !(next.getBlock() instanceof BlockBed)) {
			sensor.watch.bedBroken(cell, sensor.tick + 1);
		} else if (!beforeSolid && nextSolid && !(next.getBlock() instanceof BlockBed)) {
			boolean thrown = !level.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityEgg.class,
					new AxisAlignedBB(pos.getX() - 3, pos.getY() - 3, pos.getZ() - 3, pos.getX() + 4, pos.getY() + 4,
							pos.getZ() + 4)).isEmpty();
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
		WorldClient level = client.theWorld;
		watch.setSelf(client.thePlayer.getName());
		for (Check check : settings.get().cheats.off) {
			suspicion.forget(check);
		}
		takeArrivedAttacks(level);
		noticeServerLag(level);
		tick++;

		for (EntityPlayer player : level.playerEntities) {
			String name = watched(player);
			if (name != null && player.isEntityAlive()) {
				watchedThisRound.add(name);
				watch.frame(name, frame(level, player));
			}
		}

		double sensitivity = settings.get().cheats.sensitivity / 100.0;
		watch.setTuning(settings.get().cheats.tuning());
		for (Violation violation : watch.endTick(tick, new LevelTerrain(level))) {
			java.util.Optional<Suspicion.Flag> flag = suspicion.record(violation,
					sensitivity * settings.get().cheats.sensitivityOf(violation.check()) / 100.0);
			de.raindancer118.cheatwatch.CheatLog logging = logging();
			if (logging != null) {
				logging.record(violation, suspicion.confidence(violation.player()), settings.get().cheats.sensitivity);
				if (flag.isPresent()) {
					logging.flagged(flag.get(), suspicion.confidence(flag.get().player()));
				}
			}
			if (telemetry != null) {
				telemetry.sighting(violation);
				if (flag.isPresent()) {
					telemetry.flag(flag.get());
				}
			}
			if (flag.isPresent()) {
				announce(flag.get());
			}
		}
		java.util.Iterator<Map.Entry<Integer, Long>> it = hurtAt.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() < tick - 20) {
				it.remove();
			}
		}
	}

	/** The swing-and-push pairs from the network, as attacks between players of different teams. */
	private void takeArrivedAttacks(WorldClient level) {
		int[] pair;
		while ((pair = arrivedAttacks.poll()) != null) {
			String attacker = watched(level.getEntityByID(pair[0]));
			String victim = watched(level.getEntityByID(pair[1]));
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
	private void noticeServerLag(WorldClient level) {
		long gameTime = level.getWorldTime();
		if (lastGameTime != Long.MIN_VALUE) {
			long step = gameTime - lastGameTime;
			if (step <= -2 || step >= 3) {
				watch.serverLag(tick + 1);
			}
		}
		lastGameTime = gameTime;
	}

	private Frame frame(WorldClient level, EntityPlayer player) {
		double feetX = player.posX;
		double feetY = player.posY;
		double feetZ = player.posZ;
		double pitch = player.rotationPitch;
		double yaw = player.rotationYawHead;

		if (player instanceof EntityOtherPlayerMP) {
			EntityOtherPlayerMPAccessor accessor = (EntityOtherPlayerMPAccessor) player;
			if (accessor.hypixelscout$posRotationIncrements() > 0) {
				// Where the server last put them, not the smoothed position drawn on its way there.
				feetX = accessor.hypixelscout$targetX();
				feetY = accessor.hypixelscout$targetY();
				feetZ = accessor.hypixelscout$targetZ();
				pitch = accessor.hypixelscout$targetPitch();
			}
			// rotationYawHead is set directly by the head-look packet: no lerp to look past here.
		}

		AxisAlignedBB current = player.getEntityBoundingBox();
		AxisAlignedBB box = current.offset(feetX - player.posX, feetY - player.posY, feetZ - player.posZ);
		AxisAlignedBB below = new AxisAlignedBB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ);
		boolean assisted = player.isOnLadder() || player.isInWater() || player.isInLava() || inWeb(level, box);

		return new Frame(tick, new Vec(feetX, feetY, feetZ), new Vec(feetX, feetY + player.getEyeHeight(), feetZ),
				new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ), yaw, pitch, player.onGround, !level.getCollidingBoundingBoxes(player, below).isEmpty(),
				player.isSprinting(), player.isUsingItem(), assisted, player.isRiding(), player.isSneaking(),
				held(player));
	}

	private static Frame.Held held(EntityPlayer player) {
		ItemStack stack = player.getHeldItem();
		if (stack == null) {
			return Frame.Held.NOTHING;
		}
		Item item = stack.getItem();
		if (item instanceof ItemSword) {
			return Frame.Held.SWORD;
		}
		return item instanceof ItemBlock ? Frame.Held.BLOCK : Frame.Held.OTHER;
	}

	private static boolean inWeb(WorldClient level, AxisAlignedBB box) {
		BlockPos min = new BlockPos(box.minX, box.minY, box.minZ);
		BlockPos max = new BlockPos(box.maxX, box.maxY, box.maxZ);
		for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
			if (level.getBlockState(pos).getBlock() instanceof BlockWeb) {
				return true;
			}
		}
		return false;
	}

	private static boolean solid(WorldClient level, BlockPos pos) {
		if (!level.isBlockLoaded(pos)) {
			return false;
		}
		IBlockState state = level.getBlockState(pos);
		return state.getBlock() != Blocks.air
				&& state.getBlock().getCollisionBoundingBox(level, pos, state) != null;
	}

	/**
	 * {@link CheatWatch.Terrain} over the real world: {@link #solid} keeps treating any collision box
	 * as a full cube, for the callers that only need that coarse answer (knockback's wall and
	 * ceiling, the bed check); {@link #solidAt} looks at the block's actual collision boxes — 1.8.9
	 * has no per-shape voxel data, so a multi-box block (a fence, a wall) is asked for every box via
	 * {@code addCollisionBoxesToList} — so a slab, a stair, a bed, a carpet or a snow layer does not
	 * block a sight line through the rest of its cell.
	 */
	private static final class LevelTerrain implements CheatWatch.Terrain {
		private final WorldClient level;

		LevelTerrain(WorldClient level) {
			this.level = level;
		}

		@Override
		public boolean solid(int x, int y, int z) {
			return CheatSensor.solid(level, new BlockPos(x, y, z));
		}

		@Override
		public boolean solidAt(double x, double y, double z) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isBlockLoaded(pos)) {
				return false;
			}
			IBlockState state = level.getBlockState(pos);
			Block block = state.getBlock();
			if (block == Blocks.air) {
				return false;
			}
			List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
			AxisAlignedBB cell = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1,
					pos.getZ() + 1);
			block.addCollisionBoxesToList(level, pos, state, cell, boxes, null);
			for (AxisAlignedBB box : boxes) {
				if (x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ) {
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
		IChatComponent line = new ChatComponentTranslation("message.hypixelscout.cheat.flagged",
				coloured(flag.player(), team), coloured(flag.check().label(), null), flag.detail(),
				Suspects.percent(suspicion.confidence(flag.player())));
		line = line.appendText(" ").appendSibling(reportLinks())
				.appendText(" ")
				.appendSibling(link("message.hypixelscout.cheat.wrong",
						"/scout cheats wrong " + flag.player() + " " + flag.check().name().toLowerCase(Locale.ROOT)));
		Chat.say(line);
	}

	private static IChatComponent coloured(String text, Teams.Team team) {
		ChatComponentText component = new ChatComponentText(text);
		if (team != null && team != Teams.NONE) {
			component.getChatStyle().setColor(net.minecraft.util.EnumChatFormatting.RED);
		}
		return component;
	}

	/** {@code [→ Party] [→ Team]}: a click sends every flag of the round, the way the command does. */
	public static IChatComponent reportLinks() {
		return new ChatComponentText("")
				.appendSibling(link("message.hypixelscout.cheat.to_party", "/scout cheats party"))
				.appendText(" ")
				.appendSibling(link("message.hypixelscout.cheat.to_team", "/scout cheats team"));
	}

	private static IChatComponent link(String key, String command) {
		IChatComponent component = new ChatComponentTranslation(key);
		ChatStyle style = new ChatStyle();
		style.setColor(net.minecraft.util.EnumChatFormatting.AQUA);
		style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentTranslation(key + ".hover")));
		component.setChatStyle(style);
		return component;
	}
}
