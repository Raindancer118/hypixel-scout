package de.raindancer118.hypixelscout.cheat;

import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.Vec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Watches the other players for what only a cheat makes possible, from nothing but what the client
 * is told anyway: where everybody is and looks, who swings, who hits whom, which blocks appear and
 * which beds vanish.
 *
 * <p>Fed in the order a client tick has it: the packets first ({@link #swing}, {@link #attack},
 * {@link #hurt}, {@link #placed}, …), then a {@link #frame} for every player in sight, then
 * {@link #endTick}, which judges whatever there is enough of to judge and returns the sightings —
 * and the reliefs, where somebody did the legit thing a check looks for. Some judgements wait a few
 * ticks — knockback needs to be seen landing — so a sighting can come a little after the fact.
 *
 * <p>Every check leans towards silence. Positions arrive twenty times a second and a little late,
 * so distances are measured at the most favourable of several aligned ticks, and a moving pair gets
 * more room than a standing one; anything with a second explanation — a blast, a hit, somebody else
 * close by, the player themselves, a lagging server — is left alone. What is left is still only a
 * sighting: {@link Suspicion} decides when sightings make a flag.
 *
 * <p>The player running the client ({@link #setSelf}) is never a suspect, but counts as a witness:
 * their own swing explains a hit, their own block explains a placement.
 *
 * <p>Several ideas here were studied from two open-source client-side detectors, only one of which
 * this project may copy from. Alexdoru's HackerDetector (MegaWallsEnhancements) is under its own
 * custom, non-commercial licence, not MIT — the swing-then-push attribution, hits through walls,
 * KeepSprint, backwards-bridging Scaffold and reliefs it inspired here were written fresh from
 * studying its ideas, no code was copied from it. Iustitia (ThoriaDevelopment/Iustitia) is MIT, and
 * both its ideas and its algorithms and thresholds — the split reach limit, multi-aura, sprinting
 * while sneaking, the invulnerability gap for knockback, the server-lag pause, and the checks marked
 * below as derived from it — are used here with attribution; see {@code THIRD_PARTY_NOTICES.md} in
 * the repository root for the full licence text.
 */
public final class CheatWatch {
	/** The blocks of the world, as far as the checks care: solid or not. */
	@FunctionalInterface
	public interface Terrain {
		boolean solid(int x, int y, int z);

		/**
		 * The same block, but at the exact point rather than the whole cell: a slab, a stair, a bed, a
		 * carpet, a fence or a snow layer do not fill their cell everywhere {@link #solid} says so.
		 * Callers that only need the coarse, whole-cell answer (knockback's wall and ceiling, the bed
		 * check) keep using {@link #solid}; only a sight line needs to know exactly where a shape is.
		 * The default falls back to the coarse cell, for terrains — tests, mostly — with nothing finer.
		 */
		default boolean solidAt(double x, double y, double z) {
			return solid((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		}
	}

	/** What hurt somebody, as far as the client can tell. */
	public enum Hit {
		/** A punch or a sword. */
		MELEE,
		/** An arrow, a fall, a blast, a mob — anything that is nobody's reach. */
		OTHER,
		/** No telling: judged like melee, but only where a single swinging player fits. */
		UNKNOWN
	}

	/**
	 * The limits the player may move: reach standing and moving (blocks), speed (blocks a tick over a
	 * second), FastPlace (blocks a second) and backwards bridging (blocks a tick).
	 */
	public static final class Tuning {
		public static final Tuning DEFAULT = new Tuning(3.2, 3.8, 12.4 / 20, 13, 5.0 / 20);

		private final double reachStanding;
		private final double reachMoving;
		private final double speedLimit;
		private final int fastPlaceLimit;
		private final double bridgeSpeed;

		public Tuning(double reachStanding, double reachMoving, double speedLimit, int fastPlaceLimit,
				double bridgeSpeed) {
			this.reachStanding = reachStanding;
			this.reachMoving = reachMoving;
			this.speedLimit = speedLimit;
			this.fastPlaceLimit = fastPlaceLimit;
			this.bridgeSpeed = bridgeSpeed;
		}

		public double reachStanding() {
			return reachStanding;
		}

		public double reachMoving() {
			return reachMoving;
		}

		public double speedLimit() {
			return speedLimit;
		}

		public int fastPlaceLimit() {
			return fastPlaceLimit;
		}

		public double bridgeSpeed() {
			return bridgeSpeed;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Tuning)) return false;
			Tuning other = (Tuning) obj;
			return fastPlaceLimit == other.fastPlaceLimit
					&& Double.doubleToLongBits(reachStanding) == Double.doubleToLongBits(other.reachStanding)
					&& Double.doubleToLongBits(reachMoving) == Double.doubleToLongBits(other.reachMoving)
					&& Double.doubleToLongBits(speedLimit) == Double.doubleToLongBits(other.speedLimit)
					&& Double.doubleToLongBits(bridgeSpeed) == Double.doubleToLongBits(other.bridgeSpeed);
		}

		@Override
		public int hashCode() {
			return Objects.hash(reachStanding, reachMoving, speedLimit, fastPlaceLimit, bridgeSpeed);
		}

		@Override
		public String toString() {
			return "Tuning[reachStanding=" + reachStanding + ", reachMoving=" + reachMoving + ", speedLimit="
					+ speedLimit + ", fastPlaceLimit=" + fastPlaceLimit + ", bridgeSpeed=" + bridgeSpeed + "]";
		}
	}

	/** Frames kept per player: three seconds. */
	private static final int HISTORY = 60;
	/** A hit this close is plainly within reach, and speaks for the attacker. */
	private static final double REACH_FAIR = 3.0;
	private static final double HITBOX_BORDER = 0.1;
	/** Further than this from the victim a swing was aimed at somebody else. */
	private static final double ATTACK_RANGE = 6.0;
	/** Positions lag; distances are the best of this many aligned ticks back. */
	private static final int LAG_TICKS = 5;
	/** Degrees off the look, past the edge of the victim's hitbox, that no hit can come from. */
	private static final double AURA_ANGLE = 75.0;
	/** For a hurt of unknown cause, a swinging player further off their look than this did not do it. */
	private static final double UNSURE_ANGLE = 45.0;
	/** The last hits per attacker the through-walls rate is taken over, and how many it needs: four,
	 * not three, so a single lagging frame that only looks occluded near a doorway or a corner is not,
	 * on its own, half of a window of two. */
	private static final int WALL_WINDOW = 12;
	private static final int WALL_MIN = 4;
	/** Hits per attacker the KeepSprint pattern is taken over, and how many must keep full speed. */
	private static final int KEEPSPRINT_WINDOW = 4;
	private static final int KEEPSPRINT_MIN = 3;
	private static final int KNOCKBACK_TICKS = 6;
	/** A hit this soon after the last one lands inside its invulnerability and pushes less. */
	private static final int INVULNERABLE_TICKS = 10;
	/** The push a 1.8 hit gives when the server does not say. */
	private static final double DEFAULT_KNOCKBACK = 0.4;
	/** An explosion this close shoves a player, or opens a bed. */
	private static final double BLAST_RADIUS = 8.0;
	private static final double PLACE_REACH = 5.5;
	private static final double BED_REACH = 6.5;
	/** Blocks in one tick, close together: a pop-up tower or some other placed structure. */
	private static final int STRUCTURE_BLOCKS = 4;
	/**
	 * A Hypixel pop-up tower or a bridge-egg lays many blocks in a burst that no hand matches, but
	 * spread over more than one tick — a ring going up over ten to fifteen ticks, say. Ticks either
	 * side of a placement, and blocks around it, counted as close enough in time and space to belong
	 * to the same burst.
	 */
	private static final int STRUCTURE_WINDOW = 15;
	private static final double STRUCTURE_RADIUS = 3.0;
	/** Two different cells landing in the very same tick, near each other: no single hand does that. */
	private static final int STRUCTURE_SAME_TICK_CELLS = 2;
	/** This many different cells within this many ticks, near each other, go up faster than clicking
	 * allows — a tower or a ring, not a hand-placed bridge (which is one new cell every tick or two). */
	private static final int STRUCTURE_BURST_TICKS = 6;
	private static final int STRUCTURE_BURST_CELLS = 5;
	/** A move this long in one tick is a teleport — a pearl, a respawn — not running. */
	private static final double TELEPORT = 4.0;
	/** Ticks after a server lag in which nothing is judged: its catch-up is not anybody's movement. */
	private static final int LAG_WINDOW = 8;
	/** Ticks both sprint and item use may overlap before it is no longer the flag catching up. */
	private static final int SPRINT_USE_TICKS = 16;

	// --- Iustitia-derived thresholds (MIT, see THIRD_PARTY_NOTICES.md) ---------------------------------

	/** ClickStats: swings in this many ticks (~a second) over the CPS cap are an autoclicker. */
	private static final int CLICK_CPS_WINDOW = 20;
	private static final double CLICK_CPS_CAP = 20.0;
	/** A nano-gap this short, right after one under 50ms, is a double-click no hand can do. */
	private static final long CLICK_ROBOT_NANOS = 10_000_000L;
	private static final long CLICK_ROBOT_PRIOR_NANOS = 50_000_000L;
	/** Population stDev of the last 40 tick-intervals below this is too uniform for a hand. */
	private static final int CLICK_STDEV_WINDOW = 40;
	private static final double CLICK_STDEV_MIN = 0.45;
	/** Excess kurtosis over 600 samples below this is a near-uniform, fixed-delay click stream. */
	private static final int CLICK_KURTOSIS_WINDOW = 600;
	private static final double CLICK_KURTOSIS_MIN = -0.7;
	/** A dig relays the server's own swing clock; intervals this short cannot be that relay. */
	private static final int DIG_RELAY_MIN_TICK_DELTA = 2;

	/** AimWrap: a wrapped-yaw snap this large in one tick, out of a still aim, is a snap. */
	private static final double AIMSNAP_THRESHOLD = 165.0;
	/** The tick before a snap must itself be under this to count as "still". */
	private static final double AIMSNAP_STILL = 30.0;
	private static final int AIMSNAP_WINDOW = 8;
	private static final int AIMSNAP_MIN = 3;

	/** RotationTracking: nearby-player search radius, and the rolling match-rate window/threshold. */
	private static final double AIMTRACK_RANGE = 6.0;
	private static final int AIMTRACK_WINDOW = 60;
	private static final double AIMTRACK_RATE = 0.92;
	private static final int AIMTRACK_COMBAT_WINDOW = 60;

	/** Triggerbot: melee look-reach, the fast-hit window, and the consistency gate over the window. */
	private static final double TRIGGER_REACH = 3.0;
	private static final double TRIGGER_RANGE = 5.0;
	private static final int TRIGGER_MAX_REACTION_TICKS = 3;
	private static final int TRIGGER_MIN_SAMPLES = 5;
	private static final int TRIGGER_WINDOW = 24;
	private static final double TRIGGER_RATIO = 0.75;
	/** A held-aim opportunity only counts as a reaction when the attacker's own aim moved this
	 * recently — otherwise the rising edge was the victim walking into a still crosshair. */
	private static final double TRIGGER_AIM_TURN_EPS = 0.25;
	private static final int TRIGGER_AIM_TURN_WINDOW = 3;

	/** HitFlick: yaw off the hitbox at the hit, the return window, and the sustained-window gate. */
	private static final double HITFLICK_THRESHOLD = 30.0;
	private static final int HITFLICK_RETURN_TICKS = 2;
	private static final double HITFLICK_RETURN_DEV = 5.0;
	private static final int HITFLICK_WINDOW = 5;
	private static final int HITFLICK_MIN = 3;

	/** MultiTarget: victims across a 2-tick union window past this many is a lag-spread multi-aura. */
	private static final int MULTIAURA_WINDOW_VICTIMS = 3;

	/** NoFall: fell at least this far (blocks) with no hurt at touchdown. */
	private static final double NOFALL_DISTANCE = 8.0;
	private static final int NOFALL_HURT_GRACE = 5;

	/** Step: a grounded rise in this range, in one tick, is higher than a leg climbs. */
	private static final double STEP_MIN = 0.6;
	private static final double STEP_MAX = 2.5;
	/** Vanilla jump height, plus a buffer this mod cannot narrow: a remote player's Jump Boost
	 * amplifier is not reliably visible, so the allowance is the same whatever the potion says. */
	private static final double STEP_JUMP_ALLOWANCE = 0.52;

	/** Blink: a freeze this many ticks (near motionless), then a jump past this many blocks. */
	private static final int BLINK_FREEZE_TICKS = 5;
	private static final double BLINK_FREEZE_EPSILON = 0.01;
	private static final double BLINK_SNAP = 2.0;

	/** Criticals: the attacker's small upward rise before a hit, and how many hits with how little
	 * spread in that rise before a fixed fall-arc phase (a hack forcing crits) is called. */
	private static final double CRIT_RISE_MIN = 0.02;
	private static final double CRIT_RISE_MAX = 0.3;
	private static final int CRIT_WINDOW = 8;
	private static final double CRIT_STDEV_MAX = 0.05;

	private static final class Track {
		final ArrayDeque<Frame> frames = new ArrayDeque<>();
		final ArrayDeque<Long> swings = new ArrayDeque<>();
		final ArrayDeque<Long> placements = new ArrayDeque<>();
		final ArrayDeque<Boolean> wallHits = new ArrayDeque<>();
		final ArrayDeque<Boolean> fullSpeedHits = new ArrayDeque<>();
		final Map<Check, Long> lastSeen = new EnumMap<>(Check.class);
		long lastShove = Long.MIN_VALUE / 2;
		long lastHurt = Long.MIN_VALUE / 2;
		long lastBridgeFlag = Long.MIN_VALUE / 2;
		Vec motion;
		long motionTick = Long.MIN_VALUE / 2;
		/** The last tick this player was resolved as an attacker; feeds AimTrack's combat gate. */
		long lastAttackTick = Long.MIN_VALUE / 2;
		/** This attacker's victims on {@link #recentVictimsTick}, for MultiAura's 2-tick union window. */
		Set<String> recentVictims = new HashSet<>();
		long recentVictimsTick = Long.MIN_VALUE / 2;

		// --- ClickStats (AutoClicker) ---------------------------------------------------------------
		long lastSwingTick = Long.MIN_VALUE / 2;
		long lastSwingNano;
		final ArrayDeque<Long> swingTicksCps = new ArrayDeque<>();
		final ArrayDeque<Integer> clickTickIntervals = new ArrayDeque<>();
		final ArrayDeque<Long> clickNanoIntervals = new ArrayDeque<>();
		boolean digging;

		// --- AimWrap (AimSnap) -----------------------------------------------------------------------
		double lastWrappedDelta;
		final ArrayDeque<Boolean> aimSnapWindow = new ArrayDeque<>();

		// --- RotationTracking (AimTrack) -------------------------------------------------------------
		final ArrayDeque<Boolean> aimTrackWindow = new ArrayDeque<>();

		// --- Triggerbot ------------------------------------------------------------------------------
		final Map<String, Boolean> onHitbox = new HashMap<>();
		final Map<String, Long> engagementStart = new HashMap<>();
		double triggerAimYaw = Double.NaN;
		double triggerAimPitch = Double.NaN;
		long lastAimMoveTick = Long.MIN_VALUE / 2;
		final ArrayDeque<Boolean> triggerbotWindow = new ArrayDeque<>();

		// --- HitFlick --------------------------------------------------------------------------------
		long flickTick = Long.MIN_VALUE / 2;
		String flickVictim;
		final ArrayDeque<Boolean> hitFlickWindow = new ArrayDeque<>();

		// --- NoFall / Step ---------------------------------------------------------------------------
		double fallPeakY = Double.NaN;

		// --- Blink -----------------------------------------------------------------------------------
		int stillStreak;

		// --- Criticals -------------------------------------------------------------------------------
		final ArrayDeque<Double> critRiseWindow = new ArrayDeque<>();

		Frame at(long tick) {
			Iterator<Frame> newest = frames.descendingIterator();
			while (newest.hasNext()) {
				Frame frame = newest.next();
				if (frame.tick() == tick) {
					return frame;
				}
				if (frame.tick() < tick) {
					return null;
				}
			}
			return null;
		}

		boolean swungWithin(long from, long to) {
			for (long swing : swings) {
				if (swing >= from && swing <= to) {
					return true;
				}
			}
			return false;
		}

		/** The frames from {@code from} to {@code to}, or {@code null} unless every one is there. */
		List<Frame> run(long from, long to) {
			List<Frame> run = new ArrayList<>();
			for (long tick = from; tick <= to; tick++) {
				Frame frame = at(tick);
				if (frame == null) {
					return null;
				}
				run.add(frame);
			}
			return run;
		}

		/** Barely moved over the last ticks up to {@code tick}: its position is what the server has. */
		boolean still(long tick) {
			Frame now = at(tick);
			Frame before = at(tick - LAG_TICKS);
			return now != null && before != null && now.horizontalFrom(before) < 0.1
					&& Math.abs(now.feet().y() - before.feet().y()) < 0.1;
		}
	}

	private static final class Swing {
		private final String player;
		private final long tick;

		Swing(String player, long tick) {
			this.player = player;
			this.tick = tick;
		}

		String player() {
			return player;
		}

		long tick() {
			return tick;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Swing)) return false;
			Swing other = (Swing) obj;
			return tick == other.tick && Objects.equals(player, other.player);
		}

		@Override
		public int hashCode() {
			return Objects.hash(player, tick);
		}

		@Override
		public String toString() {
			return "Swing[player=" + player + ", tick=" + tick + "]";
		}
	}

	/**
	 * @param prevHurt when the victim was hurt before this, for the invulnerability gap
	 */
	private static final class Hurt {
		private final String victim;
		private final long tick;
		private final Hit hit;
		private final String cause;
		private final long prevHurt;

		Hurt(String victim, long tick, Hit hit, String cause, long prevHurt) {
			this.victim = victim;
			this.tick = tick;
			this.hit = hit;
			this.cause = cause;
			this.prevHurt = prevHurt;
		}

		String victim() {
			return victim;
		}

		long tick() {
			return tick;
		}

		Hit hit() {
			return hit;
		}

		String cause() {
			return cause;
		}

		long prevHurt() {
			return prevHurt;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Hurt)) return false;
			Hurt other = (Hurt) obj;
			return tick == other.tick && prevHurt == other.prevHurt && hit == other.hit
					&& Objects.equals(victim, other.victim) && Objects.equals(cause, other.cause);
		}

		@Override
		public int hashCode() {
			return Objects.hash(victim, tick, hit, cause, prevHurt);
		}

		@Override
		public String toString() {
			return "Hurt[victim=" + victim + ", tick=" + tick + ", hit=" + hit + ", cause=" + cause
					+ ", prevHurt=" + prevHurt + "]";
		}
	}

	private static final class Attack {
		private final String attacker;
		private final String victim;
		private final long tick;

		Attack(String attacker, String victim, long tick) {
			this.attacker = attacker;
			this.victim = victim;
			this.tick = tick;
		}

		String attacker() {
			return attacker;
		}

		String victim() {
			return victim;
		}

		long tick() {
			return tick;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Attack)) return false;
			Attack other = (Attack) obj;
			return tick == other.tick && Objects.equals(attacker, other.attacker)
					&& Objects.equals(victim, other.victim);
		}

		@Override
		public int hashCode() {
			return Objects.hash(attacker, victim, tick);
		}

		@Override
		public String toString() {
			return "Attack[attacker=" + attacker + ", victim=" + victim + ", tick=" + tick + "]";
		}
	}

	private static final class Knockback {
		private final String victim;
		private final String attacker;
		private final long tick;
		private final long prevHurt;

		Knockback(String victim, String attacker, long tick, long prevHurt) {
			this.victim = victim;
			this.attacker = attacker;
			this.tick = tick;
			this.prevHurt = prevHurt;
		}

		String victim() {
			return victim;
		}

		String attacker() {
			return attacker;
		}

		long tick() {
			return tick;
		}

		long prevHurt() {
			return prevHurt;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Knockback)) return false;
			Knockback other = (Knockback) obj;
			return tick == other.tick && prevHurt == other.prevHurt && Objects.equals(victim, other.victim)
					&& Objects.equals(attacker, other.attacker);
		}

		@Override
		public int hashCode() {
			return Objects.hash(victim, attacker, tick, prevHurt);
		}

		@Override
		public String toString() {
			return "Knockback[victim=" + victim + ", attacker=" + attacker + ", tick=" + tick
					+ ", prevHurt=" + prevHurt + "]";
		}
	}

	private static final class SprintHit {
		private final String attacker;
		private final long tick;

		SprintHit(String attacker, long tick) {
			this.attacker = attacker;
			this.tick = tick;
		}

		String attacker() {
			return attacker;
		}

		long tick() {
			return tick;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof SprintHit)) return false;
			SprintHit other = (SprintHit) obj;
			return tick == other.tick && Objects.equals(attacker, other.attacker);
		}

		@Override
		public int hashCode() {
			return Objects.hash(attacker, tick);
		}

		@Override
		public String toString() {
			return "SprintHit[attacker=" + attacker + ", tick=" + tick + "]";
		}
	}

	private static final class Placement {
		private final BedDefense.Cell cell;
		private final long tick;
		private final boolean thrown;

		Placement(BedDefense.Cell cell, long tick, boolean thrown) {
			this.cell = cell;
			this.tick = tick;
			this.thrown = thrown;
		}

		BedDefense.Cell cell() {
			return cell;
		}

		long tick() {
			return tick;
		}

		boolean thrown() {
			return thrown;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Placement)) return false;
			Placement other = (Placement) obj;
			return tick == other.tick && thrown == other.thrown && Objects.equals(cell, other.cell);
		}

		@Override
		public int hashCode() {
			return Objects.hash(cell, tick, thrown);
		}

		@Override
		public String toString() {
			return "Placement[cell=" + cell + ", tick=" + tick + ", thrown=" + thrown + "]";
		}
	}

	/** A placement with its candidate placer, waiting for its burst-and-look window to fill in. */
	private static final class PendingPlacement {
		private final String placer;
		private final BedDefense.Cell cell;
		private final long tick;

		PendingPlacement(String placer, BedDefense.Cell cell, long tick) {
			this.placer = placer;
			this.cell = cell;
			this.tick = tick;
		}

		String placer() {
			return placer;
		}

		BedDefense.Cell cell() {
			return cell;
		}

		long tick() {
			return tick;
		}
	}

	private static final class Blast {
		private final Vec centre;
		private final long tick;

		Blast(Vec centre, long tick) {
			this.centre = centre;
			this.tick = tick;
		}

		Vec centre() {
			return centre;
		}

		long tick() {
			return tick;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Blast)) return false;
			Blast other = (Blast) obj;
			return tick == other.tick && Objects.equals(centre, other.centre);
		}

		@Override
		public int hashCode() {
			return Objects.hash(centre, tick);
		}

		@Override
		public String toString() {
			return "Blast[centre=" + centre + ", tick=" + tick + "]";
		}
	}

	private final Map<String, Track> tracks = new HashMap<>();
	private final List<Swing> swings = new ArrayList<>();
	private final List<Hurt> hurts = new ArrayList<>();
	private final List<Attack> attacks = new ArrayList<>();
	private final List<Knockback> knockbacks = new ArrayList<>();
	private final List<SprintHit> sprintHits = new ArrayList<>();
	private final List<Placement> placements = new ArrayList<>();
	/** Every placement seen recently, whoever placed it or whether anybody could be blamed at all:
	 * kept only so a later placement can tell whether it was part of a burst. */
	private final List<Placement> placementHistory = new ArrayList<>();
	private final List<PendingPlacement> pendingPlacements = new ArrayList<>();
	private final Map<BedDefense.Cell, Long> recentBlocks = new HashMap<>();
	private final List<BedDefense.Cell> brokenBeds = new ArrayList<>();
	private final List<Blast> blasts = new ArrayList<>();
	/** ClickStats violations, found the instant a swing arrives rather than at the end of a tick;
	 * drained into the tick's sightings by {@link #endTick}. */
	private final List<Violation> autoDetected = new ArrayList<>();
	private String self = "";
	private long lastLag = Long.MIN_VALUE / 2;
	private Predicate<Check> enabled = check -> true;
	private Tuning tuning = Tuning.DEFAULT;

	/** The player running the client: a witness, never a suspect. */
	public void setSelf(String name) {
		self = name == null ? "" : name;
	}

	public void setTuning(Tuning limits) {
		tuning = limits == null ? Tuning.DEFAULT : limits;
	}

	/** Which checks may report; the others are skipped, sightings and reliefs alike. */
	public void setEnabled(Predicate<Check> which) {
		enabled = which == null ? check -> true : which;
	}

	public void frame(String player, Frame frame) {
		Track track = track(player);
		Frame last = track.frames.peekLast();
		if (last != null && last.tick() >= frame.tick()) {
			return;
		}
		if (last != null && frame.feet().distanceTo(last.feet()) > TELEPORT) {
			track.lastShove = frame.tick();
		}
		track.frames.addLast(frame);
		while (track.frames.size() > HISTORY) {
			track.frames.removeFirst();
		}
	}

	public void swing(String player, long tick) {
		swing(player, tick, tick * 50_000_000L);
	}

	/**
	 * A swing, with the moment it arrived on the network in nanoseconds — the only clock fine enough
	 * to catch a robot's sub-tick double-click. {@link #swing(String, long)} synthesises one tick of
	 * 50ms exactly, which is fine for every other check but never triggers ClickStats' own signals on
	 * its own (a perfectly even stream reads as human-fast, not as the sub-10ms/near-uniform patterns
	 * those signals look for) — callers that mean to feed ClickStats real timing (the game side, off
	 * {@code CheatSensor.arrived}) should call this overload with a real {@link System#nanoTime()}.
	 *
	 * <p>Derived from Iustitia (MIT), checks/combat/ClickStatisticsCheck.kt.
	 */
	public void swing(String player, long tick, long nanoTime) {
		Track track = track(player);
		track.swings.addLast(tick);
		while (track.swings.size() > 40) {
			track.swings.removeFirst();
		}
		swings.add(new Swing(player, tick));
		evaluateClickStats(player, track, tick, nanoTime);
	}

	// --- autoclicker: swings too fast, too even or too smooth to be a hand -----------------------------

	private void evaluateClickStats(String player, Track track, long tick, long nanoTime) {
		pushCap(track.swingTicksCps, tick, 80);

		boolean sameTickBatch = tick == track.lastSwingTick;
		long tickDelta = tick - track.lastSwingTick;
		// A digging player's swings are the server's own relay, not theirs (see DIG_RELAY_MIN_TICK_DELTA);
		// this mod has no signal for "is digging" (no block-breaking packet is watched), so the carve-out
		// Iustitia applies there cannot be reproduced — a known, documented gap, not silently dropped.
		if (!sameTickBatch) {
			if (track.lastSwingTick > Long.MIN_VALUE / 2 + 1 && tickDelta > 0) {
				pushCap(track.clickTickIntervals, (int) Math.min(Integer.MAX_VALUE, tickDelta), CLICK_KURTOSIS_WINDOW);
			}
			long nanoDelta = nanoTime - track.lastSwingNano;
			if (nanoDelta > 0) {
				pushCap(track.clickNanoIntervals, nanoDelta, 500);
			}
			track.lastSwingTick = tick;
			track.lastSwingNano = nanoTime;
		}

		if (player.equals(self) || !enabled.test(Check.AUTOCLICKER)) {
			return;
		}

		long cps = track.swingTicksCps.stream().filter(t -> t >= tick - CLICK_CPS_WINDOW).count();
		if (cps > CLICK_CPS_CAP) {
			add(autoDetected, player, Check.AUTOCLICKER, cps + " swings/s", tick);
		}

		Iterator<Long> nanoIter = track.clickNanoIntervals.iterator();
		if (nanoIter.hasNext()) {
			long a = nanoIter.next();
			if (nanoIter.hasNext()) {
				long b = nanoIter.next();
				if (a < CLICK_ROBOT_NANOS && b < CLICK_ROBOT_PRIOR_NANOS) {
					add(autoDetected, player, Check.AUTOCLICKER,
							String.format(Locale.ROOT, "%.1fms double-click", a / 1_000_000.0), tick);
				}
			}
		}

		if (track.clickTickIntervals.size() >= CLICK_STDEV_WINDOW) {
			double stdev = populationStDev(newestInts(track.clickTickIntervals, CLICK_STDEV_WINDOW));
			if (stdev < CLICK_STDEV_MIN) {
				add(autoDetected, player, Check.AUTOCLICKER,
						String.format(Locale.ROOT, "click intervals too uniform: stDev %.2f", stdev), tick);
			}
		}
		if (track.clickTickIntervals.size() >= CLICK_KURTOSIS_WINDOW) {
			double kurtosis = excessKurtosis(newestInts(track.clickTickIntervals, CLICK_KURTOSIS_WINDOW));
			if (kurtosis < CLICK_KURTOSIS_MIN) {
				add(autoDetected, player, Check.AUTOCLICKER,
						String.format(Locale.ROOT, "near-uniform clicks: excess kurtosis %.2f", kurtosis), tick);
			}
		}
	}

	private static <T> void pushCap(ArrayDeque<T> deque, T value, int cap) {
		deque.addFirst(value);
		while (deque.size() > cap) {
			deque.removeLast();
		}
	}

	private static double[] newestInts(ArrayDeque<Integer> deque, int n) {
		double[] values = new double[n];
		int i = 0;
		for (int value : deque) {
			if (i >= n) {
				break;
			}
			values[i++] = value;
		}
		return values;
	}

	/** Population standard deviation (divides by n, not n-1: the sample IS the whole window judged). */
	private static double populationStDev(double[] values) {
		double mean = 0;
		for (double v : values) {
			mean += v;
		}
		mean /= values.length;
		double variance = 0;
		for (double v : values) {
			variance += (v - mean) * (v - mean);
		}
		variance /= values.length;
		return Math.sqrt(variance);
	}

	/** Excess kurtosis (Fisher's definition, population moments): 0 for a normal distribution, -1.2
	 * for a uniform one. A fixed-delay autoclicker's intervals cluster near-uniform. */
	private static double excessKurtosis(double[] values) {
		double mean = 0;
		for (double v : values) {
			mean += v;
		}
		mean /= values.length;
		double m2 = 0;
		double m4 = 0;
		for (double v : values) {
			double d = v - mean;
			double d2 = d * d;
			m2 += d2;
			m4 += d2 * d2;
		}
		m2 /= values.length;
		m4 /= values.length;
		if (m2 < 1e-12) {
			// Every interval identical: as uniform as a distribution gets, well past the strict bar.
			return CLICK_KURTOSIS_MIN - 1;
		}
		return m4 / (m2 * m2) - 3;
	}

	/**
	 * Somebody got hurt.
	 *
	 * @param cause the attacker, where the server named them; else {@code null}
	 */
	public void hurt(String victim, long tick, Hit hit, String cause) {
		Track track = track(victim);
		long prev = track.lastHurt;
		track.lastShove = tick;
		track.lastHurt = tick;
		hurts.add(new Hurt(victim, tick, hit, cause, prev));
	}

	/**
	 * A melee attack known for certain: the attacker's swing and the victim's push, hurt or crit
	 * arrived together, the way a server sends one hit. Names the attacker even in a crowd, and
	 * counts as the hurt when no hurt came.
	 */
	public void attack(String attacker, String victim, long tick) {
		attacks.add(new Attack(attacker, victim, tick));
	}

	/** The server set somebody's velocity: knockback, a blast, a launch pad. */
	public void motion(String player, long tick, Vec velocity) {
		Track track = track(player);
		track.motion = velocity;
		track.motionTick = tick;
	}

	public void explosion(Vec centre, long tick) {
		blasts.add(new Blast(centre, tick));
		for (Track track : tracks.values()) {
			Frame last = track.frames.peekLast();
			if (last != null && last.feet().distanceTo(centre) <= BLAST_RADIUS) {
				track.lastShove = tick;
			}
		}
	}

	/** The server fell behind or caught up: for a moment nobody's movement or hits are their own. */
	public void serverLag(long tick) {
		lastLag = tick;
	}

	/**
	 * A block appeared where there was none.
	 *
	 * @param thrown whether a thrown thing was right there — a bridge egg lays blocks nobody placed
	 */
	public void placed(BedDefense.Cell cell, long tick, boolean thrown) {
		placements.add(new Placement(cell, tick, thrown));
		recentBlocks.put(cell, tick);
	}

	/** Half a bed vanished. */
	public void bedBroken(BedDefense.Cell cell, long tick) {
		brokenBeds.add(cell);
	}

	/** A new round: nobody is known any more. */
	public void clear() {
		tracks.clear();
		swings.clear();
		hurts.clear();
		attacks.clear();
		knockbacks.clear();
		sprintHits.clear();
		placements.clear();
		placementHistory.clear();
		pendingPlacements.clear();
		recentBlocks.clear();
		brokenBeds.clear();
		blasts.clear();
		lastLag = Long.MIN_VALUE / 2;
	}

	/** Judges what can be judged now; call after this tick's frames. */
	public List<Violation> endTick(long tick, Terrain terrain) {
		List<Violation> found = new ArrayList<>();
		boolean lagging = tick - lastLag <= LAG_WINDOW;

		found.addAll(autoDetected);
		autoDetected.clear();
		if (lagging) {
			// A server hitch is not anybody's clicking; reset the streak instead of judging catch-up
			// swings against it.
			for (Track track : tracks.values()) {
				track.stillStreak = 0;
			}
		}

		judgeSwings(tick, found);
		mergeAttacks(found);
		if (lagging) {
			hurts.clear();
			knockbacks.removeIf(hit -> hit.tick() + KNOCKBACK_TICKS <= tick);
			sprintHits.removeIf(hit -> hit.tick() + 1 <= tick);
		} else {
			judgeHurts(terrain, found);
			judgeKnockbacks(tick, terrain, found);
			judgeSprintHits(tick, found);
		}
		judgePlacements(tick, found);
		judgeBeds(tick, terrain, found);
		if (!lagging) {
			for (Map.Entry<String, Track> entry : tracks.entrySet()) {
				if (!entry.getKey().equals(self)) {
					judgeMovement(entry.getKey(), entry.getValue(), tick, found);
				}
			}
		}

		blasts.removeIf(blast -> blast.tick() < tick - 100);
		recentBlocks.values().removeIf(placed -> placed < tick - 40);
		tracks.values().removeIf(track -> track.frames.isEmpty() ? track.lastHurt < tick - 100
				: track.frames.peekLast().tick() < tick - HISTORY);
		return found;
	}

	private Track track(String player) {
		return tracks.computeIfAbsent(player, name -> new Track());
	}

	private void add(List<Violation> found, String player, Check check, String detail, long tick) {
		if (player.equals(self) || !enabled.test(check)) {
			return;
		}
		Track track = track(player);
		Long last = track.lastSeen.get(check);
		if (last != null && check.cooldownTicks() > 0 && tick - last < check.cooldownTicks()) {
			return;
		}
		track.lastSeen.put(check, tick);
		found.add(new Violation(player, check, detail, tick));
	}

	private void relieve(List<Violation> found, String player, Check check, long tick) {
		if (!player.equals(self) && enabled.test(check)) {
			found.add(Violation.relief(player, check, tick));
		}
	}

	// --- autoblock: a swing in the middle of blocking -------------------------------------------------

	private void judgeSwings(long tick, List<Violation> found) {
		Iterator<Swing> pending = swings.iterator();
		while (pending.hasNext()) {
			Swing swing = pending.next();
			if (swing.tick() + 1 > tick) {
				continue;
			}
			pending.remove();

			Track track = tracks.get(swing.player());
			List<Frame> around = track == null ? null : track.run(swing.tick() - 3, swing.tick() + 1);
			// A sword only: eating or drawing a bow shows its swings when they hit something, below.
			if (around == null || around.stream().anyMatch(f -> f.held() != Frame.Held.SWORD)) {
				continue;
			}
			if (around.stream().allMatch(Frame::usingItem)) {
				add(found, swing.player(), Check.AUTOBLOCK, "swung while blocking", swing.tick());
			} else if (around.stream().noneMatch(Frame::usingItem)) {
				relieve(found, swing.player(), Check.AUTOBLOCK, swing.tick());
			}
		}
	}

	// --- reach, killaura, multi-aura: who hit whom, from where ---------------------------------------------

	/**
	 * Certain attacks name the attacker of their hurt, or stand in for it where none came; an
	 * attacker with two victims in one tick is a multi-aura.
	 */
	private void mergeAttacks(List<Violation> found) {
		Map<String, Set<String>> victimsOf = new HashMap<>();
		for (Attack attack : attacks) {
			if (attack.attacker().equals(attack.victim())) {
				continue;
			}
			victimsOf.computeIfAbsent(attack.attacker() + "\n" + attack.tick(), key -> new HashSet<>()).add(attack.victim());

			boolean named = false;
			for (int i = 0; i < hurts.size(); i++) {
				Hurt hurt = hurts.get(i);
				if (hurt.victim().equals(attack.victim()) && hurt.tick() >= attack.tick() - 1 && hurt.tick() <= attack.tick() + 1) {
					hurts.set(i, new Hurt(hurt.victim(), hurt.tick(), Hit.MELEE, attack.attacker(), hurt.prevHurt()));
					named = true;
				}
			}
			if (!named) {
				hurt(attack.victim(), attack.tick(), Hit.MELEE, attack.attacker());
			}
		}
		for (Attack attack : attacks) {
			Set<String> victims = victimsOf.remove(attack.attacker() + "\n" + attack.tick());
			if (victims == null) {
				continue;
			}
			Track track = track(attack.attacker());
			if (victims.size() >= 2) {
				add(found, attack.attacker(), Check.MULTIAURA, "hit " + victims.size() + " players in one tick", attack.tick());
			} else if (track.recentVictimsTick == attack.tick() - 1) {
				// Lag-absorb (Iustitia's MultiTargetCheck): a multi-aura spread across two ticks — e.g.
				// two victims last tick and one now — sums to a multi-aura even when no single tick
				// reached two on its own. Only where the same-tick flag above did not already cover it.
				Set<String> union = new HashSet<>(victims);
				union.addAll(track.recentVictims);
				if (union.size() >= MULTIAURA_WINDOW_VICTIMS) {
					add(found, attack.attacker(), Check.MULTIAURA,
							"hit " + union.size() + " players across two ticks", attack.tick());
				}
			}
			track.recentVictims = victims;
			track.recentVictimsTick = attack.tick();
		}
		attacks.clear();
	}

	private void judgeHurts(Terrain terrain, List<Violation> found) {
		for (Hurt hurt : hurts) {
			if (hurt.hit() != Hit.OTHER) {
				judgeHit(hurt, terrain, found);
			}
		}
		hurts.clear();
	}

	private void judgeHit(Hurt hurt, Terrain terrain, List<Violation> found) {
		Track victim = tracks.get(hurt.victim());
		if (victim == null) {
			return;
		}
		boolean unsure = hurt.hit() == Hit.UNKNOWN && hurt.cause() == null;
		if (unsure && fellJustNow(victim, hurt.tick())) {
			return;
		}

		String attacker = hurt.cause() != null && tracks.containsKey(hurt.cause()) ? hurt.cause() : null;
		if (attacker == null) {
			List<String> candidates = new ArrayList<>();
			for (Map.Entry<String, Track> entry : tracks.entrySet()) {
				String name = entry.getKey();
				if (name.equals(hurt.victim()) || !entry.getValue().swungWithin(hurt.tick() - 2, hurt.tick())) {
					continue;
				}
				double distance = reachDistance(entry.getValue(), victim, hurt.tick());
				if (distance > ATTACK_RANGE) {
					continue;
				}
				// Without the server saying it was a punch, only somebody facing the victim fits.
				if (unsure && !name.equals(self) && offAngle(entry.getValue(), victim, hurt.tick()) > UNSURE_ANGLE) {
					continue;
				}
				if (name.equals(self) && distance <= tuning.reachMoving() + 0.7) {
					// The player's own hit explains it; their knockback on the victim is still worth watching.
					knockbacks.add(new Knockback(hurt.victim(), self, hurt.tick(), hurt.prevHurt()));
					return;
				}
				if (!name.equals(self)) {
					candidates.add(name);
				}
			}
			if (candidates.size() != 1) {
				return;
			}
			attacker = candidates.get(0);
		}
		if (attacker.equals(self)) {
			knockbacks.add(new Knockback(hurt.victim(), self, hurt.tick(), hurt.prevHurt()));
			return;
		}

		Track track = tracks.get(attacker);
		track.lastAttackTick = hurt.tick();
		judgeTriggerbotHit(attacker, track, hurt.victim(), hurt.tick(), found);
		judgeCriticals(attacker, track, hurt.tick(), found);
		Frame attackerNow = track.at(hurt.tick());
		Frame victimNow = victim.at(hurt.tick());
		if (attackerNow != null && victimNow != null) {
			judgeHitFlickHit(attacker, track, hurt.victim(), attackerNow, victimNow, hurt.tick(), found);
		}
		double distance = reachDistance(track, victim, hurt.tick());
		double limit = track.still(hurt.tick()) && victim.still(hurt.tick()) ? tuning.reachStanding() : tuning.reachMoving();
		if (distance > limit && distance <= ATTACK_RANGE) {
			add(found, attacker, Check.REACH, String.format(Locale.ROOT, "%.1f blocks", distance), hurt.tick());
		} else if (distance <= REACH_FAIR) {
			relieve(found, attacker, Check.REACH, hurt.tick());
		}

		double angle = offAngle(track, victim, hurt.tick());
		if (angle > AURA_ANGLE) {
			add(found, attacker, Check.KILLAURA, String.format(Locale.ROOT, "hit %.0f° outside their view", angle),
					hurt.tick());
		}
		List<Frame> eating = track.run(hurt.tick() - 6, hurt.tick());
		if (eating != null && eating.stream().allMatch(f -> f.usingItem() && f.held() != Frame.Held.SWORD)) {
			add(found, attacker, Check.KILLAURA, "hit while using an item", hurt.tick());
		}
		if (throughWall(track, victim, hurt.tick(), terrain)) {
			add(found, attacker, Check.KILLAURA, "hit through a wall", hurt.tick());
		}

		knockbacks.add(new Knockback(hurt.victim(), attacker, hurt.tick(), hurt.prevHurt()));
		if (hurt.cause() != null) {
			sprintHits.add(new SprintHit(attacker, hurt.tick()));
		}
	}

	/** Came down three blocks or more in the last three quarters of a second: fall damage. */
	private static boolean fellJustNow(Track victim, long tick) {
		Frame now = victim.at(tick);
		if (now == null) {
			now = victim.frames.peekLast();
		}
		if (now == null) {
			return false;
		}
		for (Frame frame : victim.frames) {
			if (frame.tick() >= tick - 15 && frame.feet().y() - now.feet().y() >= 3.0) {
				return true;
			}
		}
		return false;
	}

	/** Eyes to hitbox, at the most favourable of the aligned ticks up to the hit. */
	private static double reachDistance(Track attacker, Track victim, long tick) {
		double best = Double.MAX_VALUE;
		for (int back = 0; back <= LAG_TICKS; back++) {
			Frame a = attacker.at(tick - back);
			Frame v = victim.at(tick - back);
			if (a != null && v != null) {
				best = Math.min(best, v.box().inflate(HITBOX_BORDER).distanceTo(a.eye()));
			}
		}
		return best;
	}

	/** How far outside the attacker's look the victim was, past the edge of their hitbox, at best. */
	private static double offAngle(Track attacker, Track victim, long tick) {
		double best = 0;
		boolean any = false;
		for (int back = 0; back <= LAG_TICKS; back++) {
			Frame a = attacker.at(tick - back);
			Frame v = victim.at(tick - back);
			if (a == null || v == null) {
				continue;
			}
			Vec towards = v.centre().subtract(a.eye());
			double distance = towards.length();
			if (distance < 1e-6) {
				return 0;
			}
			double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, towards.scale(1 / distance).dot(a.look())))));
			double edge = Math.toDegrees(Math.atan2(0.5, distance));
			double off = Math.max(0, angle - edge);
			best = any ? Math.min(best, off) : off;
			any = true;
		}
		return best;
	}

	/**
	 * Whether this hit, with the attacker's recent ones, makes a pattern of hits through a wall: at
	 * least half of the last few, with no line from the eyes to any of the victim's sample points at
	 * ANY of the aligned ticks back — positions lag, so the most favourable of them is what counts,
	 * the same as {@link #reachDistance} and {@link #offAngle}. One such hit is a corner or a lag
	 * spike; a pattern is an aura that does not care about walls.
	 */
	private boolean throughWall(Track attacker, Track victim, long tick, Terrain terrain) {
		Boolean occludedEveryAlignedTick = null;
		for (int back = 0; back <= LAG_TICKS; back++) {
			Frame a = attacker.at(tick - back);
			Frame v = victim.at(tick - back);
			if (a == null || v == null) {
				continue;
			}
			if (!allSamplesBlocked(terrain, a.eye(), v)) {
				// A clear line at even one aligned tick is the most favourable reading: not occluded.
				occludedEveryAlignedTick = false;
				break;
			}
			occludedEveryAlignedTick = true;
		}
		boolean occluded = Boolean.TRUE.equals(occludedEveryAlignedTick);

		attacker.wallHits.addLast(occluded);
		while (attacker.wallHits.size() > WALL_WINDOW) {
			attacker.wallHits.removeFirst();
		}
		long through = attacker.wallHits.stream().filter(hit -> hit).count();
		return occluded && attacker.wallHits.size() >= WALL_MIN && through * 2 >= attacker.wallHits.size();
	}

	/**
	 * Whether every one of the victim's sample points — eye, centre, feet, and the eight corners of
	 * their hitbox — is blocked from the attacker's eye. A clear line to any single one says the
	 * attacker could have seen them there, whatever the rest says: a hand or a foot poking past an
	 * edge is still a view of the player.
	 */
	private boolean allSamplesBlocked(Terrain terrain, Vec eye, Frame victim) {
		for (Vec target : wallSamplePoints(victim)) {
			if (!blockedBetween(terrain, eye, target)) {
				return false;
			}
		}
		return true;
	}

	/** Eye, centre, feet, and the victim's hitbox corners, inflated by {@link #HITBOX_BORDER} and
	 * pulled back in by a hair so a ray does not end exactly on a block face. */
	private static List<Vec> wallSamplePoints(Frame victim) {
		List<Vec> points = new ArrayList<>(11);
		points.add(victim.eye());
		points.add(victim.centre());
		points.add(victim.feet().add(new Vec(0, 0.2, 0)));
		Box box = victim.box().inflate(HITBOX_BORDER);
		double pull = 0.05;
		for (double x : new double[] {box.minX() + pull, box.maxX() - pull}) {
			for (double y : new double[] {box.minY() + pull, box.maxY() - pull}) {
				for (double z : new double[] {box.minZ() + pull, box.maxZ() - pull}) {
					points.add(new Vec(x, y, z));
				}
			}
		}
		return points;
	}

	/** A solid block — not one placed just now, which may postdate the hit — on the line between two points. */
	private boolean blockedBetween(Terrain terrain, Vec from, Vec to) {
		double length = from.distanceTo(to);
		int steps = Math.max(1, (int) Math.ceil(length / 0.1));
		BedDefense.Cell start = cell(from);
		BedDefense.Cell end = cell(to);
		for (int i = 1; i < steps; i++) {
			Vec at = from.add(to.subtract(from).scale((double) i / steps));
			BedDefense.Cell cell = cell(at);
			if (cell.equals(start) || cell.equals(end) || recentBlocks.containsKey(cell)) {
				continue;
			}
			if (terrain.solidAt(at.x(), at.y(), at.z())) {
				return true;
			}
		}
		return false;
	}

	// --- keepsprint: no slowdown from a sprint-hit -------------------------------------------------------

	private void judgeSprintHits(long tick, List<Violation> found) {
		Iterator<SprintHit> pending = sprintHits.iterator();
		while (pending.hasNext()) {
			SprintHit hit = pending.next();
			if (hit.tick() + 1 > tick) {
				continue;
			}
			pending.remove();

			Track track = tracks.get(hit.attacker());
			List<Frame> around = track == null ? null : track.run(hit.tick() - 1, hit.tick() + 1);
			if (around == null) {
				continue;
			}
			Frame before = around.get(0);
			double pre = around.get(1).horizontalFrom(before);
			double post = around.get(2).horizontalFrom(around.get(1));
			// Only a sprint-hit on the ground slows the attacker; anything else says nothing.
			if (!before.sprinting() || !before.supported() || pre < 0.2 || before.assisted() || before.riding()) {
				continue;
			}
			boolean fullSpeed = post >= 0.9 * pre;
			track.fullSpeedHits.addLast(fullSpeed);
			while (track.fullSpeedHits.size() > KEEPSPRINT_WINDOW) {
				track.fullSpeedHits.removeFirst();
			}
			long kept = track.fullSpeedHits.stream().filter(kept1 -> kept1).count();
			if (fullSpeed && kept >= KEEPSPRINT_MIN) {
				add(found, hit.attacker(), Check.KEEPSPRINT,
						String.format(Locale.ROOT, "kept %.0f%% of their speed through a sprint-hit", 100 * post / pre), hit.tick());
			} else if (!fullSpeed) {
				relieve(found, hit.attacker(), Check.KEEPSPRINT, hit.tick());
			}
		}
	}

	// --- anti-knockback: hit, and nothing happened -----------------------------------------------------

	private void judgeKnockbacks(long tick, Terrain terrain, List<Violation> found) {
		Iterator<Knockback> pending = knockbacks.iterator();
		while (pending.hasNext()) {
			Knockback hit = pending.next();
			if (hit.tick() + KNOCKBACK_TICKS > tick) {
				continue;
			}
			pending.remove();
			if (hit.victim().equals(self) || hit.tick() - hit.prevHurt() < INVULNERABLE_TICKS) {
				// A hit inside the last one's invulnerability pushes less: nothing to hold them to.
				continue;
			}

			Boolean took = tookKnockback(hit, terrain);
			if (took == null) {
				continue;
			}
			if (took) {
				relieve(found, hit.victim(), Check.VELOCITY, hit.tick());
			} else {
				add(found, hit.victim(), Check.VELOCITY, "barely moved from a hit", hit.tick());
			}
		}
	}

	/** Whether the victim took the hit's knockback; {@code null} when there is no telling. */
	private Boolean tookKnockback(Knockback hit, Terrain terrain) {
		Track victim = tracks.get(hit.victim());
		Track attacker = tracks.get(hit.attacker());
		if (victim == null || attacker == null) {
			return null;
		}
		List<Frame> after = victim.run(hit.tick() - 1, hit.tick() + KNOCKBACK_TICKS);
		Frame from = attacker.at(hit.tick() - 1);
		if (after == null || from == null) {
			return null;
		}
		Frame before = after.get(0);
		if (!before.supported() || before.assisted() || before.riding()
				|| victim.lastHurt > hit.tick() || shovedBetween(victim, hit)) {
			return null;
		}

		double expected = DEFAULT_KNOCKBACK;
		if (victim.motion != null && victim.motionTick >= hit.tick() && victim.motionTick <= hit.tick() + 1) {
			expected = Math.hypot(victim.motion.x(), victim.motion.z());
			if (expected < 0.15) {
				// The server itself gave no real push: nothing to hold the victim to.
				return null;
			}
		}

		Vec away = new Vec(before.feet().x() - from.feet().x(), 0, before.feet().z() - from.feet().z());
		if (away.length() < 1e-3) {
			return null;
		}
		away = away.normalize();

		boolean wall = blocked(terrain, before.feet(), away, before.box().maxY() - before.box().minY());
		boolean ceiling = ceilingAbove(terrain, before);
		if (wall && ceiling) {
			return null;
		}

		double along = after.get(after.size() - 1).feet().subtract(before.feet()).dot(away);
		double rise = after.stream().mapToDouble(f -> f.feet().y() - before.feet().y()).max().orElse(0);
		// A 1.8 hit carries a victim about 4.8 times the push over six ticks; a quarter of that, at
		// most half a block, is what walking into the hit and jumping it can take off.
		boolean stayed = wall || along < Math.min(0.45, expected * 4.8 * 0.25);
		boolean grounded = ceiling || rise < 0.15;
		if (stayed && grounded) {
			return false;
		}
		return !wall && along >= expected * 4.8 * 0.5 || !ceiling && rise >= 0.3 ? Boolean.TRUE : null;
	}

	private boolean shovedBetween(Track victim, Knockback hit) {
		for (Blast blast : blasts) {
			if (blast.tick() >= hit.tick() - 1 && blast.tick() <= hit.tick() + KNOCKBACK_TICKS) {
				Frame at = victim.at(hit.tick());
				if (at != null && at.feet().distanceTo(blast.centre()) <= BLAST_RADIUS) {
					return true;
				}
			}
		}
		return false;
	}

	/** A block within a block of the victim in the direction they would be pushed. */
	private static boolean blocked(Terrain terrain, Vec feet, Vec away, double height) {
		for (double reach = 0.4; reach <= 1.0; reach += 0.3) {
			for (double up = 0.1; up < height; up += 0.8) {
				Vec at = feet.add(away.scale(reach)).add(new Vec(0, up, 0));
				if (terrain.solid(floor(at.x()), floor(at.y()), floor(at.z()))) {
					return true;
				}
			}
		}
		return false;
	}

	/** A block less than a jump above the victim's head. */
	private static boolean ceilingAbove(Terrain terrain, Frame frame) {
		int y = floor(frame.box().maxY() + 0.5);
		Box box = frame.box();
		for (double x : new double[] {box.minX(), box.maxX()}) {
			for (double z : new double[] {box.minZ(), box.maxZ()}) {
				if (terrain.solid(floor(x), y, floor(z))) {
					return true;
				}
			}
		}
		return false;
	}

	// --- scaffold, fastplace: blocks nobody looked at, or too many --------------------------------------

	private void judgePlacements(long tick, List<Violation> found) {
		List<Placement> now = new ArrayList<>();
		Iterator<Placement> pending = placements.iterator();
		while (pending.hasNext()) {
			Placement placement = pending.next();
			if (placement.tick() <= tick) {
				now.add(placement);
				pending.remove();
			}
		}

		for (Placement placement : now) {
			placementHistory.add(placement);
			if (placement.thrown() || partOfStructure(placement, now)) {
				continue;
			}
			Vec centre = centre(placement.cell());

			String placer = null;
			int placers = 0;
			boolean mine = false;
			for (Map.Entry<String, Track> entry : tracks.entrySet()) {
				Frame at = entry.getValue().at(placement.tick());
				if (at == null || at.eye().distanceTo(centre) > PLACE_REACH
						|| !entry.getValue().swungWithin(placement.tick() - 2, placement.tick())) {
					continue;
				}
				if (entry.getKey().equals(self)) {
					mine = true;
				} else {
					placer = entry.getKey();
					placers++;
				}
			}
			if (mine || placers != 1) {
				continue;
			}

			pendingPlacements.add(new PendingPlacement(placer, placement.cell(), placement.tick()));
		}
		placementHistory.removeIf(p -> p.tick() < tick - STRUCTURE_WINDOW * 2);

		// Judged only once the burst window around each placement, and the look window past it, are
		// both fully known — a pop-up tower needs its later blocks to tell it apart from a hand.
		Iterator<PendingPlacement> deferred = pendingPlacements.iterator();
		while (deferred.hasNext()) {
			PendingPlacement pendingPlacement = deferred.next();
			if (pendingPlacement.tick() + STRUCTURE_WINDOW > tick) {
				continue;
			}
			deferred.remove();
			judgeConfirmedPlacement(pendingPlacement, found);
		}
	}

	private void judgeConfirmedPlacement(PendingPlacement placement, List<Violation> found) {
		if (isStructure(placement, placementHistory)) {
			// A machine-built structure, not a hand: nobody is blamed for any block of it.
			return;
		}
		Track track = tracks.get(placement.placer());
		if (track == null) {
			return;
		}

		track.placements.addLast(placement.tick());
		while (!track.placements.isEmpty() && track.placements.peekFirst() <= placement.tick() - 20) {
			track.placements.removeFirst();
		}
		if (track.placements.size() > tuning.fastPlaceLimit()) {
			add(found, placement.placer(), Check.FASTPLACE, track.placements.size() + " blocks a second", placement.tick());
		}

		if (!lookedAt(track, placement.cell(), placement.tick())) {
			add(found, placement.placer(), Check.SCAFFOLD, "placed a block they were not looking at", placement.tick());
		}
	}

	private static boolean partOfStructure(Placement placement, List<Placement> sameTick) {
		int close = 0;
		for (Placement other : sameTick) {
			if (other.tick() == placement.tick() && centre(other.cell()).distanceTo(centre(placement.cell())) <= 4.0) {
				close++;
			}
		}
		return close >= STRUCTURE_BLOCKS;
	}

	/**
	 * Whether this placement, with the others seen recently nearby, makes a burst that goes up faster
	 * than a hand can click — a pop-up tower's ring or a bridge egg's line, spread over several ticks
	 * rather than landing all in one. {@link #partOfStructure} already catches the same-tick case
	 * quickly; this catches the same pattern stretched across the window.
	 */
	private static boolean isStructure(PendingPlacement placement, List<Placement> history) {
		Vec centre = centre(placement.cell());
		List<Placement> cluster = new ArrayList<>();
		for (Placement other : history) {
			if (Math.abs(other.tick() - placement.tick()) <= STRUCTURE_WINDOW
					&& centre(other.cell()).distanceTo(centre) <= STRUCTURE_RADIUS) {
				cluster.add(other);
			}
		}
		long sameTickCells = cluster.stream().filter(p -> p.tick() == placement.tick())
				.map(Placement::cell).distinct().count();
		if (sameTickCells >= STRUCTURE_SAME_TICK_CELLS) {
			return true;
		}
		// A sliding window exactly STRUCTURE_BURST_TICKS wide, not centred either side of an anchor —
		// that would double it — so a hand's one-block-every-couple-of-ticks bridge (which still puts
		// several blocks within a wider, symmetric window) does not read as a burst.
		for (Placement start : cluster) {
			long burstCells = cluster.stream()
					.filter(p -> p.tick() >= start.tick() && p.tick() < start.tick() + STRUCTURE_BURST_TICKS)
					.map(Placement::cell).distinct().count();
			if (burstCells >= STRUCTURE_BURST_CELLS) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the placer's look passed by the new block at any frame from three ticks before the
	 * placement to two after: a remote player's rotation arrives later, and coarser, than the block
	 * update that placed it, so the look is allowed to catch up. The cell is inflated by half a block,
	 * wider than the hitbox border elsewhere, to cover rotation quantisation as well as lag.
	 */
	private static boolean lookedAt(Track track, BedDefense.Cell cell, long placementTick) {
		Box box = new Box(cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + 1, cell.z() + 1).inflate(0.5);
		for (long t = placementTick - 3; t <= placementTick + 2; t++) {
			Frame at = track.at(t);
			if (at != null && box.entry(at.eye(), at.eye().add(at.look().scale(PLACE_REACH + 1))) >= 0) {
				return true;
			}
		}
		return false;
	}

	// --- bed nuker: a bed gone while nobody could reach it ------------------------------------------------

	private void judgeBeds(long tick, Terrain terrain, List<Violation> found) {
		while (!brokenBeds.isEmpty()) {
			List<BedDefense.Cell> bed = new ArrayList<>();
			bed.add(brokenBeds.remove(0));
			brokenBeds.removeIf(cell -> {
				boolean half = cell.x() == bed.get(0).x() && cell.y() == bed.get(0).y()
						? Math.abs(cell.z() - bed.get(0).z()) == 1
						: cell.z() == bed.get(0).z() && cell.y() == bed.get(0).y() && Math.abs(cell.x() - bed.get(0).x()) == 1;
				if (half) {
					bed.add(cell);
				}
				return half;
			});
			judgeBed(bed, tick, terrain, found);
		}
	}

	private void judgeBed(List<BedDefense.Cell> bed, long tick, Terrain terrain, List<Violation> found) {
		Vec centre = centre(bed.get(0));
		for (Blast blast : blasts) {
			if (blast.tick() >= tick - 60 && blast.centre().distanceTo(centre) <= BLAST_RADIUS) {
				return;
			}
		}

		BedDefense.Block solid = new BedDefense.Block("solid", 1);
		BedDefense.Report report = BedDefense.analyse(bed, at -> terrain.solid(at.x(), at.y(), at.z()) ? solid : null);
		if (report.open()) {
			return;
		}

		String breaker = null;
		int near = 0;
		for (Map.Entry<String, Track> entry : tracks.entrySet()) {
			Frame at = entry.getValue().at(tick);
			if (at == null) {
				at = entry.getValue().frames.peekLast();
			}
			if (at == null || at.eye().distanceTo(centre) > BED_REACH) {
				continue;
			}
			if (entry.getKey().equals(self)) {
				return;
			}
			breaker = entry.getKey();
			near++;
		}
		if (near == 1) {
			add(found, breaker, Check.NUKER, "broke a bed through its defence", tick);
		}
	}

	// --- speed, fly, noslow, sprint, backwards bridging: movement over the last ticks -----------------------

	private void judgeMovement(String player, Track track, long tick, List<Violation> found) {
		judgeAimSnap(player, track, tick, found);
		judgeAimTrack(player, track, tick, found);
		judgeTriggerbotTick(track, tick);
		judgeHitFlickTick(player, track, tick, found);
		judgeNoFall(player, track, tick, found);
		judgeStep(player, track, tick, found);
		judgeBlink(player, track, tick, found);

		boolean calm = track.lastShove < tick - 40;

		List<Frame> using = track.run(tick - 9, tick);
		if (using != null && track.lastShove < tick - 20 && using.stream().allMatch(Frame::usingItem)
				&& using.stream().noneMatch(f -> f.assisted() || f.riding())) {
			double speed = using.get(using.size() - 1).horizontalFrom(using.get(using.size() - 6)) / 5;
			List<Frame> meal = track.run(tick - SPRINT_USE_TICKS + 1, tick);
			if (speed > 0.18) {
				add(found, player, Check.NOSLOW,
						String.format(Locale.ROOT, "%.1f blocks/s while using an item", speed * 20), tick);
			} else if (meal != null && meal.stream().allMatch(f -> f.usingItem() && f.sprinting())) {
				// The flag may lag the first bites; a whole meal at a sprint is no lag.
				add(found, player, Check.NOSLOW, "sprinted while using an item", tick);
			} else if (speed < 0.1 && using.stream().noneMatch(Frame::sprinting) && tick % 10 == 0) {
				relieve(found, player, Check.NOSLOW, tick);
			}
		}

		List<Frame> sneak = track.run(tick - 3, tick);
		if (sneak != null && track.lastHurt < tick - 10 && sneak.stream().allMatch(f -> f.sprinting() && f.sneaking())) {
			add(found, player, Check.SPRINT, "sprinted while sneaking", tick);
		}

		List<Frame> sprint = track.run(tick - 10, tick);
		if (sprint != null && track.lastShove < tick - 20 && omniSprint(sprint)) {
			add(found, player, Check.SPRINT, "sprinted backwards", tick);
		}

		List<Frame> bridge = track.run(tick - 4, tick);
		if (bridge != null && track.lastShove < tick - 10 && tick - track.lastBridgeFlag >= 20
				&& track.swungWithin(tick - 3, tick)) {
			String detail = backwardsBridge(bridge, tuning.bridgeSpeed());
			if (detail != null) {
				track.lastBridgeFlag = tick;
				add(found, player, Check.SCAFFOLD, detail, tick);
			}
		}

		List<Frame> second = track.run(tick - 20, tick);
		if (second != null && calm && second.stream().noneMatch(f -> f.assisted() || f.riding())) {
			double travelled = 0;
			for (int i = 1; i < second.size(); i++) {
				travelled += second.get(i).horizontalFrom(second.get(i - 1));
			}
			double speed = travelled / 20;
			if (speed > tuning.speedLimit()) {
				add(found, player, Check.SPEED, String.format(Locale.ROOT, "%.1f blocks/s", speed * 20), tick);
			}
		}

		List<Frame> air = track.run(tick - 30, tick);
		if (air != null && track.lastShove < tick - 60 && hovering(air)) {
			add(found, player, Check.FLY, "moved in mid-air without falling", tick);
		}
	}

	// --- aimsnap: a rotation snap out of a still aim ----------------------------------------------------

	/**
	 * Shortest-path yaw rotation this tick past {@link #AIMSNAP_THRESHOLD}, out of a tick that was
	 * itself near-still (so a legitimate turn already in progress is not judged twice — once on the
	 * way out, once on the way back). A boundary crossing (179° → -179°) wraps to a tiny delta, not a
	 * near-360° one, so it never false-flags.
	 *
	 * <p>Derived from Iustitia (MIT), checks/movement/AimWrapCheck.kt.
	 */
	private void judgeAimSnap(String player, Track track, long tick, List<Violation> found) {
		Frame now = track.at(tick);
		Frame before = track.at(tick - 1);
		if (now == null || before == null) {
			return;
		}
		double wrapped = wrapDegrees(now.yaw() - before.yaw());
		boolean exempt = tick - track.lastShove < 5 || tick - track.lastHurt < 3;
		if (!exempt && Math.abs(track.lastWrappedDelta) < AIMSNAP_STILL) {
			boolean snapped = Math.abs(wrapped) > AIMSNAP_THRESHOLD;
			pushCap(track.aimSnapWindow, snapped, AIMSNAP_WINDOW);
			long snaps = track.aimSnapWindow.stream().filter(b -> b).count();
			if (snapped && snaps >= AIMSNAP_MIN) {
				add(found, player, Check.AIMSNAP,
						String.format(Locale.ROOT, "snapped %.0f° out of a still aim", Math.abs(wrapped)), tick);
			}
		}
		track.lastWrappedDelta = wrapped;
	}

	/** The shortest signed angular distance in degrees, in [-180, 180). */
	private static double wrapDegrees(double delta) {
		double wrapped = delta % 360.0;
		if (wrapped >= 180.0) {
			wrapped -= 360.0;
		} else if (wrapped < -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
	}

	// --- aimtrack: an aim that keeps pointing at one nearby player -------------------------------------

	/**
	 * Whether this player's broadcast yaw/pitch lands within tolerance of the bearing to the nearest
	 * other tracked player, this tick; judged over a rolling window while the player has attacked
	 * recently (idle "looking at a teammate" never feeds the window). A sustained near-perfect match
	 * rate is a silent aim: a real player's look wanders even while fighting.
	 *
	 * <p>Skipped from Iustitia's original: the pitch-GCD "too-clean mouse step" sub-signal, which
	 * needs a full-float-look / mouse-sensitivity substrate this mod has no way to observe (1.8.9 and
	 * 26.2 both broadcast quantised yaw/pitch with no sensitivity telemetry).
	 *
	 * <p>Derived from Iustitia (MIT), checks/movement/RotationTrackingCheck.kt.
	 */
	private void judgeAimTrack(String player, Track track, long tick, List<Violation> found) {
		if (tick - track.lastAttackTick > AIMTRACK_COMBAT_WINDOW) {
			return;
		}
		Frame now = track.at(tick);
		if (now == null) {
			return;
		}
		Track bestTrack = null;
		double bestDistance = AIMTRACK_RANGE;
		Frame bestFrame = null;
		for (Map.Entry<String, Track> entry : tracks.entrySet()) {
			if (entry.getKey().equals(player) || entry.getValue() == track) {
				continue;
			}
			Frame otherFrame = entry.getValue().at(tick);
			if (otherFrame == null || otherFrame.riding()) {
				continue;
			}
			double distance = now.feet().distanceTo(otherFrame.feet());
			if (distance < bestDistance) {
				bestDistance = distance;
				bestTrack = entry.getValue();
				bestFrame = otherFrame;
			}
		}
		if (bestTrack == null) {
			pushCap(track.aimTrackWindow, false, AIMTRACK_WINDOW);
			return;
		}

		double tcenter = bestFrame.sneaking() ? 0.75 : 0.9;
		Vec eye = now.eye();
		double cx = bestFrame.feet().x() - eye.x();
		double cy = bestFrame.feet().y() + tcenter - eye.y();
		double cz = bestFrame.feet().z() - eye.z();
		double horiz = Math.hypot(cx, cz);
		if (horiz < 0.3) {
			pushCap(track.aimTrackWindow, false, AIMTRACK_WINDOW);
			return;
		}
		double expectedYaw = Math.toDegrees(Math.atan2(-cx, cz));
		double expectedPitch = Math.toDegrees(Math.atan2(-cy, horiz));
		double yawDiff = Math.abs(wrapDegrees(now.yaw() - expectedYaw));
		double pitchDiff = Math.abs(now.pitch() - expectedPitch);
		boolean match = horiz < 2.0 ? yawDiff < 3.0 && pitchDiff < 4.0 : yawDiff < 8.0 && pitchDiff < 10.0;
		pushCap(track.aimTrackWindow, match, AIMTRACK_WINDOW);

		if (track.aimTrackWindow.size() >= AIMTRACK_WINDOW) {
			long matches = track.aimTrackWindow.stream().filter(b -> b).count();
			double rate = (double) matches / track.aimTrackWindow.size();
			if (rate > AIMTRACK_RATE) {
				add(found, player, Check.AIMTRACK,
						String.format(Locale.ROOT, "locked on a target %.0f%% of the time", rate * 100), tick);
			}
		}
	}

	// --- triggerbot: hits within a tick or two of the crosshair reaching the victim --------------------

	/**
	 * Per tick: raycasts this player's eye/look against every nearby victim's hitbox (vanilla melee
	 * reach) and records the tick the crosshair FIRST reached each one — but only when this player's
	 * own aim moved recently enough to have caused that edge (the held-aim discriminator: a target
	 * walking into a held crosshair is legitimate play, not a reaction).
	 *
	 * <p>Derived from Iustitia (MIT), checks/combat/TriggerbotCheck.kt.
	 */
	private void judgeTriggerbotTick(Track track, long tick) {
		Frame now = track.at(tick);
		if (now == null || now.riding()) {
			return;
		}
		if (Double.isNaN(track.triggerAimYaw)) {
			track.triggerAimYaw = now.yaw();
			track.triggerAimPitch = now.pitch();
		} else {
			double moved = Math.max(Math.abs(wrapDegrees(now.yaw() - track.triggerAimYaw)),
					Math.abs(now.pitch() - track.triggerAimPitch));
			track.triggerAimYaw = now.yaw();
			track.triggerAimPitch = now.pitch();
			if (moved >= TRIGGER_AIM_TURN_EPS) {
				track.lastAimMoveTick = tick;
			}
		}

		Vec eye = now.eye();
		Vec end = eye.add(now.look().scale(TRIGGER_REACH + 1.0));
		Set<String> nearby = new HashSet<>();
		for (Map.Entry<String, Track> entry : tracks.entrySet()) {
			if (entry.getValue() == track) {
				continue;
			}
			Frame victimFrame = entry.getValue().at(tick);
			if (victimFrame == null || now.feet().distanceTo(victimFrame.feet()) > TRIGGER_RANGE) {
				continue;
			}
			nearby.add(entry.getKey());
			boolean onNow = victimFrame.box().inflate(HITBOX_BORDER).entry(eye, end) >= 0;
			boolean prev = track.onHitbox.getOrDefault(entry.getKey(), false);
			if (onNow && !prev) {
				if (tick - track.lastAimMoveTick <= TRIGGER_AIM_TURN_WINDOW) {
					track.engagementStart.put(entry.getKey(), tick);
				} else {
					track.engagementStart.remove(entry.getKey());
				}
			} else if (!onNow) {
				track.engagementStart.remove(entry.getKey());
			}
			track.onHitbox.put(entry.getKey(), onNow);
		}
		track.onHitbox.keySet().retainAll(nearby);
		track.engagementStart.keySet().retainAll(nearby);
	}

	/** Called once an attack's attacker is resolved: was the hit within reaction range of the crosshair
	 * first reaching the victim, and has that happened consistently across recent hits? */
	private void judgeTriggerbotHit(String attacker, Track track, String victim, long tick, List<Violation> found) {
		Long start = track.engagementStart.get(victim);
		boolean fast = start != null && tick - start >= 0 && tick - start <= TRIGGER_MAX_REACTION_TICKS;
		pushCap(track.triggerbotWindow, fast, TRIGGER_WINDOW);

		int total = track.triggerbotWindow.size();
		long fastCount = track.triggerbotWindow.stream().filter(b -> b).count();
		double ratio = total > 0 ? (double) fastCount / total : 0;
		if (total >= TRIGGER_MIN_SAMPLES && ratio >= TRIGGER_RATIO) {
			add(found, attacker, Check.TRIGGERBOT,
					String.format(Locale.ROOT, "%d of %d hits within %d ticks of aiming on", fastCount, total,
							TRIGGER_MAX_REACTION_TICKS), tick);
		}
	}

	// --- hitflick: the aim flicks off the victim at the hit and snaps back -----------------------------

	/** Called once an attack's attacker is resolved: the yaw error to the victim's hitbox at the hit. */
	private void judgeHitFlickHit(String attacker, Track track, String victim, Frame attackerFrame,
			Frame victimFrame, long tick, List<Violation> found) {
		double dev = yawErrorToHitbox(attackerFrame, victimFrame);
		if (dev > HITFLICK_THRESHOLD) {
			track.flickTick = tick;
			track.flickVictim = victim;
		} else {
			judgeHitFlickPattern(attacker, track, tick, found, false);
		}
	}

	/** Called every tick: watches the short return window after a flick for the snap back onto the
	 * victim's hitbox, and times a flick out if it never returns. */
	private void judgeHitFlickTick(String player, Track track, long tick, List<Violation> found) {
		if (track.flickTick <= Long.MIN_VALUE / 2 || track.flickVictim == null) {
			return;
		}
		if (tick - track.flickTick > HITFLICK_RETURN_TICKS) {
			track.flickTick = Long.MIN_VALUE / 2;
			judgeHitFlickPattern(player, track, tick, found, false);
			return;
		}
		Track victim = tracks.get(track.flickVictim);
		Frame now = track.at(tick);
		Frame victimFrame = victim == null ? null : victim.at(tick);
		if (now == null || victimFrame == null) {
			return;
		}
		if (yawErrorToHitbox(now, victimFrame) <= HITFLICK_RETURN_DEV) {
			track.flickTick = Long.MIN_VALUE / 2;
			judgeHitFlickPattern(player, track, tick, found, true);
		}
	}

	private void judgeHitFlickPattern(String player, Track track, long tick, List<Violation> found, boolean flicked) {
		pushCap(track.hitFlickWindow, flicked, HITFLICK_WINDOW);
		long flicks = track.hitFlickWindow.stream().filter(b -> b).count();
		if (flicked && flicks >= HITFLICK_MIN) {
			add(found, player, Check.HITFLICK,
					String.format(Locale.ROOT, "snapped off the target and back within %d ticks on %d of the last %d hits",
							HITFLICK_RETURN_TICKS, flicks, track.hitFlickWindow.size()), tick);
		}
	}

	/** How far the attacker's yaw is, horizontally, past the near edge of the victim's hitbox. */
	private static double yawErrorToHitbox(Frame attacker, Frame victim) {
		Box box = victim.box().inflate(HITBOX_BORDER);
		double dx = victim.centre().x() - attacker.eye().x();
		double dz = victim.centre().z() - attacker.eye().z();
		double distance = Math.hypot(dx, dz);
		if (distance < 1e-6) {
			return 0;
		}
		double bearing = Math.toDegrees(Math.atan2(-dx, dz));
		double halfWidth = Math.max(box.maxX() - box.minX(), box.maxZ() - box.minZ()) / 2;
		double edge = Math.toDegrees(Math.atan2(halfWidth, distance));
		return Math.max(0, Math.abs(wrapDegrees(attacker.yaw() - bearing)) - edge);
	}

	// --- nofall: fell far enough to hurt, and did not -------------------------------------------------

	/**
	 * Tracks the highest feet-y since this player was last {@linkplain Frame#supported() supported} —
	 * not since the server last said {@code onGround}, which a NoFall cheat spoofs; {@code supported}
	 * is computed straight from the terrain under their feet and cannot be lied about. Touchdown (a
	 * tick where they are supported again) past {@link #NOFALL_DISTANCE} blocks of fall with no hurt
	 * nearby is NoFall.
	 *
	 * <p>Derived from Iustitia (MIT), checks/movement/NoFallDamageCheck.kt.
	 */
	private void judgeNoFall(String player, Track track, long tick, List<Violation> found) {
		Frame now = track.at(tick);
		if (now == null) {
			return;
		}
		boolean airborne = !now.supported() && !now.assisted() && !now.riding();
		if (airborne) {
			if (Double.isNaN(track.fallPeakY) || now.feet().y() > track.fallPeakY) {
				track.fallPeakY = now.feet().y();
			}
			return;
		}
		if (!Double.isNaN(track.fallPeakY)) {
			double fallDistance = track.fallPeakY - now.feet().y();
			if (fallDistance >= NOFALL_DISTANCE && tick - track.lastHurt > NOFALL_HURT_GRACE) {
				add(found, player, Check.NOFALL,
						String.format(Locale.ROOT, "fell %.1f blocks and took no fall damage", fallDistance), tick);
			}
		}
		track.fallPeakY = Double.NaN;
	}

	// --- step: a rise higher than a jump lets a leg climb -----------------------------------------------

	/**
	 * A grounded-to-grounded rise in one tick past what a jump explains. Vanilla's jump gives about
	 * 0.42 blocks of ground clearance; the allowance here is a little wider because a remote player's
	 * Jump Boost amplifier is not reliably visible to this client, so it cannot be subtracted out —
	 * left conservative on purpose (a real StepHeight cheat's climb is well past this either way).
	 *
	 * <p>Derived from Iustitia (MIT), checks/movement/StepHeightCheck.kt.
	 */
	private void judgeStep(String player, Track track, long tick, List<Violation> found) {
		Frame now = track.at(tick);
		Frame before = track.at(tick - 1);
		if (now == null || before == null || !now.supported() || !before.supported()
				|| now.assisted() || now.riding() || before.assisted() || before.riding()) {
			return;
		}
		double dy = now.feet().y() - before.feet().y();
		if (dy > Math.max(STEP_MIN, STEP_JUMP_ALLOWANCE) && dy <= STEP_MAX) {
			add(found, player, Check.STEP, String.format(Locale.ROOT, "stepped up %.2f blocks", dy), tick);
		}
	}

	// --- blink: froze, then snapped several blocks -------------------------------------------------------

	/**
	 * A freeze (near-zero movement) held for {@link #BLINK_FREEZE_TICKS} or more, immediately followed
	 * by a jump past {@link #BLINK_SNAP} blocks: packets held back and released in a burst. A
	 * server-wide hitch is already excluded — {@link #endTick} skips every movement judge, this one
	 * included, for {@link #LAG_WINDOW} ticks after {@link #serverLag}, and resets the freeze streak
	 * itself so it cannot carry suspicion across the gap into this player's next real freeze.
	 *
	 * <p>Derived from Iustitia (MIT), checks/movement/PacketGapCheck.kt.
	 */
	private void judgeBlink(String player, Track track, long tick, List<Violation> found) {
		Frame now = track.at(tick);
		Frame before = track.at(tick - 1);
		if (now == null || before == null) {
			track.stillStreak = 0;
			return;
		}
		double moved = now.horizontalFrom(before) + Math.abs(now.feet().y() - before.feet().y());
		if (moved < BLINK_FREEZE_EPSILON) {
			track.stillStreak++;
			return;
		}
		if (track.stillStreak >= BLINK_FREEZE_TICKS && moved > BLINK_SNAP) {
			add(found, player, Check.BLINK,
					String.format(Locale.ROOT, "froze %d ticks then jumped %.1f blocks", track.stillStreak, moved), tick);
		}
		track.stillStreak = 0;
	}

	// --- criticals: every hit on the same fixed fall-arc phase -----------------------------------------

	/**
	 * Called once an attack's attacker is resolved: the attacker's small upward rise in the tick
	 * before the hit — real criticals come from however high a jump happened to be timed, which varies
	 * hit to hit; a criticals-forcing hack reproduces the same phase almost exactly every time, so its
	 * spread over several hits is far tighter than a hand's.
	 *
	 * <p>Derived from Iustitia (MIT), checks/combat/CriticalsCheck.kt.
	 */
	private void judgeCriticals(String attacker, Track track, long tick, List<Violation> found) {
		Frame hitTick = track.at(tick - 1);
		Frame beforeThat = track.at(tick - 2);
		if (hitTick == null || beforeThat == null || hitTick.riding() || hitTick.assisted()) {
			return;
		}
		double rise = hitTick.feet().y() - beforeThat.feet().y();
		if (rise < CRIT_RISE_MIN || rise > CRIT_RISE_MAX) {
			return;
		}
		pushCap(track.critRiseWindow, rise, CRIT_WINDOW);
		if (track.critRiseWindow.size() >= CRIT_WINDOW) {
			double[] values = new double[track.critRiseWindow.size()];
			int i = 0;
			for (double v : track.critRiseWindow) {
				values[i++] = v;
			}
			double stdev = populationStDev(values);
			if (stdev < CRIT_STDEV_MAX) {
				add(found, attacker, Check.CRITICALS,
						String.format(Locale.ROOT, "same %.3f-block rise on every hit (stDev %.3f)", rise, stdev), tick);
			}
		}
	}

	/**
	 * Looking down at the bridge and moving straight away from where they look, blocks in hand and
	 * swinging: backwards bridging. Legs do that at up to about 4 blocks a second on the flat; past 5
	 * — or rising a tower while going sideways — it is a scaffold, whatever the rotation says.
	 */
	private static String backwardsBridge(List<Frame> frames, double limit) {
		Frame first = frames.get(0);
		Frame last = frames.get(frames.size() - 1);
		if (frames.stream().anyMatch(f -> f.held() != Frame.Held.BLOCK || f.assisted() || f.riding()) || last.pitch() < 50) {
			return null;
		}
		double dx = last.feet().x() - first.feet().x();
		double dz = last.feet().z() - first.feet().z();
		double ticks = frames.size() - 1;
		double speed = Math.hypot(dx, dz) / ticks;
		double rise = (last.feet().y() - first.feet().y()) / ticks;
		Vec look = last.look();
		double lookLength = Math.hypot(look.x(), look.z());
		if (speed < 1e-3 || lookLength < 1e-3) {
			return null;
		}
		double cos = (dx * look.x() + dz * look.z()) / (Math.hypot(dx, dz) * lookLength);
		if (cos > Math.cos(Math.toRadians(165)) || speed > 0.5) {
			return null;
		}
		if (Math.abs(rise) < 0.05 && speed > limit) {
			return String.format(Locale.ROOT, "bridged backwards at %.1f blocks/s", speed * 20);
		}
		if (rise > 0.2 && rise < 0.75 && speed > 0.15) {
			return String.format(Locale.ROOT, "towered up while bridging backwards at %.1f blocks/s", speed * 20);
		}
		return null;
	}

	private static boolean omniSprint(List<Frame> frames) {
		for (int i = 1; i < frames.size(); i++) {
			Frame now = frames.get(i);
			Frame before = frames.get(i - 1);
			if (!now.sprinting() || !now.supported() || now.assisted() || now.riding()) {
				return false;
			}
			double dx = now.feet().x() - before.feet().x();
			double dz = now.feet().z() - before.feet().z();
			double speed = Math.hypot(dx, dz);
			if (speed < 0.2) {
				return false;
			}
			Vec look = now.look();
			double lookLength = Math.hypot(look.x(), look.z());
			if (lookLength < 1e-3) {
				return false;
			}
			double cos = (dx * look.x() + dz * look.z()) / (speed * lookLength);
			if (cos > Math.cos(Math.toRadians(100))) {
				return false;
			}
		}
		return true;
	}

	private static boolean hovering(List<Frame> frames) {
		if (frames.stream().anyMatch(f -> f.supported() || f.onGround() || f.assisted() || f.riding())) {
			return false;
		}
		List<Frame> last = frames.subList(frames.size() - 21, frames.size());
		double travelled = 0;
		for (int i = 1; i < last.size(); i++) {
			if (Math.abs(last.get(i).feet().y() - last.get(i - 1).feet().y()) >= 0.1) {
				return false;
			}
			travelled += last.get(i).horizontalFrom(last.get(i - 1));
		}
		// A frozen, lagging player hangs in the air too; somebody flying goes somewhere.
		return travelled > 1.0;
	}

	private static BedDefense.Cell cell(Vec at) {
		return new BedDefense.Cell(floor(at.x()), floor(at.y()), floor(at.z()));
	}

	private static Vec centre(BedDefense.Cell cell) {
		return new Vec(cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5);
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
