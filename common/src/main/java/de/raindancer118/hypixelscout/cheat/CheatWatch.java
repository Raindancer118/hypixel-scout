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
 * <p>Several ideas here come from two open-source client-side detectors (both MIT): the
 * swing-then-push attribution, hits through walls, KeepSprint, backwards-bridging Scaffold and
 * reliefs from Alexdoru's HackerDetector (MegaWallsEnhancements); the split reach limit, multi-aura,
 * sprinting while sneaking, the invulnerability gap for knockback and the server-lag pause from
 * Iustitia.
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
	public record Tuning(double reachStanding, double reachMoving, double speedLimit, int fastPlaceLimit,
			double bridgeSpeed) {
		public static final Tuning DEFAULT = new Tuning(3.2, 3.8, 12.4 / 20, 13, 5.0 / 20);
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

	private record Swing(String player, long tick) {
	}

	/**
	 * @param prevHurt when the victim was hurt before this, for the invulnerability gap
	 */
	private record Hurt(String victim, long tick, Hit hit, String cause, long prevHurt) {
	}

	private record Attack(String attacker, String victim, long tick) {
	}

	private record Knockback(String victim, String attacker, long tick, long prevHurt) {
	}

	private record SprintHit(String attacker, long tick) {
	}

	private record Placement(BedDefense.Cell cell, long tick, boolean thrown) {
	}

	/** A placement with its candidate placer, waiting for its burst-and-look window to fill in. */
	private record PendingPlacement(String placer, BedDefense.Cell cell, long tick) {
	}

	private record Blast(Vec centre, long tick) {
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
		Track track = track(player);
		track.swings.addLast(tick);
		while (track.swings.size() > 40) {
			track.swings.removeFirst();
		}
		swings.add(new Swing(player, tick));
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
			if (victims != null && victims.size() >= 2) {
				add(found, attack.attacker(), Check.MULTIAURA, "hit " + victims.size() + " players in one tick", attack.tick());
			}
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
			attacker = candidates.getFirst();
		}
		if (attacker.equals(self)) {
			knockbacks.add(new Knockback(hurt.victim(), self, hurt.tick(), hurt.prevHurt()));
			return;
		}

		Track track = tracks.get(attacker);
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
			double angle = Math.toDegrees(Math.acos(Math.clamp(towards.scale(1 / distance).dot(a.look()), -1, 1)));
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
		Frame before = after.getFirst();
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

		double along = after.getLast().feet().subtract(before.feet()).dot(away);
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
			bed.add(brokenBeds.removeFirst());
			brokenBeds.removeIf(cell -> {
				boolean half = cell.x() == bed.getFirst().x() && cell.y() == bed.getFirst().y()
						? Math.abs(cell.z() - bed.getFirst().z()) == 1
						: cell.z() == bed.getFirst().z() && cell.y() == bed.getFirst().y() && Math.abs(cell.x() - bed.getFirst().x()) == 1;
				if (half) {
					bed.add(cell);
				}
				return half;
			});
			judgeBed(bed, tick, terrain, found);
		}
	}

	private void judgeBed(List<BedDefense.Cell> bed, long tick, Terrain terrain, List<Violation> found) {
		Vec centre = centre(bed.getFirst());
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
		boolean calm = track.lastShove < tick - 40;

		List<Frame> using = track.run(tick - 9, tick);
		if (using != null && track.lastShove < tick - 20 && using.stream().allMatch(Frame::usingItem)
				&& using.stream().noneMatch(f -> f.assisted() || f.riding())) {
			double speed = using.getLast().horizontalFrom(using.get(using.size() - 6)) / 5;
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

	/**
	 * Looking down at the bridge and moving straight away from where they look, blocks in hand and
	 * swinging: backwards bridging. Legs do that at up to about 4 blocks a second on the flat; past 5
	 * — or rising a tower while going sideways — it is a scaffold, whatever the rotation says.
	 */
	private static String backwardsBridge(List<Frame> frames, double limit) {
		Frame first = frames.getFirst();
		Frame last = frames.getLast();
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
