package de.raindancer118.hypixelscout.cheat;

import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.Vec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Watches the other players for what only a cheat makes possible, from nothing but what the client
 * is told anyway: where everybody is and looks, who swings, who gets hurt, which blocks appear and
 * which beds vanish.
 *
 * <p>Fed in the order a client tick has it: the packets first ({@link #swing}, {@link #hurt},
 * {@link #placed}, …), then a {@link #frame} for every player in sight, then {@link #endTick}, which
 * judges whatever there is enough of to judge and returns the sightings. Some judgements wait a few
 * ticks — knockback needs to be seen landing — so a sighting can come a little after the fact.
 *
 * <p>Every check leans towards silence. Positions arrive twenty times a second and a little late,
 * so distances are measured at the most favourable of several aligned ticks; anything with a second
 * explanation — a blast, a hit, somebody else close by, the player themselves — is left alone. What
 * is left is still only a sighting: {@link Suspicion} decides when sightings make a flag.
 *
 * <p>The player running the client ({@link #setSelf}) is never a suspect, but counts as a witness:
 * their own swing explains a hit, their own block explains a placement.
 */
public final class CheatWatch {
	/** The blocks of the world, as far as the checks care: solid or not. */
	@FunctionalInterface
	public interface Terrain {
		boolean solid(int x, int y, int z);
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

	/** Frames kept per player: three seconds. */
	private static final int HISTORY = 60;
	/** A melee hit reaches three blocks from the eyes to the hitbox grown by 0.1; this is on top. */
	static final double REACH_LIMIT = 3.3;
	private static final double HITBOX_BORDER = 0.1;
	/** Further than this from the victim a swing was aimed at somebody else. */
	private static final double ATTACK_RANGE = 6.0;
	/** Positions lag; distances are the best of this many aligned ticks back. */
	private static final int LAG_TICKS = 5;
	/** Degrees off the look, past the edge of the victim's hitbox, that no hit can come from. */
	private static final double AURA_ANGLE = 75.0;
	/** For a hurt of unknown cause, a swinging player further off their look than this did not do it. */
	private static final double UNSURE_ANGLE = 45.0;
	private static final int KNOCKBACK_TICKS = 6;
	/** The push a 1.8 hit gives when the server does not say. */
	private static final double DEFAULT_KNOCKBACK = 0.4;
	/** An explosion this close shoves a player, or opens a bed. */
	private static final double BLAST_RADIUS = 8.0;
	private static final double PLACE_REACH = 5.5;
	private static final double BED_REACH = 6.5;
	/** Blocks in a second from one player past which nobody is clicking. */
	private static final int FASTPLACE_LIMIT = 13;
	/** Blocks in one tick, close together: a pop-up tower or some other placed structure. */
	private static final int STRUCTURE_BLOCKS = 4;
	/** Blocks a tick, over a second: a Speed II sprint-jump is about 0.45. */
	private static final double SPEED_LIMIT = 0.62;
	/** A move this long in one tick is a teleport — a pearl, a respawn — not running. */
	private static final double TELEPORT = 4.0;

	private static final class Track {
		final ArrayDeque<Frame> frames = new ArrayDeque<>();
		final ArrayDeque<Long> swings = new ArrayDeque<>();
		final ArrayDeque<Long> placements = new ArrayDeque<>();
		final Map<Check, Long> lastSeen = new EnumMap<>(Check.class);
		long lastShove = Long.MIN_VALUE / 2;
		long lastHurt = Long.MIN_VALUE / 2;
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
	}

	private record Swing(String player, long tick) {
	}

	private record Hurt(String victim, long tick, Hit hit, String cause) {
	}

	private record Knockback(String victim, String attacker, long tick) {
	}

	private record Placement(BedDefense.Cell cell, long tick, boolean thrown) {
	}

	private record Blast(Vec centre, long tick) {
	}

	private final Map<String, Track> tracks = new HashMap<>();
	private final List<Swing> swings = new ArrayList<>();
	private final List<Hurt> hurts = new ArrayList<>();
	private final List<Knockback> knockbacks = new ArrayList<>();
	private final List<Placement> placements = new ArrayList<>();
	private final List<BedDefense.Cell> brokenBeds = new ArrayList<>();
	private final List<Blast> blasts = new ArrayList<>();
	private String self = "";

	/** The player running the client: a witness, never a suspect. */
	public void setSelf(String name) {
		self = name == null ? "" : name;
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
		track.lastShove = tick;
		track.lastHurt = tick;
		hurts.add(new Hurt(victim, tick, hit, cause));
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

	/**
	 * A block appeared where there was none.
	 *
	 * @param thrown whether a thrown thing was right there — a bridge egg lays blocks nobody placed
	 */
	public void placed(BedDefense.Cell cell, long tick, boolean thrown) {
		placements.add(new Placement(cell, tick, thrown));
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
		knockbacks.clear();
		placements.clear();
		brokenBeds.clear();
		blasts.clear();
	}

	/** Judges what can be judged now; call after this tick's frames. */
	public List<Violation> endTick(long tick, Terrain terrain) {
		List<Violation> found = new ArrayList<>();

		judgeSwings(tick, found);
		judgeHurts(found);
		judgeKnockbacks(tick, terrain, found);
		judgePlacements(tick, found);
		judgeBeds(tick, terrain, found);
		for (Map.Entry<String, Track> entry : tracks.entrySet()) {
			if (!entry.getKey().equals(self)) {
				judgeMovement(entry.getKey(), entry.getValue(), tick, found);
			}
		}

		blasts.removeIf(blast -> blast.tick() < tick - 100);
		tracks.values().removeIf(track -> track.frames.isEmpty() ? track.lastHurt < tick - 100
				: track.frames.peekLast().tick() < tick - HISTORY);
		return found;
	}

	private Track track(String player) {
		return tracks.computeIfAbsent(player, name -> new Track());
	}

	private void add(List<Violation> found, String player, Check check, String detail, long tick) {
		if (player.equals(self)) {
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

	// --- autoblock: a swing in the middle of using an item ------------------------------------------

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
			if (around != null && around.stream().allMatch(Frame::usingItem)) {
				add(found, swing.player(), Check.AUTOBLOCK, "swung while using an item", swing.tick());
			}
		}
	}

	// --- reach, killaura: who hit whom, from where ----------------------------------------------------

	private void judgeHurts(List<Violation> found) {
		for (Hurt hurt : hurts) {
			if (hurt.hit() != Hit.OTHER) {
				judgeHit(hurt, found);
			}
		}
		hurts.clear();
	}

	private void judgeHit(Hurt hurt, List<Violation> found) {
		Track victim = tracks.get(hurt.victim());
		if (victim == null) {
			return;
		}
		boolean unsure = hurt.hit() == Hit.UNKNOWN && hurt.cause() == null;
		if (unsure && fellJustNow(victim, hurt.tick())) {
			return;
		}

		List<String> candidates = new ArrayList<>();
		boolean selfHit = false;
		for (Map.Entry<String, Track> entry : tracks.entrySet()) {
			String name = entry.getKey();
			if (name.equals(hurt.victim())) {
				continue;
			}
			boolean named = name.equals(hurt.cause());
			if (!named && !entry.getValue().swungWithin(hurt.tick() - 2, hurt.tick())) {
				continue;
			}
			double distance = reachDistance(entry.getValue(), victim, hurt.tick());
			if (distance > ATTACK_RANGE && !named) {
				continue;
			}
			// Without the server saying it was a punch, only somebody facing the victim fits.
			if (unsure && !name.equals(self) && offAngle(entry.getValue(), victim, hurt.tick()) > UNSURE_ANGLE) {
				continue;
			}
			if (name.equals(self)) {
				selfHit = distance <= REACH_LIMIT + 1.2;
				continue;
			}
			candidates.add(name);
			if (named) {
				candidates = new ArrayList<>(List.of(name));
				break;
			}
		}

		if (selfHit) {
			// The player's own hit explains it; their knockback on the victim is still worth watching.
			knockbacks.add(new Knockback(hurt.victim(), self, hurt.tick()));
			return;
		}
		if (candidates.size() != 1) {
			return;
		}

		String attacker = candidates.getFirst();
		Track track = tracks.get(attacker);
		double distance = reachDistance(track, victim, hurt.tick());
		if (distance > REACH_LIMIT && distance <= ATTACK_RANGE) {
			add(found, attacker, Check.REACH, String.format(Locale.ROOT, "%.1f blocks", distance), hurt.tick());
		}

		double angle = offAngle(track, victim, hurt.tick());
		if (angle > AURA_ANGLE) {
			add(found, attacker, Check.KILLAURA, String.format(Locale.ROOT, "hit %.0f° outside their view", angle),
					hurt.tick());
		}

		knockbacks.add(new Knockback(hurt.victim(), attacker, hurt.tick()));
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

	// --- anti-knockback: hit, and nothing happened -----------------------------------------------------

	private void judgeKnockbacks(long tick, Terrain terrain, List<Violation> found) {
		Iterator<Knockback> pending = knockbacks.iterator();
		while (pending.hasNext()) {
			Knockback hit = pending.next();
			if (hit.tick() + KNOCKBACK_TICKS > tick) {
				continue;
			}
			pending.remove();
			if (hit.victim().equals(self)) {
				continue;
			}

			String detail = knockbackDetail(hit, terrain);
			if (detail != null) {
				add(found, hit.victim(), Check.VELOCITY, detail, hit.tick());
			}
		}
	}

	/** What is wrong with the victim's knockback, or {@code null} when nothing is (or nothing is sure). */
	private String knockbackDetail(Knockback hit, Terrain terrain) {
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
			return String.format(Locale.ROOT, "moved %.2f blocks from a hit", Math.max(0, along));
		}
		return null;
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

			Track track = tracks.get(placer);
			track.placements.addLast(placement.tick());
			while (!track.placements.isEmpty() && track.placements.peekFirst() <= placement.tick() - 20) {
				track.placements.removeFirst();
			}
			if (track.placements.size() > FASTPLACE_LIMIT) {
				add(found, placer, Check.FASTPLACE, track.placements.size() + " blocks a second", placement.tick());
			}

			if (!lookedAt(track, placement)) {
				add(found, placer, Check.SCAFFOLD, "placed a block they were not looking at", placement.tick());
			}
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

	/** Whether the placer's look passed by the new block at any of the ticks up to the placement. */
	private static boolean lookedAt(Track track, Placement placement) {
		BedDefense.Cell cell = placement.cell();
		Box box = new Box(cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + 1, cell.z() + 1).inflate(0.35);
		for (int back = 0; back <= 3; back++) {
			Frame at = track.at(placement.tick() - back);
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

	// --- speed, fly, noslow, omni-sprint: movement over the last ticks ---------------------------------------

	private void judgeMovement(String player, Track track, long tick, List<Violation> found) {
		boolean calm = track.lastShove < tick - 40;

		List<Frame> using = track.run(tick - 9, tick);
		if (using != null && track.lastShove < tick - 20 && using.stream().allMatch(Frame::usingItem)
				&& using.stream().noneMatch(f -> f.assisted() || f.riding())) {
			double speed = using.getLast().horizontalFrom(using.get(using.size() - 6)) / 5;
			if (speed > 0.18) {
				add(found, player, Check.NOSLOW,
						String.format(Locale.ROOT, "%.1f blocks/s while using an item", speed * 20), tick);
			} else if (using.stream().allMatch(Frame::sprinting)) {
				add(found, player, Check.NOSLOW, "sprinted while using an item", tick);
			}
		}

		List<Frame> sprint = track.run(tick - 10, tick);
		if (sprint != null && track.lastShove < tick - 20 && omniSprint(sprint)) {
			add(found, player, Check.SPRINT, "sprinted backwards", tick);
		}

		List<Frame> second = track.run(tick - 20, tick);
		if (second != null && calm && second.stream().noneMatch(f -> f.assisted() || f.riding())) {
			double travelled = 0;
			for (int i = 1; i < second.size(); i++) {
				travelled += second.get(i).horizontalFrom(second.get(i - 1));
			}
			double speed = travelled / 20;
			if (speed > SPEED_LIMIT) {
				add(found, player, Check.SPEED, String.format(Locale.ROOT, "%.1f blocks/s", speed * 20), tick);
			}
		}

		List<Frame> air = track.run(tick - 30, tick);
		if (air != null && track.lastShove < tick - 60 && hovering(air)) {
			add(found, player, Check.FLY, "moved in mid-air without falling", tick);
		}
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

	private static Vec centre(BedDefense.Cell cell) {
		return new Vec(cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5);
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
