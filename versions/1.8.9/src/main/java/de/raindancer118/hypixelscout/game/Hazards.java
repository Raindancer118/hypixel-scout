package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.core.BedLedger;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.flight.Blast;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.Fall;
import de.raindancer118.hypixelscout.flight.FlightPath;
import de.raindancer118.hypixelscout.flight.IncomingWatch;
import de.raindancer118.hypixelscout.flight.Vec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * What is about to happen around the player, worked out once a tick from what the client already
 * has: primed TNT and the push it would give, where a fall ends (or that it does not), the loaded
 * layers of the bed being looked at, and enemies in plain sight outside the view. Every bed looked
 * at goes into the round's {@link BedLedger}, which outlives deaths and only a new game clears.
 *
 * <p>Ported from 26.2's {@code game.Hazards}. The one real capability gap: 1.8.9's vanilla bed block
 * ({@link BlockBed}) carries no colour at all — coloured beds are a 1.12 feature, years after this
 * client. There is no server-supplied way here to tell a red bed from a blue one directly off the
 * block. This falls back to a heuristic instead of pretending otherwise: the team-coloured wool (or
 * other {@link BlockColored}) block most common in a small radius around the bed's head, which is
 * how Hypixel's own 1.8-era Bedwars maps mark each island. Unverified against a real Hypixel map —
 * see {@code Project.md}.
 */
public final class Hazards {
	/** TNT further than this is not the player's problem: a power-4 blast reaches 8. */
	private static final double TNT_SEARCH = 24.0;
	/** A fall is worth a landing marker from this many blocks down. */
	private static final double MARKED_DROP = 4.0;
	/** A bed counts as looked at if the crosshair lands this close to it. */
	private static final int BED_SEARCH = BedDefense.RADIUS;
	private static final double BED_REACH = 64.0;
	private static final int MAX_EDGE_MARKERS = 6;
	/** How often the beds on record are checked for still being there. */
	private static final int LEDGER_CHECK_TICKS = 20;
	/** How far out from the bed's head the surrounding wool is sampled for its team colour. */
	private static final int DYE_SEARCH = 3;

	/** Primed TNT: its fall, where it goes off, when, and what it would do to the player. */
	public static final class Tnt {
		private final int id;
		private final List<Vec> path;
		private final Vec center;
		private final double reach;
		private final int fuse;
		private final Blast.Knock knock;

		Tnt(int id, List<Vec> path, Vec center, double reach, int fuse, Blast.Knock knock) {
			this.id = id;
			this.path = path;
			this.center = center;
			this.reach = reach;
			this.fuse = fuse;
			this.knock = knock;
		}

		public int id() {
			return id;
		}

		public List<Vec> path() {
			return path;
		}

		public Vec center() {
			return center;
		}

		public double reach() {
			return reach;
		}

		public int fuse() {
			return fuse;
		}

		public Blast.Knock knock() {
			return knock;
		}

		public double seconds() {
			return fuse / 20.0;
		}
	}

	/** A bed looked at, and its defence from outside; {@code dye} is its colour's id ({@code light_blue}). */
	public static final class Bed {
		private final String colour;
		private final String dye;
		private final BlockPos head;
		private final List<BedDefense.Cell> cells;
		private final BedDefense.Report report;

		Bed(String colour, String dye, BlockPos head, List<BedDefense.Cell> cells, BedDefense.Report report) {
			this.colour = colour;
			this.dye = dye;
			this.head = head;
			this.cells = cells;
			this.report = report;
		}

		public String colour() {
			return colour;
		}

		public String dye() {
			return dye;
		}

		public BlockPos head() {
			return head;
		}

		public List<BedDefense.Cell> cells() {
			return cells;
		}

		public BedDefense.Report report() {
			return report;
		}
	}

	/** An enemy outside the view, in plain sight. */
	public static final class Offscreen {
		private final String name;
		private final int rgb;
		private final double bearing;
		private final double distance;

		Offscreen(String name, int rgb, double bearing, double distance) {
			this.name = name;
			this.rgb = rgb;
			this.bearing = bearing;
			this.distance = distance;
		}

		public String name() {
			return name;
		}

		public int rgb() {
			return rgb;
		}

		public double bearing() {
			return bearing;
		}

		public double distance() {
			return distance;
		}
	}

	private final Roster roster;
	private final Supplier<ScoutSettings> settings;

	private List<Tnt> tnt = new ArrayList<Tnt>();
	private Fall fall;
	private Bed bed;
	private List<Offscreen> offscreen = new ArrayList<Offscreen>();
	private final BedLedger ledger = new BedLedger(Clock.SYSTEM);
	private int ledgerTicks;

	public Hazards(Roster roster, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.settings = settings;
	}

	public boolean active(Minecraft client) {
		return client.thePlayer != null && client.theWorld != null
				&& (!settings.get().projectiles.onlyInGame || roster.isInGame());
	}

	public void tick(Minecraft client) {
		if (!active(client)) {
			reset();
			return;
		}

		ScoutSettings.Awareness options = settings.get().awareness;
		EntityPlayerSP player = client.thePlayer;
		WorldClient level = client.theWorld;

		tnt = options.tnt ? tnt(level, player) : new ArrayList<Tnt>();
		fall = options.voidWarning ? fall(level, player) : null;
		bed = options.bedDefense ? bed(level, player) : null;
		if (bed != null) {
			ledger.record(bed.dye(), bed.cells(), bed.report());
		}
		if (++ledgerTicks >= LEDGER_CHECK_TICKS) {
			ledgerTicks = 0;
			checkLedger(level);
		}
		offscreen = options.offscreen ? offscreen(client, level, player, options.offscreenRange) : new ArrayList<Offscreen>();
	}

	/** What is shown right now goes; the beds on record stay — a death does not end the round. */
	public void reset() {
		tnt = new ArrayList<Tnt>();
		fall = null;
		bed = null;
		offscreen = new ArrayList<Offscreen>();
	}

	/** Every primed TNT near the player, the most dangerous first. */
	public List<Tnt> tnt() {
		return tnt;
	}

	/** The fall the player is in, when it is worth showing: into the void, or far down; else {@code null}. */
	public Fall fall() {
		return fall;
	}

	public Bed bed() {
		return bed;
	}

	/** Every bed looked at this round, as last seen. */
	public BedLedger ledger() {
		return ledger;
	}

	/** A new game: the beds on record were somebody else's. */
	public void newRound() {
		reset();
		ledger.clear();
		ledgerTicks = 0;
	}

	public List<Offscreen> offscreen() {
		return offscreen;
	}

	// --- TNT ------------------------------------------------------------------------------------

	private static List<Tnt> tnt(WorldClient level, EntityPlayerSP player) {
		List<Tnt> found = new ArrayList<Tnt>();
		Box self = Flights.box(player.getEntityBoundingBox());
		FlightPath.Obstacle rays = Flights.obstacle(level, player);

		for (Entity entity : level.loadedEntityList) {
			if (!(entity instanceof EntityTNTPrimed) || entity.getDistanceToEntity(player) > TNT_SEARCH) {
				continue;
			}
			EntityTNTPrimed primed = (EntityTNTPrimed) entity;
			Blast.Tnt blast = Blast.tnt(Flights.vec(entity.getPositionVector()),
					new Vec(entity.motionX, entity.motionY, entity.motionZ), primed.fuse, Flights.obstacle(level, entity));
			double exposure = Blast.exposure(self, blast.center(), rays);
			Blast.Knock knock = Blast.knockback(blast.center(), Blast.TNT_POWER, Flights.vec(player.getPositionVector()),
					Flights.vec(player.getPositionEyes(1.0f)), exposure);
			found.add(new Tnt(entity.getEntityId(), blast.path(), blast.center(), Blast.reach(Blast.TNT_POWER),
					primed.fuse, knock));
		}

		java.util.Collections.sort(found, new Comparator<Tnt>() {
			@Override
			public int compare(Tnt a, Tnt b) {
				double sa = a.knock() == null ? 0.0 : -a.knock().strength();
				double sb = b.knock() == null ? 0.0 : -b.knock().strength();
				int cmp = Double.compare(sa, sb);
				return cmp != 0 ? cmp : Integer.compare(a.fuse(), b.fuse());
			}
		});
		return found;
	}

	// --- falls ----------------------------------------------------------------------------------

	private static Fall fall(WorldClient level, EntityPlayerSP player) {
		if (player.onGround || player.capabilities.isFlying || player.isInWater() || player.isRiding()
				|| player.isSpectator()) {
			return null;
		}

		Fall fall = Fall.predict(Flights.vec(player.getPositionVector()),
				new Vec(player.motionX, player.motionY, player.motionZ), player.width / 2.0,
				Flights.obstacle(level, player), Flights.voidY(), 200);
		if (fall.intoTheVoid()) {
			return fall;
		}
		return fall.landing() != null && player.posY - fall.landing().y() >= MARKED_DROP ? fall : null;
	}

	// --- beds -----------------------------------------------------------------------------------

	private static Bed bed(WorldClient level, EntityPlayerSP player) {
		MovingObjectPosition hit = player.rayTrace(BED_REACH, 1.0f);
		if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
			return null;
		}

		BlockPos centre = hit.getBlockPos();
		BlockPos head = null;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.getAllInBox(centre.add(-BED_SEARCH, -BED_SEARCH, -BED_SEARCH),
				centre.add(BED_SEARCH, BED_SEARCH, BED_SEARCH))) {
			if (level.getBlockState(pos).getBlock() instanceof BlockBed && pos.distanceSq(centre) < best) {
				best = pos.distanceSq(centre);
				head = pos.getImmutable();
			}
		}
		if (head == null) {
			return null;
		}

		IBlockState state = level.getBlockState(head);
		List<BedDefense.Cell> cells = new ArrayList<BedDefense.Cell>();
		cells.add(cell(head));
		BlockPos other = otherHalf(head, state);
		if (level.getBlockState(other).getBlock() instanceof BlockBed) {
			cells.add(cell(other));
		}

		// In one order whichever half the crosshair found, so every look adds to the same bed.
		java.util.Collections.sort(cells, new Comparator<BedDefense.Cell>() {
			@Override
			public int compare(BedDefense.Cell a, BedDefense.Cell b) {
				if (a.x() != b.x()) return Integer.compare(a.x(), b.x());
				if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
				return Integer.compare(a.z(), b.z());
			}
		});
		BedDefense.Report report = BedDefense.analyse(cells, new BedDefense.World() {
			@Override
			public BedDefense.Block at(BedDefense.Cell at) {
				return block(level, new BlockPos(at.x(), at.y(), at.z()));
			}
		});
		String dye = nearestDye(level, head);
		return new Bed(BedLedger.teamOf(dye), dye, head, java.util.Collections.unmodifiableList(cells), report);
	}

	/** The other half of a bed, from the head/foot direction the way vanilla connects them. */
	private static BlockPos otherHalf(BlockPos pos, IBlockState state) {
		EnumFacing facing = (EnumFacing) state.getValue(BlockDirectional.FACING);
		boolean isHead = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD;
		return pos.offset(isHead ? facing.getOpposite() : facing);
	}

	/**
	 * The majority wool (or other dyed block) colour within {@link #DYE_SEARCH} of the bed's head —
	 * the closest thing 1.8.9 has to the colour actually painted on a modern bed. {@code "white"} when
	 * nothing dyed is found nearby.
	 */
	private static String nearestDye(WorldClient level, BlockPos head) {
		Map<String, Integer> counts = new HashMap<String, Integer>();
		for (BlockPos pos : BlockPos.getAllInBox(head.add(-DYE_SEARCH, -DYE_SEARCH, -DYE_SEARCH),
				head.add(DYE_SEARCH, DYE_SEARCH, DYE_SEARCH))) {
			IBlockState state = level.getBlockState(pos);
			Block block = state.getBlock();
			if (block instanceof BlockColored) {
				EnumDyeColor colour = (EnumDyeColor) state.getValue(BlockColored.COLOR);
				String name = colour.getName();
				Integer count = counts.get(name);
				counts.put(name, count == null ? 1 : count + 1);
			}
		}
		String best = null;
		int bestCount = 0;
		for (Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() > bestCount) {
				bestCount = entry.getValue();
				best = entry.getKey();
			}
		}
		// 1.8.9 calls light grey "silver"; every other name matches BedLedger.teamOf already.
		return best == null ? "white" : "silver".equals(best) ? "gray" : best;
	}

	/** Further than this a block going up is not something the player watched. */
	private static final double WATCH_RANGE = 128.0;

	/**
	 * A block about to appear where there was none. Placed within a bed's defence and in plain sight
	 * of the player — a clear line from the eyes to it, as it goes up on the outside — it joins what
	 * is known of that defence: the player watched it being built. Placed out of sight, it does not.
	 */
	public void placed(WorldClient level, EntityPlayerSP player, BlockPos pos, IBlockState next) {
		if (!settings.get().awareness.bedDefense || next.getBlock() instanceof BlockBed
				|| next.getBlock().getCollisionBoundingBox(level, pos, next) == null) {
			return;
		}
		Vec3 centre = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		Vec3 eye = player.getPositionEyes(1.0f);
		if (eye.distanceTo(centre) > WATCH_RANGE) {
			return;
		}

		BlockPos bedPos = null;
		for (BlockPos near : BlockPos.getAllInBox(pos.add(-BED_SEARCH, -BED_SEARCH, -BED_SEARCH),
				pos.add(BED_SEARCH, 0, BED_SEARCH))) {
			if (level.getBlockState(near).getBlock() instanceof BlockBed) {
				bedPos = near.getImmutable();
				break;
			}
		}
		if (bedPos == null) {
			return;
		}
		IBlockState bedState = level.getBlockState(bedPos);
		List<BedDefense.Cell> cells = new ArrayList<BedDefense.Cell>();
		cells.add(cell(bedPos));
		BlockPos other = otherHalf(bedPos, bedState);
		if (level.getBlockState(other).getBlock() instanceof BlockBed) {
			cells.add(cell(other));
		}
		java.util.Collections.sort(cells, new Comparator<BedDefense.Cell>() {
			@Override
			public int compare(BedDefense.Cell a, BedDefense.Cell b) {
				if (a.x() != b.x()) return Integer.compare(a.x(), b.x());
				if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
				return Integer.compare(a.z(), b.z());
			}
		});
		if (!BedDefense.partOf(cells, cell(pos)) || !inSight(level, player, eye, pos, centre)) {
			return;
		}
		String dye = nearestDye(level, bedPos);
		ledger.watched(cells, dye, cell(pos), new BedDefense.Block(next.getBlock().getLocalizedName(),
				next.getBlock().getBlockHardness(level, pos)));
	}

	/** Nothing solid between the eyes and the block about to go up. */
	private static boolean inSight(WorldClient level, EntityPlayerSP player, Vec3 eye, BlockPos pos, Vec3 centre) {
		MovingObjectPosition hit = level.rayTraceBlocks(eye, centre);
		return hit == null || hit.getBlockPos().equals(pos) || hit.hitVec.distanceTo(centre) < 0.9;
	}

	/** A bed on record whose head is loaded but no longer a bed has been broken. */
	private void checkLedger(WorldClient level) {
		for (BedLedger.Entry entry : ledger.standing()) {
			BlockPos head = new BlockPos(entry.head().x(), entry.head().y(), entry.head().z());
			if (level.isBlockLoaded(head) && !(level.getBlockState(head).getBlock() instanceof BlockBed)) {
				ledger.markGone(entry.team());
			}
		}
	}

	private static BedDefense.Cell cell(BlockPos pos) {
		return new BedDefense.Cell(pos.getX(), pos.getY(), pos.getZ());
	}

	/** A block with a collision box; anything you can walk or throw through counts as air. */
	private static BedDefense.Block block(WorldClient level, BlockPos pos) {
		IBlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (block == Blocks.air || block.getCollisionBoundingBox(level, pos, state) == null) {
			return null;
		}
		return new BedDefense.Block(block.getLocalizedName(), block.getBlockHardness(level, pos));
	}

	// --- enemies out of view --------------------------------------------------------------------

	private static List<Offscreen> offscreen(Minecraft client, WorldClient level, EntityPlayerSP player, int range) {
		Vec3 eye = player.getPositionEyes(1.0f);
		Vec look = Flights.vec(player.getLook(1.0f));
		double viewCos = Math.cos(Flights.halfViewAngle(client));

		List<Offscreen> found = new ArrayList<Offscreen>();
		for (EntityPlayer other : level.playerEntities) {
			if (other == player || other.isSpectator() || !other.isEntityAlive() || other.isInvisibleToPlayer(player)
					|| Teams.isOwnTeam(other.getName()) || other.getDistanceToEntity(player) > range) {
				continue;
			}

			Vec towards = Flights.vec(other.getPositionEyes(1.0f).subtract(eye));
			double distance = towards.length();
			if (distance < 1e-6 || towards.scale(1.0 / distance).dot(look) >= viewCos) {
				continue;
			}
			if (!inPlainSight(level, player, eye, other)) {
				continue;
			}

			double bearing = IncomingWatch.bearing(Flights.vec(other.getPositionEyes(1.0f)), Flights.vec(eye), look);
			found.add(new Offscreen(other.getName(), Teams.of(other.getName()).rgb(), bearing, distance));
		}

		java.util.Collections.sort(found, new Comparator<Offscreen>() {
			@Override
			public int compare(Offscreen a, Offscreen b) {
				return Double.compare(a.distance(), b.distance());
			}
		});
		return found.subList(0, Math.min(found.size(), MAX_EDGE_MARKERS));
	}

	/** A clear line from the eyes to the enemy's head or middle: seen if the player turned round. */
	private static boolean inPlainSight(WorldClient level, EntityPlayerSP player, Vec3 eye, EntityPlayer other) {
		Vec3[] targets = {other.getPositionEyes(1.0f),
				new Vec3(other.posX, other.posY + other.height / 2.0, other.posZ)};
		for (Vec3 target : targets) {
			MovingObjectPosition hit = level.rayTraceBlocks(eye, target);
			if (hit == null) {
				return true;
			}
		}
		return false;
	}
}
