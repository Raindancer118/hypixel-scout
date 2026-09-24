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
		tick++;
		for (Map.Entry<String, Pose> entry : poses.entrySet()) {
			Pose p = entry.getValue();
			Vec feet = new Vec(p.x, p.y, p.z);
			boolean supported = p.y <= 0.001 || terrain.solid((int) Math.floor(p.x), (int) Math.floor(p.y - 0.1), (int) Math.floor(p.z));
			watch.frame(entry.getKey(), new Frame(tick, feet, feet.add(new Vec(0, 1.62, 0)),
					new Box(p.x - 0.3, p.y, p.z - 0.3, p.x + 0.3, p.y + 1.8, p.z + 0.3), p.yaw, p.pitch,
					supported && !p.flying, supported && !p.flying, p.sprinting, p.using, false, false, p.sneaking, p.held));
		}
		for (Violation violation : watch.endTick(tick, terrain)) {
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
		for (int hit = 0; hit < 3; hit++) {
			watch.swing("Cheater", tick + 1);
			watch.attack("Cheater", "Victim", tick + 1);
			steps(12);
		}

		assertThat(seen).anyMatch(v -> v.check() == Check.KILLAURA && v.detail().contains("wall"));
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
		// Walking backwards along -z, looking straight ahead at the horizon, blocks appear behind.
		put("Cheater", 0.5, 0.5, 0);
		steps(4);
		watch.swing("Cheater", tick + 1);
		watch.placed(new BedDefense.Cell(0, -1, -1), tick + 1, false);
		step();

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

		assertThat(seen).isEmpty();
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
		Pose cheater = put("Cheater", 0.5, 0.5, 0);
		cheater.yaw = 180;
		cheater.pitch = 78;
		steps(4);
		for (int i = 0; i < 20; i++) {
			watch.swing("Cheater", tick + 1);
			watch.placed(new BedDefense.Cell(0, -1, -1), tick + 1, false);
			step();
		}
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
}
