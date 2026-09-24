package de.raindancer118.hypixelscout.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BedDefenseTest {
	private static final BedDefense.Block FLOOR = new BedDefense.Block("Floor", 1.5);
	private static final BedDefense.Block WOOL = new BedDefense.Block("Wool", 0.8);
	private static final BedDefense.Block WOOD = new BedDefense.Block("Wood", 2.0);
	private static final BedDefense.Block END_STONE = new BedDefense.Block("End Stone", 3.0);

	/** A bed along x at (0,1,0)-(1,1,0) on a floor at y 0. */
	private static final List<BedDefense.Cell> BED = List.of(new BedDefense.Cell(0, 1, 0), new BedDefense.Cell(1, 1, 0));

	private final Map<BedDefense.Cell, BedDefense.Block> blocks = new HashMap<>();

	BedDefenseTest() {
		for (int x = -8; x <= 9; x++) {
			for (int z = -8; z <= 8; z++) {
				blocks.put(new BedDefense.Cell(x, 0, z), FLOOR);
			}
		}
	}

	/** Wraps the bed in shells: the first one closest, each further one a block further out. */
	private void defend(BedDefense.Block... layers) {
		for (int layer = layers.length; layer >= 1; layer--) {
			for (int x = -layer; x <= 1 + layer; x++) {
				for (int y = 1; y <= 1 + layer; y++) {
					for (int z = -layer; z <= layer; z++) {
						BedDefense.Cell cell = new BedDefense.Cell(x, y, z);
						if (BED.contains(cell) || distance(cell) > layer) {
							continue;
						}
						blocks.put(cell, layers[layer - 1]);
					}
				}
			}
		}
	}

	private static int distance(BedDefense.Cell cell) {
		int best = Integer.MAX_VALUE;
		for (BedDefense.Cell bed : BED) {
			best = Math.min(best, Math.abs(cell.x() - bed.x()) + Math.abs(cell.y() - bed.y()) + Math.abs(cell.z() - bed.z()));
		}
		return best;
	}

	private BedDefense.Report analyse() {
		return BedDefense.analyse(BED, blocks::get);
	}

	@Test
	void aBedWithNothingAroundItIsOpen() {
		BedDefense.Report report = analyse();
		assertThat(report.open()).isTrue();
		assertThat(report.outside()).isEmpty();
	}

	@Test
	void aDefendedBedIsNotOpenAndShowsItsOutsideOnly() {
		defend(WOOL, WOOD, END_STONE);
		BedDefense.Report report = analyse();

		assertThat(report.open()).isFalse();
		// Only the outer shell can be seen from outside; wool and wood under it stay unknown.
		assertThat(report.outside()).extracting(BedDefense.Material::name).containsExactly("End Stone");
	}

	@Test
	void aGapInTheOuterShellShowsWhatIsBehindIt() {
		defend(WOOL, END_STONE);
		// Someone broke one end stone block on top: the wool under it is exposed now.
		blocks.remove(new BedDefense.Cell(0, 3, 0));
		BedDefense.Report report = analyse();

		assertThat(report.outside()).extracting(BedDefense.Material::name).containsExactly("Wool", "End Stone");
		assertThat(report.weakest()).isEqualTo(new BedDefense.Cell(0, 2, 0));
	}

	@Test
	void theFloorIsNotPartOfTheDefence() {
		defend(WOOL);
		assertThat(analyse().outside()).extracting(BedDefense.Material::name).containsExactly("Wool");
	}

	@Test
	void weakestIsTheSoftestMaterialAndCountsAreKept() {
		defend(WOOD);
		blocks.put(new BedDefense.Cell(-1, 1, 0), WOOL);
		BedDefense.Report report = analyse();

		assertThat(report.outside().getFirst().name()).isEqualTo("Wool");
		assertThat(report.outside().getFirst().exposed()).isEqualTo(1);
		assertThat(report.outside().get(1).exposed()).isGreaterThan(1);
		assertThat(report.weakest()).isEqualTo(new BedDefense.Cell(-1, 1, 0));
	}
}
