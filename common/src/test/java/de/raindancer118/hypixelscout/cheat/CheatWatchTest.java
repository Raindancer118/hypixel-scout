package de.raindancer118.hypixelscout.cheat;

import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.flight.Box;
import de.raindancer118.hypixelscout.flight.Vec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every check against a staged scene: players moved tick by tick on a flat floor (solid below y 0),
 * doing what a cheat makes them do — and what a legit player does that looks close to it.
 */
class CheatWatchTest {
	private static final String SELF = "Me";

	private final CheatWatch watch = new CheatWatch();
	private final Set<List<Integer>> blocks = new HashSet<>();
	private final Map<String, Pose> poses = new HashMap<>();
	private final List<Violation> seen = new ArrayList<>();
	private final List<Violation> reliefs = new ArrayList<>();
	private long tick;

	CheatWatchTest() {
		watch.setSelf(SELF);
	}

	/** Where somebody is and what they are doing, carried from tick to tick until changed. */
	private static final class Pose {
		double x;
		double y;
		double z;
		double yaw;
		double pitch;
		boolean sprinting;
		boolean using;
		boolean flying;
		boolean sneaking;
		Frame.Held held = Frame.Held.SWORD;
	}

	private final CheatWatch.Terrain terrain = (x, y, z) -> y < 0 || blocks.contains(List.of(x, y, z));

	private Pose put(String name, double x, double z, double yaw) {
		Pose pose = new Pose();
		pose.x = x;
		pose.z = z;
		pose.yaw = yaw;
		poses.put(name, pose);
		return pose;
	}

	/** The yaw that faces from one point to another, the way Minecraft counts it. */
	private static double yawTowards(double fromX, double fromZ, double toX, double toZ) {
		return Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
	}

	private void step() {
		step(terrain);
	}

	/** Same as {@link #step()}, but against a different terrain — for tests of {@code solidAt}. */
	private void step(CheatWatch.Terrain customTerrain) {
		tick++;
		for (Map.Entry<String, Pose> entry : poses.entrySet()) {
			Pose p = entry.getValue();
			Vec feet = new Vec(p.x, p.y, p.z);
			boolean supported = p.y <= 0.001
					|| customTerrain.solid((int) Math.floor(p.x), (int) Math.floor(p.y - 0.1), (int) Math.floor(p.z));
			watch.frame(entry.getKey(), new Frame(tick, feet, feet.add(new Vec(0, 1.62, 0)),
					new Box(p.x - 0.3, p.y, p.z - 0.3, p.x + 0.3, p.y + 1.8, p.z + 0.3), p.yaw, p.pitch,
					supported && !p.flying, supported && !p.flying, p.sprinting, p.using, false, false, p.sneaking, p.held));
		}
		for (Violation violation : watch.endTick(tick, customTerrain)) {
			(violation.relief() ? reliefs : seen).add(violation);
		}
	}

	private void steps(int count) {
		for (int i = 0; i < count; i++) {
			step();
		}
	}

	private List<Check> checks() {
		return seen.stream().map(Violation::check).toList();
	}

	// --- autoblock --------------------------------------------------------------------------------

	@Test
	void swingingWhileBlockingIsAutoBlock() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.using = true;
		steps(6);
		watch.swing("Cheater", tick + 1);
		steps(3);

		assertThat(checks()).containsExactly(Check.AUTOBLOCK);
		assertThat(seen.getFirst().player()).isEqualTo("Cheater");
	}

	@Test
	void aSwingRightAfterRaisingTheSwordIsBlockHitting() {
		Pose player = put("Legit", 0, 0, 0);
		steps(5);
		player.using = true;
		watch.swing("Legit", tick + 1);
		steps(4);
		player.using = false;
		watch.swing("Legit", tick + 1);
		steps(4);

		assertThat(seen).isEmpty();
	}

	@Test
	void swingingWhileEatingIsNotAutoBlockButHittingWhileEatingIsKillAura() {
		Pose cheater = put("Cheater", 0, 2.5, 180);
		put("Victim", 0, 0, 0);
		cheater.using = true;
		cheater.held = Frame.Held.OTHER;
		steps(6);
		watch.swing("Cheater", tick + 1);
		steps(3);
		assertThat(checks()).doesNotContain(Check.AUTOBLOCK);

		watch.swing("Cheater", tick + 1);
		watch.attack("Cheater", "Victim", tick + 1);
		step();
		assertThat(seen).anyMatch(v -> v.check() == Check.KILLAURA && v.detail().contains("using an item"));
	}

	@Test
	void aSwingWithTheSwordDownSpeaksForThem() {
		put("Legit", 0, 0, 0);
		steps(6);
		watch.swing("Legit", tick + 1);
		steps(3);

		assertThat(seen).isEmpty();
		assertThat(reliefs).extracting(Violation::check).contains(Check.AUTOBLOCK);
	}

	// --- noslow and sprint --------------------------------------------------------------------------

	@Test
	void walkingAtFullSpeedWhileEatingIsNoSlow() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.using = true;
		for (int i = 0; i < 15; i++) {
			cheater.z += 0.25;
			step();
		}
		assertThat(checks()).contains(Check.NOSLOW);
	}

	@Test
	void theSprintFlagLingeringIntoTheFirstBitesIsFine() {
		Pose player = put("Legit", 0, 0, 0);
		player.using = true;
		player.sprinting = true;
		player.held = Frame.Held.OTHER;
		for (int i = 0; i < 12; i++) {
			player.z += 0.05;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void sprintingThroughAWholeMealIsNoSlow() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.using = true;
		cheater.sprinting = true;
		cheater.held = Frame.Held.OTHER;
		for (int i = 0; i < 18; i++) {
			cheater.z += 0.05;
			step();
		}
		assertThat(checks()).contains(Check.NOSLOW);
	}

	@Test
	void sprintingWhileSneakingIsASprintHack() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.sprinting = true;
		cheater.sneaking = true;
		for (int i = 0; i < 6; i++) {
			cheater.z += 0.1;
			step();
		}
		assertThat(checks()).containsExactly(Check.SPRINT);
	}

	@Test
	void creepingWhileEatingIsFine() {
		Pose player = put("Legit", 0, 0, 0);
		player.using = true;
		for (int i = 0; i < 30; i++) {
			player.z += 0.05;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void flyingBackAfterAHitWhileEatingIsTheHitNotNoSlow() {
		Pose player = put("Legit", 0, 0, 0);
		player.using = true;
		steps(3);
		watch.hurt("Legit", tick + 1, CheatWatch.Hit.OTHER, null);
		for (int i = 0; i < 12; i++) {
			player.z += 0.3;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void sprintingBackwardsIsOmniSprint() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.sprinting = true;
		for (int i = 0; i < 15; i++) {
			cheater.z -= 0.28;
			step();
		}
		assertThat(checks()).containsExactly(Check.SPRINT);
	}

	@Test
	void sprintingForwardsIsFine() {
		Pose player = put("Legit", 0, 0, 0);
		player.sprinting = true;
		for (int i = 0; i < 30; i++) {
			player.z += 0.28;
			step();
		}
		assertThat(seen).isEmpty();
	}

	// --- reach, killaura -----------------------------------------------------------------------------

	@Test
	void aHitFromFourBlocksIsReach() {
		put("Victim", 0, 0, 0);
		put("Cheater", 0, 4.4, 180);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		step();

		assertThat(checks()).containsExactly(Check.REACH);
		assertThat(seen.getFirst().player()).isEqualTo("Cheater");
		assertThat(seen.getFirst().detail()).contains("4.0");
	}

	@Test
	void aFightBetweenTwoOthersIsJudgedWithMeStandingBy() {
		put(SELF, 3, 0, 0);
		put("Victim", 0, 0, 0);
		put("Cheater", 0, 4.4, 180);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		steps(8);

		assertThat(checks()).containsExactly(Check.REACH, Check.VELOCITY);
	}

	@Test
	void standingStillAHitFromThreeAndAHalfBlocksIsReach() {
		put("Victim", 0, 0, 0);
		put("Cheater", 0, 3.9, 180);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		step();

		assertThat(checks()).containsExactly(Check.REACH);
	}

	@Test
	void inAChaseAHitFromThreeAndAHalfBlocksIsLag() {
		Pose victim = put("Victim", 0, 0, 180);
		Pose chaser = put("Chaser", 0, 3.9, 180);
		for (int i = 0; i < 6; i++) {
			victim.z -= 0.28;
			chaser.z -= 0.28;
			step();
		}
		watch.swing("Chaser", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		step();

		assertThat(checks()).doesNotContain(Check.REACH);
	}

	@Test
	void aTighterMovingLimitCatchesWhatTheDefaultLetsThrough() {
		watch.setTuning(new CheatWatch.Tuning(3.2, 3.4, CheatWatch.Tuning.DEFAULT.speedLimit(),
				CheatWatch.Tuning.DEFAULT.fastPlaceLimit(), CheatWatch.Tuning.DEFAULT.bridgeSpeed()));
		inAChaseAHitFromThreeAndAHalfBlocksIsLag_scene();
		assertThat(checks()).contains(Check.REACH);
	}

	@Test
	void aLooserSpeedLimitLetsAFasterRunnerGo() {
		watch.setTuning(new CheatWatch.Tuning(3.2, 3.8, 1.0, 13, 0.25));
		Pose runner = put("Runner", 0, 0, 0);
		for (int i = 0; i < 30; i++) {
			runner.z += 0.8;
			step();
		}
		assertThat(checks()).doesNotContain(Check.SPEED);
	}

	private void inAChaseAHitFromThreeAndAHalfBlocksIsLag_scene() {
		Pose victim = put("Victim", 0, 0, 180);
		Pose chaser = put("Chaser", 0, 3.9, 180);
		for (int i = 0; i < 6; i++) {
			victim.z -= 0.28;
			chaser.z -= 0.28;
			step();
		}
		watch.swing("Chaser", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		step();
	}

	@Test
	void theSwingAndPushArrivingTogetherNameTheAttackerInACrowd() {
		put("Victim", 0, 0, 0);
		put("One", 0, 4.4, 180);
		put("Two", 4.4, 0, 90);
		steps(6);
		watch.swing("One", tick + 1);
		watch.swing("Two", tick + 1);
		watch.attack("One", "Victim", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.UNKNOWN, null);
		step();

		assertThat(seen).extracting(Violation::player, Violation::check)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("One", Check.REACH));
	}

	@Test
	void anAttackSeenOnlyByItsPushIsStillJudged() {
		put("Victim", 0, 0, 0);
		put("Cheater", 0, 4.4, 180);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.attack("Cheater", "Victim", tick + 1);
		step();

		assertThat(checks()).containsExactly(Check.REACH);
	}

	@Test
	void hittingTwoPlayersInOneTickIsMultiAura() {
		put("Cheater", 0, 0, 0);
		put("Left", -1.5, 1.5, 0);
		put("Right", 1.5, 1.5, 0);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.attack("Cheater", "Left", tick + 1);
		watch.attack("Cheater", "Right", tick + 1);
		step();

		assertThat(checks()).contains(Check.MULTIAURA);
	}

	private void wallBetween() {
		for (int x = -2; x <= 2; x++) {
			for (int y = 0; y <= 3; y++) {
				blocks.add(List.of(x, y, 1));
			}
		}
	}

	@Test
	void hitsThroughAWallAgainAndAgainAreKillAura() {
		wallBetween();
		put("Victim", 0.5, -0.5, 0);
		put("Cheater", 0.5, 2.5, 180);
		steps(6);
		// WALL_MIN is now four, not three: every hit stays fully occluded the whole aligned window.
		for (int hit = 0; hit < 4; hit++) {
			watch.swing("Cheater", tick + 1);
			watch.attack("Cheater", "Victim", tick + 1);
			steps(12);
		}

		assertThat(seen).anyMatch(v -> v.check() == Check.KILLAURA && v.detail().contains("wall"));
	}

	@Test
	void aHitThatOnlyLooksOccludedAtTheLatestLaggedFrameIsNotThroughWall() {
		// Positions lag: the attacker's current, aligned-to-the-hit frame looks blocked, but a frame
		// from just a tick earlier — still within the aligned window — had a clear line. The most
		// favourable of the aligned ticks is what counts, so this must never build a wall pattern.
		wallBetween();
		put("Victim", 0.5, -0.5, 0);
		// Far enough off to the side that the wall (only x -2..2) no longer crosses the line of sight.
		Pose cheater = put("Cheater", 6.0, 2.5, 180);
		for (int hit = 0; hit < 4; hit++) {
			steps(6);
			cheater.x = 0.5; // this tick alone looks occluded
			watch.swing("Cheater", tick + 1);
			watch.attack("Cheater", "Victim", tick + 1);
			step();
			cheater.x = 6.0; // back to the clear spot before the next swing
		}

		assertThat(seen).noneMatch(v -> v.check() == Check.KILLAURA && v.detail().contains("wall"));
	}

	@Test
	void aClearLineToOneCornerOfTheHitboxIsNotThroughWall() {
		// A single pillar sits directly on the eye/centre/feet line, but the far corner of the
		// victim's hitbox peeks around it — a clear line to any one sample point says "not occluded".
		for (int y = 0; y <= 3; y++) {
			blocks.add(List.of(0, y, 1));
		}
		put("Victim", 0.9, -0.5, 0);
		// Far enough away that x has already crossed well past the pillar's edge by the time the ray
		// enters its z-slab, so the corner ray clears the pillar throughout, not just at one instant.
		put("Cheater", 0.9, 10, 180);
		steps(6);
		for (int hit = 0; hit < 4; hit++) {
			watch.swing("Cheater", tick + 1);
			watch.attack("Cheater", "Victim", tick + 1);
			steps(12);
		}

		assertThat(seen).noneMatch(v -> v.check() == Check.KILLAURA && v.detail().contains("wall"));
	}

	@Test
	void blockedBetweenUsesTheExactPointNotTheWholeCell() {
		// Every check but through-walls would treat this cell as a full, solid cube; its real
		// collision shape (a thin pane, say) does not actually reach the sight line's sample points.
		wallBetween();
		CheatWatch.Terrain thinShapes = new CheatWatch.Terrain() {
			@Override
			public boolean solid(int x, int y, int z) {
				return terrain.solid(x, y, z);
			}

			@Override
			public boolean solidAt(double x, double y, double z) {
				return false;
			}
		};
		put("Victim", 0.5, -0.5, 0);
		put("Cheater", 0.5, 2.5, 180);
		for (int i = 0; i < 6; i++) {
			step(thinShapes);
		}
		for (int hit = 0; hit < 4; hit++) {
			watch.swing("Cheater", tick + 1);
			watch.attack("Cheater", "Victim", tick + 1);
			for (int i = 0; i < 12; i++) {
				step(thinShapes);
			}
		}

		assertThat(seen).noneMatch(v -> v.check() == Check.KILLAURA && v.detail().contains("wall"));
	}

	@Test
	void hitsInTheOpenAreNotThroughWalls() {
		put("Victim", 0.5, -0.5, 0);
		Pose attacker = put("Legit", 0.5, 2.0, 180);
		steps(6);
		for (int hit = 0; hit < 3; hit++) {
			watch.swing("Legit", tick + 1);
			watch.attack("Legit", "Victim", tick + 1);
			steps(12);
		}

		assertThat(seen).noneMatch(v -> v.check() == Check.KILLAURA);
		assertThat(attacker).isNotNull();
	}

	/** Sprinting at the victim and hitting four times, each time going on at {@code after} of the speed. */
	private void sprintHits(double after) {
		Pose victim = put("Victim", 0, 0, 180);
		Pose attacker = put("Attacker", 0, 2.6, 180);
		attacker.sprinting = true;
		steps(2);
		for (int hit = 0; hit < 4; hit++) {
			for (int i = 0; i < 3; i++) {
				attacker.z -= 0.28;
				victim.z -= 0.28;
				step();
			}
			watch.swing("Attacker", tick + 1);
			watch.attack("Attacker", "Victim", tick + 1);
			attacker.z -= 0.28;
			victim.z -= 0.6;
			step();
			attacker.z -= 0.28 * after;
			victim.z -= 0.5;
			step();
			for (int i = 0; i < 6; i++) {
				attacker.z -= 0.28;
				victim.z -= 0.3;
				step();
			}
		}
	}

	@Test
	void runningOnAtFullSpeedThroughSprintHitsIsKeepSprint() {
		sprintHits(1.0);
		assertThat(checks()).contains(Check.KEEPSPRINT);
	}

	@Test
	void slowingDownOnASprintHitIsFine() {
		sprintHits(0.6);
		assertThat(checks()).doesNotContain(Check.KEEPSPRINT);
	}

	@Test
	void aSecondHitInsideTheFirstOnesInvulnerabilityIsNotHeldToFullKnockback() {
		put("Victim", 0, 0, 180);
		put("Attacker", 0, 2.5, 180);
		steps(6);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.OTHER, null);
		steps(5);
		watch.swing("Attacker", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.VELOCITY);
	}

	@Test
	void whileTheServerLagsNothingIsJudged() {
		put("Victim", 0, 0, 0);
		Pose cheater = put("Cheater", 0, 4.4, 180);
		steps(6);
		watch.serverLag(tick);
		watch.swing("Cheater", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		step();
		for (int i = 0; i < 5; i++) {
			cheater.z += 1.0;
			step();
		}

		assertThat(seen).isEmpty();
	}

	@Test
	void aCheckSwitchedOffSeesNothing() {
		watch.setEnabled(check -> check != Check.REACH);
		put("Victim", 0, 0, 0);
		put("Cheater", 0, 4.4, 180);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.REACH).contains(Check.VELOCITY);
	}

	@Test
	void aHitFromThreeBlocksIsFine() {
		put("Victim", 0, 0, 0);
		put("Legit", 0, 3.2, 180);
		steps(6);
		watch.swing("Legit", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.REACH, Check.KILLAURA);
	}

	@Test
	void aHitNobodyCanBePinnedOnIsNobodysReach() {
		put("Victim", 0, 0, 0);
		put("One", 0, 4.4, 180);
		put("Two", 4.4, 0, 90);
		steps(6);
		watch.swing("One", tick + 1);
		watch.swing("Two", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.REACH);
	}

	@Test
	void whenIHitSomebodyThatExplainsIt() {
		put("Victim", 0, 0, 0);
		put(SELF, 0, -2.5, 0);
		put("Bystander", 0, 4.4, 180);
		steps(6);
		watch.swing(SELF, tick + 1);
		watch.swing("Bystander", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.REACH);
	}

	@Test
	void anArrowIsNotAMeleeHit() {
		put("Victim", 0, 0, 0);
		put("Archer", 0, 4.4, 180);
		steps(6);
		watch.swing("Archer", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.OTHER, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.REACH);
	}

	@Test
	void anUnexplainedHurtRightAfterAFallIsTheFall() {
		Pose victim = put("Victim", 0, 0, 0);
		put("Fighter", 0, 4.4, 180);
		victim.y = 5;
		victim.flying = true;
		steps(2);
		for (int i = 0; i < 5; i++) {
			victim.y -= 1;
			step();
		}
		victim.flying = false;
		watch.swing("Fighter", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.UNKNOWN, null);
		steps(8);

		assertThat(checks()).doesNotContain(Check.REACH, Check.KILLAURA, Check.VELOCITY);
	}

	@Test
	void anUnexplainedHurtFromSomebodyLookingElsewhereIsNotPinnedOnThem() {
		put("Victim", 0, 0, 0);
		put("Fighter", 0, 4.4, 0);
		steps(6);
		watch.swing("Fighter", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.UNKNOWN, null);
		steps(8);

		assertThat(seen).isEmpty();
	}

	@Test
	void hittingSomebodyBehindYouIsKillAura() {
		put("Victim", 0, 0, 0);
		// Looking straight away from the victim.
		put("Cheater", 0, 2.5, 0);
		steps(6);
		watch.swing("Cheater", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
		step();

		assertThat(checks()).containsExactly(Check.KILLAURA);
	}

	// --- knockback -----------------------------------------------------------------------------------

	private void hitVictimFromTheNorth() {
		put("Victim", 0, 0, 180);
		put("Attacker", 0, 2.5, 180);
		steps(6);
		watch.swing("Attacker", tick + 1);
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.MELEE, null);
	}

	@Test
	void notMovingAtAllWhenHitIsAntiKnockback() {
		hitVictimFromTheNorth();
		steps(8);
		assertThat(checks()).containsExactly(Check.VELOCITY);
		assertThat(seen.getFirst().player()).isEqualTo("Victim");
	}

	@Test
	void takingTheKnockbackIsFine() {
		hitVictimFromTheNorth();
		Pose victim = poses.get("Victim");
		double[] rise = {0.4, 0.72, 0.96, 1.12, 1.2, 1.2, 1.12, 0.96};
		for (int i = 0; i < 8; i++) {
			victim.z -= 0.35 * Math.pow(0.91, i);
			victim.y = rise[i];
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void aWallBehindAndACeilingAboveTakeTheKnockback() {
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 1; y++) {
				blocks.add(List.of(x, y, -1));
			}
			for (int z = -1; z <= 1; z++) {
				blocks.add(List.of(x, 2, z));
			}
		}
		hitVictimFromTheNorth();
		steps(8);
		assertThat(seen).isEmpty();
	}

	// --- blocks --------------------------------------------------------------------------------------

	@Test
	void aBlockPlacedWhereTheyAreNotLookingIsScaffold() {
		// Facing forward but the block lands off to the side of the look ray - within the wide 60°
		// attribution cone (so they are plausibly the one who placed it) yet clearly missing even the
		// widened lookedAt box: a real scaffold placed without properly aiming.
		put("Cheater", 0.5, 0.5, 0);
		steps(4);
		watch.swing("Cheater", tick + 1);
		watch.placed(new BedDefense.Cell(2, 1, 4), tick + 1, false);
		step();
		steps(15); // let the deferred judgement resolve

		assertThat(checks()).containsExactly(Check.SCAFFOLD);
	}

	@Test
	void aBlockPlacedWhereTheyLookIsBridging() {
		Pose player = put("Legit", 0.5, 0.5, 0);
		player.yaw = 180;
		player.pitch = 78;
		steps(4);
		watch.swing("Legit", tick + 1);
		watch.placed(new BedDefense.Cell(0, -1, -1), tick + 1, false);
		step();
		steps(15);

		assertThat(seen).isEmpty();
	}

	@Test
	void aLookArrivingAfterThePlacementStillCountsAsBridging() {
		// A remote player's rotation packet arrives later than the block update: the pitch that would
		// prove they were looking down at the bridge only lands two ticks after the placement.
		Pose player = put("Legit", 0.5, 0.5, 0);
		player.yaw = 180;
		steps(4);
		watch.swing("Legit", tick + 1);
		watch.placed(new BedDefense.Cell(0, -1, -1), tick + 1, false);
		step(); // the placement tick - still not looking down yet
		player.pitch = 78;
		steps(2); // the rotation catches up
		steps(15);

		assertThat(seen).isEmpty();
	}

	@Test
	void aGlanceJustBeyondTheOldToleranceStillCountsAsLookingAtTheBlock() {
		// Looking straight ahead, the ray only grazes the cell once its box is inflated by half a
		// block rather than the old 0.35 - a rotation-quantisation near miss, not a real scaffold.
		put("Legit", 5, 1.4, 90);
		steps(4);
		watch.swing("Legit", tick + 1);
		watch.placed(new BedDefense.Cell(0, 1, 0), tick + 1, false);
		step();
		steps(15);

		assertThat(seen).isEmpty();
	}

	@Test
	void aSteadyLineOfBlocksBehindALegitLookingCheaterIsStillScaffold() {
		// A real scaffold hack: one block every couple of ticks, always right behind their own feet as
		// they walk backwards, looking forward the whole time - never at the blocks. Spread out (not a
		// same-tick or same-spot burst) so the structure filter must not swallow it either.
		Pose cheater = put("Cheater", 0.5, 4.5, 0);
		steps(4);
		for (int i = 0; i < 4; i++) {
			int cellZ = (int) Math.floor(cheater.z) - 1;
			watch.swing("Cheater", tick + 1);
			watch.placed(new BedDefense.Cell(0, -1, cellZ), tick + 1, false);
			steps(2);
			cheater.z -= 1.0;
		}
		steps(15);

		assertThat(seen).anyMatch(v -> v.check() == Check.SCAFFOLD && v.detail().contains("not looking"));
	}

	@Test
	void aBridgeEggIsNobodysScaffold() {
		put("Legit", 0.5, 0.5, 0);
		steps(4);
		watch.swing("Legit", tick + 1);
		watch.placed(new BedDefense.Cell(0, -1, -1), tick + 1, true);
		step();

		assertThat(seen).isEmpty();
	}

	@Test
	void aPopUpTowerIsNobodysScaffold() {
		put("Legit", 0.5, 0.5, 0);
		steps(4);
		watch.swing("Legit", tick + 1);
		for (int y = 0; y < 6; y++) {
			watch.placed(new BedDefense.Cell(2, y, 2), tick + 1, false);
		}
		step();

		assertThat(seen).isEmpty();
	}

	@Test
	void aPopUpTowerRisingOneBlockATickIsNobodysScaffoldOrFastPlace() {
		// A Hypixel pop-up tower or a bridge egg's line: a 3x3 ring plus a short ladder column, one
		// block landing a tick, over more than a second. Nobody aims each of the ~10 blocks as they
		// appear, and no single tick has more than one — the old same-tick structure filter alone would
		// miss this; the burst filter (spread over the window, many distinct cells nearby) must catch
		// it instead, for both Scaffold and FastPlace.
		put("Bystander", 4.5, 1.0, 180);
		steps(4);
		int[][] ring = {
				{3, 3}, {4, 3}, {5, 3},
				{3, 4}, {5, 4},
				{3, 5}, {4, 5}, {5, 5},
		};
		for (int[] xz : ring) {
			watch.swing("Bystander", tick + 1);
			watch.placed(new BedDefense.Cell(xz[0], 0, xz[1]), tick + 1, false);
			step();
		}
		for (int y = 1; y <= 2; y++) {
			watch.swing("Bystander", tick + 1);
			watch.placed(new BedDefense.Cell(4, y, 4), tick + 1, false);
			step();
		}
		steps(20);

		assertThat(seen).isEmpty();
	}

	/** Looking down at the bridge, walking backwards away from where they look. */
	private void bridgeBackwards(double speed) {
		Pose bridger = put("Bridger", 0.5, 0.5, 0);
		bridger.pitch = 80;
		bridger.held = Frame.Held.BLOCK;
		steps(3);
		for (int i = 0; i < 20; i++) {
			bridger.z -= speed;
			watch.swing("Bridger", tick + 1);
			step();
		}
	}

	@Test
	void bridgingBackwardsFasterThanLegsCanIsScaffold() {
		bridgeBackwards(0.3);
		assertThat(seen).anyMatch(v -> v.check() == Check.SCAFFOLD && v.detail().contains("backwards"));
	}

	@Test
	void ordinaryBackwardsBridgingIsFine() {
		bridgeBackwards(0.15);
		assertThat(seen).isEmpty();
	}

	@Test
	void placingFasterThanAnyoneCanClickIsFastPlace() {
		// FastPlace is judged the same way Scaffold now is - only once each placement's window has
		// resolved - so the deque of confirmed placements needs the extra ticks below to build up past
		// the limit.
		Pose cheater = put("Cheater", 0.5, 0.5, 0);
		cheater.yaw = 180;
		cheater.pitch = 78;
		steps(4);
		for (int i = 0; i < 20; i++) {
			watch.swing("Cheater", tick + 1);
			watch.placed(new BedDefense.Cell(0, -1, -1), tick + 1, false);
			step();
		}
		steps(20);
		assertThat(checks()).contains(Check.FASTPLACE).doesNotContain(Check.SCAFFOLD);
	}

	/** A bed along x at (0,0,0)-(1,0,0), wrapped in a shell of blocks. */
	private List<BedDefense.Cell> wrappedBed() {
		for (int x = -1; x <= 2; x++) {
			for (int y = 0; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					blocks.add(List.of(x, y, z));
				}
			}
		}
		return List.of(new BedDefense.Cell(0, 0, 0), new BedDefense.Cell(1, 0, 0));
	}

	private void breakBed(List<BedDefense.Cell> bed) {
		for (BedDefense.Cell cell : bed) {
			blocks.remove(List.of(cell.x(), cell.y(), cell.z()));
			watch.bedBroken(cell, tick + 1);
		}
	}

	@Test
	void aBedBrokenThroughItsDefenceIsANuker() {
		List<BedDefense.Cell> bed = wrappedBed();
		put("Cheater", 0.5, 4.5, 180);
		steps(4);
		breakBed(bed);
		step();

		assertThat(checks()).containsExactly(Check.NUKER);
		assertThat(seen.getFirst().player()).isEqualTo("Cheater");
	}

	@Test
	void aBedBrokenFromTheTunnelDugToItIsFine() {
		List<BedDefense.Cell> bed = wrappedBed();
		blocks.remove(List.of(0, 0, 1));
		blocks.remove(List.of(0, 1, 1));
		put("Legit", 0.5, 2.5, 180);
		steps(4);
		breakBed(bed);
		step();

		assertThat(seen).isEmpty();
	}

	@Test
	void aBedBlownUpIsNobodysNuker() {
		List<BedDefense.Cell> bed = wrappedBed();
		put("Legit", 0.5, 4.5, 180);
		steps(4);
		watch.explosion(new Vec(0.5, 1, 3), tick);
		steps(3);
		breakBed(bed);
		step();

		assertThat(seen).isEmpty();
	}

	// --- movement ------------------------------------------------------------------------------------

	@Test
	void runningTwiceAsFastAsASpeedPotionIsSpeed() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.sprinting = true;
		for (int i = 0; i < 30; i++) {
			cheater.z += 0.8;
			step();
		}
		assertThat(checks()).contains(Check.SPEED);
	}

	@Test
	void sprintJumpingWithSpeedTwoIsFine() {
		Pose player = put("Legit", 0, 0, 0);
		player.sprinting = true;
		for (int i = 0; i < 60; i++) {
			player.z += 0.45;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void aFireballJumpIsNotSpeed() {
		Pose player = put("Legit", 0, 0, 0);
		steps(2);
		watch.explosion(new Vec(0, 0, -1), tick);
		for (int i = 0; i < 30; i++) {
			player.z += 1.2;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void hangingInTheAirWhileMovingIsFly() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.y = 5;
		cheater.flying = true;
		for (int i = 0; i < 40; i++) {
			cheater.z += 0.2;
			step();
		}
		assertThat(checks()).contains(Check.FLY);
	}

	@Test
	void fallingIsFine() {
		Pose player = put("Legit", 0, 0, 0);
		player.y = 60;
		player.flying = true;
		double dy = 0;
		for (int i = 0; i < 40; i++) {
			dy = (dy - 0.08) * 0.98;
			player.y += dy;
			player.z += 0.1;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void iAmNeverASuspect() {
		Pose me = put(SELF, 0, 0, 0);
		me.sprinting = true;
		for (int i = 0; i < 30; i++) {
			me.z += 0.8;
			step();
		}
		assertThat(seen).isEmpty();
	}

	@Test
	void aNewRoundForgetsEveryone() {
		Pose cheater = put("Cheater", 0, 0, 0);
		cheater.using = true;
		steps(6);
		watch.clear();
		watch.swing("Cheater", tick + 1);
		steps(3);

		assertThat(seen).isEmpty();
	}

	// --- autoclicker (Iustitia ClickStatisticsCheck.kt) -----------------------------------------------

	@Test
	void moreThanTwentySwingsASecondIsAutoClicker() {
		put("Cheater", 0, 0, 0);
		steps(4);
		for (int i = 0; i < 25; i++) {
			watch.swing("Cheater", tick + 1, (tick + 1) * 50_000_000L);
			step();
		}
		assertThat(checks()).contains(Check.AUTOCLICKER);
	}

	@Test
	void clickingEveryFewTicksIsFine() {
		put("Legit", 0, 0, 0);
		steps(4);
		for (int i = 0; i < 25; i++) {
			if (i % 3 == 0) {
				watch.swing("Legit", tick + 1, (tick + 1) * 50_000_000L);
			}
			step();
		}
		assertThat(checks()).doesNotContain(Check.AUTOCLICKER);
	}

	@Test
	void aSubTenMillisecondDoubleClickIsAutoClicker() {
		put("Cheater", 0, 0, 0);
		steps(4);
		watch.swing("Cheater", tick + 1, 1_000_000_000L);
		step();
		watch.swing("Cheater", tick + 1, 1_000_000_000L + 40_000_000L);
		step();
		watch.swing("Cheater", tick + 1, 1_000_000_000L + 45_000_000L);
		step();
		assertThat(checks()).contains(Check.AUTOCLICKER);
	}

	@Test
	void sameTickLagBatchedSwingsAreNotAutoClicker() {
		put("Legit", 0, 0, 0);
		steps(4);
		watch.swing("Legit", tick + 1, 1_000_000_000L);
		watch.swing("Legit", tick + 1, 1_000_000_000L + 1_000_000L);
		steps(3);
		assertThat(checks()).doesNotContain(Check.AUTOCLICKER);
	}

	@Test
	void perfectlyEvenClickIntervalsAreAutoClicker() {
		put("Cheater", 0, 0, 0);
		steps(4);
		// One swing every two ticks, well under the CPS cap, but a perfectly constant interval —
		// stDev and kurtosis both catch a fixed-delay autoclicker that CPS alone would miss.
		for (int i = 0; i < 45; i++) {
			watch.swing("Cheater", tick + 1, (tick + 1) * 50_000_000L);
			steps(2);
		}
		assertThat(checks()).contains(Check.AUTOCLICKER);
	}

	@Test
	void humanJitterInClickIntervalsIsFine() {
		put("Legit", 0, 0, 0);
		steps(4);
		int[] pattern = {4, 5, 4, 4, 5, 9, 4, 5, 4, 4, 5, 12, 4, 4, 5, 4, 9, 5, 4, 4};
		for (int i = 0; i < 50; i++) {
			watch.swing("Legit", tick + 1, (tick + 1) * 50_000_000L);
			steps(pattern[i % pattern.length]);
		}
		assertThat(checks()).doesNotContain(Check.AUTOCLICKER);
	}

	// --- aimsnap (Iustitia AimWrapCheck.kt) ------------------------------------------------------------

	@Test
	void repeatedSnapsOutOfAStillAimAreAimSnap() {
		Pose cheater = put("Cheater", 0, 0, 0);
		steps(3);
		for (int i = 0; i < 5; i++) {
			cheater.yaw = (i % 2 == 0) ? 175 : -5;
			step();
			steps(3);
		}
		assertThat(checks()).contains(Check.AIMSNAP);
	}

	@Test
	void aBoundaryCrossingTurnIsNotAimSnap() {
		Pose player = put("Legit", 0, 0, 179);
		steps(3);
		for (int i = 0; i < 20; i++) {
			// 179 -> -179 is a real turn of about 2 degrees, not 358.
			player.yaw = player.yaw > 0 ? -179 : 179;
			step();
		}
		assertThat(checks()).doesNotContain(Check.AIMSNAP);
	}

	@Test
	void aContinuousFastTurnIsNotAimSnap() {
		Pose player = put("Legit", 0, 0, 0);
		steps(3);
		for (int i = 0; i < 30; i++) {
			player.yaw += 40;
			if (player.yaw > 180) {
				player.yaw -= 360;
			}
			step();
		}
		assertThat(checks()).doesNotContain(Check.AIMSNAP);
	}

	// --- aimtrack (Iustitia RotationTrackingCheck.kt) --------------------------------------------------

	@Test
	void aimAlwaysLockedOnANearbyTargetDuringCombatIsAimTrack() {
		Pose cheater = put("Cheater", 0, 0, 0);
		put("Victim", 4, 0, 0);
		// yaw/pitch set exactly to the bearing from the attacker's eye to the victim's body centre,
		// computed the same way the check does: atan2(-cx, cz) / atan2(-cy, horiz).
		cheater.yaw = Math.toDegrees(Math.atan2(-4, 0));
		cheater.pitch = Math.toDegrees(Math.atan2(-(0 + 0.9 - 1.62), 4));
		steps(2);
		watch.attack("Cheater", "Victim", tick + 1);
		steps(65);
		assertThat(checks()).contains(Check.AIMTRACK);
	}

	@Test
	void aPerfectLockWithoutEverAttackingIsNotAimTrack() {
		Pose cheater = put("Legit", 0, 0, 0);
		put("Victim", 4, 0, 0);
		cheater.yaw = Math.toDegrees(Math.atan2(-4, 0));
		cheater.pitch = Math.toDegrees(Math.atan2(-(0 + 0.9 - 1.62), 4));
		steps(65);
		assertThat(checks()).doesNotContain(Check.AIMTRACK);
	}

	// --- triggerbot (Iustitia TriggerbotCheck.kt) ------------------------------------------------------

	private void triggerbotCycle(double onYaw, double onPitch) {
		Pose cheater = poses.get("Cheater");
		cheater.yaw = 90;
		cheater.pitch = 0;
		steps(4);
		cheater.yaw = onYaw;
		cheater.pitch = onPitch;
		step();
		watch.attack("Cheater", "Victim", tick + 1);
		step();
	}

	@Test
	void handsFreeInstantHitsOnAimingOnTargetAreTriggerbot() {
		Pose cheater = put("Cheater", 0, 0, 90);
		put("Victim", 2, 0, 0);
		double onYaw = Math.toDegrees(Math.atan2(-2, 0));
		double onPitch = Math.toDegrees(Math.atan2(-(0 + 0.9 - 1.62), 2));
		for (int i = 0; i < 5; i++) {
			triggerbotCycle(onYaw, onPitch);
		}
		assertThat(checks()).contains(Check.TRIGGERBOT);
	}

	@Test
	void hittingATargetThatWalksIntoAHeldCrosshairIsNotTriggerbot() {
		double onYaw = Math.toDegrees(Math.atan2(-2, 0));
		double onPitch = Math.toDegrees(Math.atan2(-(0 + 0.9 - 1.62), 2));
		Pose cheater = put("Legit", 0, 0, 0);
		cheater.yaw = onYaw;
		cheater.pitch = onPitch;
		Pose victim = put("Victim", 10, 0, 0);
		steps(5);
		for (int i = 0; i < 5; i++) {
			victim.x = 2;
			steps(2);
			watch.attack("Legit", "Victim", tick + 1);
			step();
			victim.x = 10;
			steps(3);
		}
		assertThat(checks()).doesNotContain(Check.TRIGGERBOT);
	}

	// --- hitflick (Iustitia HitFlickCheck.kt) ----------------------------------------------------------

	@Test
	void flickingOffTheTargetAtEveryHitAndSnappingBackIsHitFlick() {
		Pose cheater = put("Cheater", 0, -3, 0);
		put("Victim", 0, 0, 0);
		steps(4);
		for (int i = 0; i < 5; i++) {
			cheater.yaw = 70;
			watch.attack("Cheater", "Victim", tick + 1);
			step();
			cheater.yaw = 0;
			step();
		}
		assertThat(checks()).contains(Check.HITFLICK);
	}

	@Test
	void alwaysFacingTheTargetAtTheHitIsNotHitFlick() {
		Pose cheater = put("Legit", 0, -3, 0);
		put("Victim", 0, 0, 0);
		steps(4);
		for (int i = 0; i < 5; i++) {
			watch.attack("Legit", "Victim", tick + 1);
			steps(2);
		}
		assertThat(checks()).doesNotContain(Check.HITFLICK);
	}

	// --- multiaura 2-tick union window (Iustitia MultiTargetCheck.kt) ---------------------------------

	@Test
	void threeVictimsAcrossTwoTicksIsMultiAura() {
		put("Cheater", 0, 0, 0);
		put("VictimA", 1, 0, 0);
		put("VictimB", -1, 0, 0);
		put("VictimC", 0, 1, 0);
		steps(4);
		watch.attack("Cheater", "VictimA", tick + 1);
		watch.attack("Cheater", "VictimB", tick + 1);
		step();
		watch.attack("Cheater", "VictimC", tick + 1);
		step();
		assertThat(checks()).contains(Check.MULTIAURA);
	}

	@Test
	void twoSeparateSingleHitsTenTicksApartAreNotMultiAura() {
		put("Cheater", 0, 0, 0);
		put("VictimA", 1, 0, 0);
		put("VictimB", -1, 0, 0);
		steps(4);
		watch.attack("Cheater", "VictimA", tick + 1);
		steps(10);
		watch.attack("Cheater", "VictimB", tick + 1);
		steps(4);
		assertThat(checks()).doesNotContain(Check.MULTIAURA);
	}

	// --- nofall (Iustitia NoFallDamageCheck.kt) --------------------------------------------------------

	@Test
	void fallingFarWithNoHurtIsNoFall() {
		Pose victim = put("Victim", 0, 0, 0);
		victim.y = 10;
		victim.flying = true;
		steps(2);
		for (int i = 0; i < 7; i++) {
			victim.y -= 1.4;
			step();
		}
		victim.y = 0;
		victim.flying = false;
		step();
		assertThat(checks()).contains(Check.NOFALL);
	}

	@Test
	void fallingFarAndTakingTheHurtIsFine() {
		Pose victim = put("Victim", 0, 0, 0);
		victim.y = 10;
		victim.flying = true;
		steps(2);
		for (int i = 0; i < 7; i++) {
			victim.y -= 1.4;
			step();
		}
		victim.y = 0;
		victim.flying = false;
		watch.hurt("Victim", tick + 1, CheatWatch.Hit.OTHER, null);
		step();
		assertThat(checks()).doesNotContain(Check.NOFALL);
	}

	// --- step (Iustitia StepHeightCheck.kt) ------------------------------------------------------------

	@Test
	void steppingUpMoreThanAJumpInOneTickIsStep() {
		put("Cheater", 0, 0, 0);
		steps(3);
		blocks.add(List.of(0, 0, 0));
		Pose cheater = poses.get("Cheater");
		cheater.y = 1.0;
		step();
		assertThat(checks()).contains(Check.STEP);
	}

	@Test
	void aSmallLedgeIsFine() {
		put("Legit", 0, 0, 0);
		steps(3);
		blocks.add(List.of(0, 0, 0));
		Pose player = poses.get("Legit");
		player.y = 0.5;
		step();
		assertThat(checks()).doesNotContain(Check.STEP);
	}

	// --- blink (Iustitia PacketGapCheck.kt) ------------------------------------------------------------

	@Test
	void freezingThenSnappingIsBlink() {
		put("Cheater", 0, 0, 0);
		steps(6);
		Pose cheater = poses.get("Cheater");
		cheater.z += 5;
		step();
		assertThat(checks()).contains(Check.BLINK);
	}

	@Test
	void aFreezeAndSnapDuringAServerHitchIsNotBlink() {
		put("Legit", 0, 0, 0);
		steps(6);
		Pose player = poses.get("Legit");
		watch.serverLag(tick + 1);
		player.z += 5;
		step();
		assertThat(checks()).doesNotContain(Check.BLINK);
	}

	// --- criticals (Iustitia CriticalsCheck.kt) --------------------------------------------------------

	@Test
	void aFixedFallArcPhaseOnEveryHitIsCriticals() {
		put("Cheater", 0, -3, 0);
		put("Victim", 0, 0, 0);
		Pose cheater = poses.get("Cheater");
		for (int i = 0; i < 9; i++) {
			cheater.y = 0;
			step();
			cheater.y = 0.1;
			step();
			watch.attack("Cheater", "Victim", tick + 1);
			step();
		}
		assertThat(checks()).contains(Check.CRITICALS);
	}

	@Test
	void varyingJumpTimingOnEveryHitIsFine() {
		put("Legit", 0, -3, 0);
		put("Victim", 0, 0, 0);
		Pose player = poses.get("Legit");
		double[] rises = {0.05, 0.25, 0.12, 0.28, 0.08, 0.2, 0.15, 0.03};
		for (double rise : rises) {
			player.y = 0;
			step();
			player.y = rise;
			step();
			watch.attack("Legit", "Victim", tick + 1);
			step();
		}
		assertThat(checks()).doesNotContain(Check.CRITICALS);
	}

	// --- cooldown vs. Suspicion's fade: can every check realistically reach a flag? --------------------

	/**
	 * {@link Suspicion} fades a point every {@link Suspicion#TICKS_PER_POINT} ticks and flags at
	 * {@link Suspicion#FLAG_AT}. A check whose weight cannot outpace that fade at its own minimum
	 * re-trigger spacing ({@link Check#cooldownTicks()}, or one tick where there is none) could sight
	 * a cheat forever and never flag them — this asserts every check clears the bar within a bounded,
	 * realistic number of sightings.
	 */
	@Test
	void everyCheckCanRealisticallyReachAFlag() {
		for (Check check : Check.values()) {
			Suspicion suspicion = new Suspicion();
			long spacing = Math.max(1, check.cooldownTicks());
			long tick = 0;
			boolean flagged = false;
			for (int i = 0; i < 200 && !flagged; i++) {
				tick += spacing;
				flagged = suspicion.record(new Violation("Cheater", check, "sighting", tick), 1.0).isPresent();
			}
			assertThat(flagged).as("%s should flag within 200 sightings spaced %d ticks apart", check, spacing).isTrue();
		}
	}
}
