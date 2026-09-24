package de.raindancer118.hypixelscout.cheat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuspicionTest {
	private final Suspicion suspicion = new Suspicion();

	private static Violation reach(long tick) {
		return new Violation("Cheater", Check.REACH, "4.1 blocks", tick);
	}

	@Test
	void oneViolationIsNoFlag() {
		assertThat(suspicion.record(reach(1), 1.0)).isEmpty();
		assertThat(suspicion.flags("Cheater")).isEmpty();
		assertThat(suspicion.count("Cheater", Check.REACH)).isEqualTo(1);
	}

	@Test
	void enoughOfThemInAShortTimeFlagOnceAndTheFlagStays() {
		int announced = 0;
		for (int i = 0; i < 8; i++) {
			if (suspicion.record(reach(i * 20L), 1.0).isPresent()) {
				announced++;
			}
		}
		assertThat(announced).isEqualTo(1);
		Suspicion.Flag flag = suspicion.flags("Cheater").getFirst();
		assertThat(flag.check()).isEqualTo(Check.REACH);
		assertThat(flag.count()).isEqualTo(8);
		assertThat(flag.detail()).isEqualTo("4.1 blocks");
	}

	@Test
	void rareOnesFadeAwayBeforeTheyAddUp() {
		for (int i = 0; i < 10; i++) {
			assertThat(suspicion.record(reach(i * 20L * 60), 1.0)).isEmpty();
		}
		assertThat(suspicion.flags("Cheater")).isEmpty();
	}

	@Test
	void aHigherSensitivityFlagsSooner() {
		assertThat(suspicion.record(reach(0), 2.0)).isEmpty();
		assertThat(suspicion.record(reach(1), 2.0)).isPresent();
	}

	@Test
	void aNukerIsFlaggedAtOnce() {
		assertThat(suspicion.record(new Violation("Cheater", Check.NUKER, "Red bed", 5), 1.0)).isPresent();
	}

	@Test
	void confidenceGrowsWithEverySightingInAShortTime() {
		assertThat(suspicion.confidence("Cheater")).isZero();
		suspicion.record(reach(0), 1.0);
		double one = suspicion.confidence("Cheater");
		for (int i = 1; i < 4; i++) {
			suspicion.record(reach(i * 20L), 1.0);
		}
		double four = suspicion.confidence("Cheater");

		assertThat(one).isCloseTo(Check.REACH.sureness(), org.assertj.core.data.Offset.offset(0.001));
		assertThat(four).isGreaterThan(one).isLessThan(1.0);
		assertThat(suspicion.flags("Cheater").getFirst().confidence()).isEqualTo(four);
	}

	@Test
	void rareSightingsOverALongGameStayUnsure() {
		for (int i = 0; i < 20; i++) {
			suspicion.record(reach(i * 20L * 60), 1.0);
		}
		assertThat(suspicion.confidence("Cheater")).isLessThan(0.5);
	}

	@Test
	void differentChecksOnOnePlayerAddUp() {
		suspicion.record(reach(0), 1.0);
		double reachOnly = suspicion.confidence("Cheater");
		suspicion.record(new Violation("Cheater", Check.FLY, "", 1), 1.0);

		assertThat(suspicion.confidence("Cheater")).isGreaterThan(reachOnly)
				.isGreaterThan(Check.FLY.sureness());
		assertThat(suspicion.confidence("Somebody")).isZero();
	}

	@Test
	void aBedNukerAloneIsAlreadyFairlySure() {
		suspicion.record(new Violation("Cheater", Check.NUKER, "", 1), 1.0);
		assertThat(suspicion.confidence("Cheater")).isGreaterThanOrEqualTo(0.75);
	}

	@Test
	void legitBehaviourTakesSightingsBack() {
		for (int i = 0; i < 3; i++) {
			suspicion.record(reach(i), 1.0);
		}
		double before = suspicion.confidence("Cheater");
		for (int i = 0; i < 3; i++) {
			suspicion.record(Violation.relief("Cheater", Check.REACH, 3 + i), 1.0);
		}
		// Three sightings would have flagged with a fourth; after the reliefs a fourth does not.
		assertThat(suspicion.record(reach(10), 1.0)).isEmpty();
		assertThat(suspicion.confidence("Cheater")).isLessThan(before);
		assertThat(suspicion.count("Cheater", Check.REACH)).isEqualTo(4);
	}

	@Test
	void aReliefForSomebodyNeverSeenIsNothing() {
		assertThat(suspicion.record(Violation.relief("Nobody", Check.REACH, 1), 1.0)).isEmpty();
		assertThat(suspicion.confidence("Nobody")).isZero();
		assertThat(suspicion.count("Nobody", Check.REACH)).isZero();
	}

	@Test
	void aCheckSwitchedOffIsForgottenWithItsFlags() {
		suspicion.record(new Violation("Cheater", Check.NUKER, "", 1), 1.0);
		suspicion.record(reach(2), 1.0);
		suspicion.forget(Check.NUKER);

		assertThat(suspicion.flags("Cheater")).isEmpty();
		assertThat(suspicion.count("Cheater", Check.NUKER)).isZero();
		assertThat(suspicion.confidence("Cheater")).isCloseTo(Check.REACH.sureness(), org.assertj.core.data.Offset.offset(0.001));
	}

	@Test
	void aWrongFlagIsClearedForThatPlayerAlone() {
		for (int i = 0; i < 4; i++) {
			suspicion.record(reach(i), 1.0);
			suspicion.record(new Violation("Other", Check.REACH, "", i), 1.0);
		}
		suspicion.record(new Violation("Cheater", Check.NUKER, "", 5), 1.0);
		suspicion.forget("Cheater", Check.REACH);

		assertThat(suspicion.flags("Cheater")).extracting(Suspicion.Flag::check).containsExactly(Check.NUKER);
		assertThat(suspicion.flags("Other")).extracting(Suspicion.Flag::check).containsExactly(Check.REACH);

		suspicion.forget("Cheater", null);
		assertThat(suspicion.flags("Cheater")).isEmpty();
		assertThat(suspicion.confidence("Cheater")).isZero();
	}

	@Test
	void everyFlagOfTheRoundAndAClearSlate() {
		suspicion.record(new Violation("A", Check.NUKER, "", 1), 1.0);
		suspicion.record(new Violation("B", Check.NUKER, "", 1), 1.0);
		assertThat(suspicion.flagged()).extracting(Suspicion.Flag::player).containsExactlyInAnyOrder("A", "B");

		suspicion.clear();
		assertThat(suspicion.flagged()).isEmpty();
	}
}
