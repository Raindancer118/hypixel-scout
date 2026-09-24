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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * What is about to happen around the player, worked out once a tick from what the client already
 * has: primed TNT and the push it would give, where a fall ends (or that it does not), the outside
 * of the bed being looked at, and enemies in plain sight outside the view. Every bed looked at goes
 * into the round's {@link BedLedger}, which outlives deaths and only a new game clears.
 *
 * <p>Nothing here looks through walls. TNT and falls are physics of things in sight; the bed shows
 * only blocks with a face to the air; an edge marker needs a clear line from the player's eyes to
 * the enemy, and an invisible enemy never gets one.
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

	/** Primed TNT: its fall, where it goes off, when, and what it would do to the player. */
	public record Tnt(int id, List<Vec> path, Vec center, double reach, int fuse, Blast.Knock knock) {
		public double seconds() {
			return fuse / 20.0;
		}
	}

	/** A bed looked at, and its defence from outside; {@code dye} is its colour's id ({@code light_blue}). */
	public record Bed(String colour, String dye, BlockPos head, BedDefense.Report report) {
	}

	/** An enemy outside the view, in plain sight. */
	public record Offscreen(String name, int rgb, double bearing, double distance) {
	}

	private final Roster roster;
	private final Supplier<ScoutSettings> settings;

	private List<Tnt> tnt = List.of();
	private Fall fall;
	private Bed bed;
	private List<Offscreen> offscreen = List.of();
	private final BedLedger ledger = new BedLedger(Clock.SYSTEM);
	private int ledgerTicks;

	public Hazards(Roster roster, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.settings = settings;
	}

	public boolean active(Minecraft client) {
		return client.player != null && client.level != null
				&& (!settings.get().projectiles.onlyInGame || roster.isInGame());
	}

	public void tick(Minecraft client) {
		if (!active(client)) {
			reset();
			return;
		}

		ScoutSettings.Awareness options = settings.get().awareness;
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		tnt = options.tnt ? tnt(level, player) : List.of();
		fall = options.voidWarning ? fall(level, player) : null;
		bed = options.bedDefense ? bed(level, player) : null;
		if (bed != null) {
			ledger.record(bed.dye(), cell(bed.head()), bed.report());
		}
		if (++ledgerTicks >= LEDGER_CHECK_TICKS) {
			ledgerTicks = 0;
			checkLedger(level);
		}
		offscreen = options.offscreen ? offscreen(client, level, player, options.offscreenRange) : List.of();
	}

	/** What is shown right now goes; the beds on record stay — a death does not end the round. */
	public void reset() {
		tnt = List.of();
		fall = null;
		bed = null;
		offscreen = List.of();
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

	private static List<Tnt> tnt(ClientLevel level, LocalPlayer player) {
		List<Tnt> found = new ArrayList<>();
		Box self = Flights.box(player.getBoundingBox());
		FlightPath.Obstacle rays = Flights.obstacle(level, player);

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof PrimedTnt primed) || entity.distanceTo(player) > TNT_SEARCH) {
				continue;
			}
			Blast.Tnt blast = Blast.tnt(Flights.vec(entity.position()), Flights.vec(entity.getDeltaMovement()),
					primed.getFuse(), Flights.obstacle(level, entity));
			double exposure = Blast.exposure(self, blast.center(), rays);
			Blast.Knock knock = Blast.knockback(blast.center(), Blast.TNT_POWER, Flights.vec(player.position()),
					Flights.vec(player.getEyePosition()), exposure);
			found.add(new Tnt(entity.getId(), blast.path(), blast.center(), Blast.reach(Blast.TNT_POWER),
					primed.getFuse(), knock));
		}

		found.sort(Comparator.comparingDouble((Tnt t) -> t.knock() == null ? 0.0 : -t.knock().strength())
				.thenComparingInt(Tnt::fuse));
		return List.copyOf(found);
	}

	// --- falls ----------------------------------------------------------------------------------

	private static Fall fall(ClientLevel level, LocalPlayer player) {
		if (player.onGround() || player.getAbilities().flying || player.isFallFlying() || player.isInWater()
				|| player.isPassenger() || player.isSpectator()) {
			return null;
		}

		Fall fall = Fall.predict(Flights.vec(player.position()), Flights.vec(player.getDeltaMovement()),
				player.getBbWidth() / 2.0, Flights.obstacle(level, player), level.getMinY(), 200);
		if (fall.intoTheVoid()) {
			return fall;
		}
		return fall.landing() != null && player.getY() - fall.landing().y() >= MARKED_DROP ? fall : null;
	}

	// --- beds -----------------------------------------------------------------------------------

	private static Bed bed(ClientLevel level, LocalPlayer player) {
		HitResult hit = player.pick(BED_REACH, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos centre = blockHit.getBlockPos();
		BlockPos head = null;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-BED_SEARCH, -BED_SEARCH, -BED_SEARCH),
				centre.offset(BED_SEARCH, BED_SEARCH, BED_SEARCH))) {
			if (level.getBlockState(pos).getBlock() instanceof BedBlock && pos.distSqr(centre) < best) {
				best = pos.distSqr(centre);
				head = pos.immutable();
			}
		}
		if (head == null) {
			return null;
		}

		BlockState state = level.getBlockState(head);
		List<BedDefense.Cell> cells = new ArrayList<>();
		cells.add(cell(head));
		BlockPos other = head.relative(BedBlock.getConnectedDirection(state));
		if (level.getBlockState(other).getBlock() instanceof BedBlock) {
			cells.add(cell(other));
		}

		BedDefense.Report report = BedDefense.analyse(cells, at -> block(level, new BlockPos(at.x(), at.y(), at.z())));
		String dye = ((BedBlock) state.getBlock()).getColor().getName();
		return new Bed(dye.toUpperCase(Locale.ROOT).replace('_', ' '), dye, head, report);
	}

	/** A bed on record whose head is loaded but no longer a bed has been broken. */
	private void checkLedger(ClientLevel level) {
		for (BedLedger.Entry entry : ledger.standing()) {
			BlockPos head = new BlockPos(entry.head().x(), entry.head().y(), entry.head().z());
			if (level.isLoaded(head) && !(level.getBlockState(head).getBlock() instanceof BedBlock)) {
				ledger.markGone(entry.team());
			}
		}
	}

	private static BedDefense.Cell cell(BlockPos pos) {
		return new BedDefense.Cell(pos.getX(), pos.getY(), pos.getZ());
	}

	/** A block with a collision box; anything you can walk or throw through counts as air. */
	private static BedDefense.Block block(ClientLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
			return null;
		}
		return new BedDefense.Block(state.getBlock().getName().getString(), state.getDestroySpeed(level, pos));
	}

	// --- enemies out of view --------------------------------------------------------------------

	private static List<Offscreen> offscreen(Minecraft client, ClientLevel level, LocalPlayer player, int range) {
		Vec3 eye = player.getEyePosition();
		Vec look = Flights.vec(player.getViewVector(1.0f));
		double viewCos = Math.cos(Flights.halfViewAngle(client));

		List<Offscreen> found = new ArrayList<>();
		for (Player other : level.players()) {
			if (other == player || other.isSpectator() || !other.isAlive() || other.isInvisibleTo(player)
					|| Teams.isOwnTeam(other.getScoreboardName()) || other.distanceTo(player) > range) {
				continue;
			}

			Vec towards = Flights.vec(other.getEyePosition().subtract(eye));
			double distance = towards.length();
			if (distance < 1e-6 || towards.scale(1.0 / distance).dot(look) >= viewCos) {
				continue;
			}
			if (!inPlainSight(level, player, eye, other)) {
				continue;
			}

			double bearing = IncomingWatch.bearing(Flights.vec(other.getEyePosition()), Flights.vec(eye), look);
			found.add(new Offscreen(other.getScoreboardName(), Teams.of(other.getScoreboardName()).rgb(), bearing, distance));
		}

		found.sort(Comparator.comparingDouble(Offscreen::distance));
		return List.copyOf(found.subList(0, Math.min(found.size(), MAX_EDGE_MARKERS)));
	}

	/** A clear line from the eyes to the enemy's head or middle: seen if the player turned round. */
	private static boolean inPlainSight(ClientLevel level, LocalPlayer player, Vec3 eye, Player other) {
		for (Vec3 target : new Vec3[] {other.getEyePosition(), other.position().add(0, other.getBbHeight() / 2, 0)}) {
			BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
