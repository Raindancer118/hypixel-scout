package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BedLedgerTest {
	private static final BedDefense.Report WOOL = new BedDefense.Report(false,
			List.of(new BedDefense.Material("Wool", 0.8, 5)), new BedDefense.Cell(0, 2, 0));
	private static final BedDefense.Report OPEN = new BedDefense.Report(true, List.of(), null);
	private static final BedDefense.Cell HEAD = new BedDefense.Cell(10, 64, -3);

	private long now = 1_000;
	private final BedLedger ledger = new BedLedger(() -> now);

	@Test
	void aBedNeverLookedAtIsUnknown() {
		assertThat(ledger.of("Red")).isEmpty();
		assertThat(ledger.entries()).isEmpty();
	}

	@Test
	void theLastLookIsKeptUnderTheTeamItBelongsTo() {
		ledger.record("red", HEAD, WOOL);
		now += 5_000;
		ledger.record("red", HEAD, OPEN);

		BedLedger.Entry red = ledger.of("Red").orElseThrow();
		assertThat(red.report()).isEqualTo(OPEN);
		assertThat(red.head()).isEqualTo(HEAD);
		assertThat(red.gone()).isFalse();
		assertThat(red.ageMillis(now + 2_000)).isEqualTo(2_000);
	}

	@Test
	void bedColoursAreTheTeamsHypixelNamesThemBy() {
		assertThat(BedLedger.teamOf("red")).isEqualTo("Red");
		assertThat(BedLedger.teamOf("blue")).isEqualTo("Blue");
		assertThat(BedLedger.teamOf("lime")).isEqualTo("Green");
		assertThat(BedLedger.teamOf("green")).isEqualTo("Green");
		assertThat(BedLedger.teamOf("yellow")).isEqualTo("Yellow");
		assertThat(BedLedger.teamOf("light_blue")).isEqualTo("Aqua");
		assertThat(BedLedger.teamOf("cyan")).isEqualTo("Aqua");
		assertThat(BedLedger.teamOf("pink")).isEqualTo("Pink");
		assertThat(BedLedger.teamOf("magenta")).isEqualTo("Pink");
		assertThat(BedLedger.teamOf("gray")).isEqualTo("Gray");
		assertThat(BedLedger.teamOf("light_gray")).isEqualTo("Gray");
		assertThat(BedLedger.teamOf("white")).isEqualTo("White");
		assertThat(BedLedger.teamOf("LIGHT BLUE")).isEqualTo("Aqua");
	}

	@Test
	void aBedThatIsGoneKeepsItsLastDefenceAndStaysGoneUntilSeenAgain() {
		ledger.record("red", HEAD, WOOL);
		now += 1_000;
		ledger.markGone("Red");

		BedLedger.Entry red = ledger.of("Red").orElseThrow();
		assertThat(red.gone()).isTrue();
		assertThat(red.report()).isEqualTo(WOOL);
		assertThat(red.goneAt()).isEqualTo(now);

		ledger.markGone("Red");
		assertThat(ledger.of("Red").orElseThrow().goneAt()).isEqualTo(now);

		ledger.record("red", HEAD, OPEN);
		assertThat(ledger.of("Red").orElseThrow().gone()).isFalse();
	}

	@Test
	void markingABedNeverSeenChangesNothing() {
		ledger.markGone("Blue");
		assertThat(ledger.of("Blue")).isEmpty();
	}

	@Test
	void standingBedsAreTheOnesStillToWatch() {
		ledger.record("red", HEAD, WOOL);
		ledger.record("blue", new BedDefense.Cell(-10, 64, 3), OPEN);
		ledger.markGone("Blue");

		assertThat(ledger.standing()).extracting(BedLedger.Entry::team).containsExactly("Red");
		assertThat(ledger.entries()).extracting(BedLedger.Entry::team).containsExactly("Red", "Blue");
	}

	@Test
	void aNewRoundForgetsEverything() {
		ledger.record("red", HEAD, WOOL);
		ledger.clear();
		assertThat(ledger.entries()).isEmpty();
	}

	@Test
	void aNewScanReplacesBlocksThatHaveBeenRemoved() {
		List<BedDefense.Cell> bed = List.of(HEAD, new BedDefense.Cell(11, 64, -3));
		BedDefense.Cell top = new BedDefense.Cell(10, 66, -3);
		BedDefense.Cell inner = new BedDefense.Cell(10, 65, -3);
		ledger.record("red", bed, new BedDefense.Report(false, List.of(), null,
				java.util.Map.of(top, new BedDefense.Block("End Stone", 3.0))));
		ledger.record("red", bed, new BedDefense.Report(false, List.of(), null,
				java.util.Map.of(inner, new BedDefense.Block("Wool", 0.8))));

		BedLedger.Entry red = ledger.of("Red").orElseThrow();
		assertThat(red.seen()).containsKey(inner).doesNotContainKey(top);
		assertThat(red.layers()).extracting(BedDefense.Layer::material).containsExactly("Wool");
	}

	@Test
	void aBlockWatchedGoingUpJoinsTheDefenceOfABedOnRecord() {
		List<BedDefense.Cell> bed = List.of(HEAD, new BedDefense.Cell(11, 64, -3));
		ledger.record("red", bed, WOOL);
		BedDefense.Cell placed = new BedDefense.Cell(10, 65, -2);
		ledger.watched(bed, "red", placed, new BedDefense.Block("End Stone", 3.0));

		assertThat(ledger.of("Red").orElseThrow().seen()).containsEntry(placed, new BedDefense.Block("End Stone", 3.0));
	}

	@Test
	void aBlockWatchedGoingUpAroundABedNotLookedAtYetStartsItsRecord() {
		List<BedDefense.Cell> bed = List.of(new BedDefense.Cell(0, 64, 0), new BedDefense.Cell(1, 64, 0));
		ledger.watched(bed, "blue", new BedDefense.Cell(0, 65, 0), new BedDefense.Block("Blue Wool", 0.8));

		BedLedger.Entry blue = ledger.of("Blue").orElseThrow();
		assertThat(blue.layers()).extracting(BedDefense.Layer::material).containsExactly("Blue Wool");
		assertThat(blue.gone()).isFalse();
	}
}
