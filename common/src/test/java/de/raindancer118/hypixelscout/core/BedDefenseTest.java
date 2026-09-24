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

	@Test
	void everyDefenseBlockIsKeptIncludingCoveredBlocks() {
		defend(WOOL, END_STONE);
		BedDefense.Report report = analyse();

		assertThat(report.seen()).isNotEmpty();
		assertThat(report.seen().values()).extracting(BedDefense.Block::name).containsOnly("End Stone", "Wool");
	}

	@Test
	void theLayersAreWhatWasEverSeenByDistanceOutermostFirst() {
		defend(WOOL, END_STONE);
		java.util.Map<BedDefense.Cell, BedDefense.Block> seen = new java.util.HashMap<>(analyse().seen());
		// Somebody breaks through the top: the wool comes into sight, and stays remembered.
		blocks.remove(new BedDefense.Cell(0, 3, 0));
		seen.putAll(analyse().seen());

		List<BedDefense.Layer> layers = BedDefense.layers(BED, seen);
		assertThat(layers).extracting(BedDefense.Layer::material).containsExactly("End Stone", "Wool");
		assertThat(layers).extracting(BedDefense.Layer::depth).containsExactly(2, 1);
		assertThat(BedDefense.unknownInside(layers)).isFalse();
	}

	@Test
	void fullyCoveredLayersAreDetectedWithoutPriorSightings() {
		defend(WOOL, WOOD, END_STONE, FLOOR);
		List<BedDefense.Layer> layers = BedDefense.layers(BED, analyse().seen());

		assertThat(layers).extracting(BedDefense.Layer::material).containsExactly("Floor", "End Stone", "Wood", "Wool");
		assertThat(layers).extracting(BedDefense.Layer::depth).containsExactly(4, 3, 2, 1);
		assertThat(BedDefense.unknownInside(layers)).isFalse();
		assertThat(BedDefense.unknownInside(List.of())).isTrue();
	}

	@Test
	void aMixedLayerIsNamedByWhatMostOfItIs() {
		defend(WOOD);
		blocks.put(new BedDefense.Cell(-1, 1, 0), WOOL);
		List<BedDefense.Layer> layers = BedDefense.layers(BED, analyse().seen());

		assertThat(layers.getFirst().material()).isEqualTo("Wood");
		assertThat(layers.getFirst().count()).isGreaterThan(1);
	}

	@Test
	void aThickShellOfOneMaterialIsOneLayerThatKnowsItsThickness() {
		defend(WOOL, END_STONE, END_STONE);
		java.util.Map<BedDefense.Cell, BedDefense.Block> seen = new java.util.HashMap<>();
		// Seen as it went up, shell by shell, from the inside out.
		for (int shell = 1; shell <= 3; shell++) {
			for (var entry : blocks.entrySet()) {
				if (entry.getKey().y() > 0 && distance(entry.getKey()) == shell) {
					seen.put(entry.getKey(), entry.getValue());
				}
			}
		}
		List<BedDefense.Layer> layers = BedDefense.layers(BED, seen);

		assertThat(layers).extracting(BedDefense.Layer::material).containsExactly("End Stone", "Wool");
		assertThat(layers.getFirst().thickness()).isEqualTo(2);
		assertThat(layers.get(1).thickness()).isEqualTo(1);
	}

	@Test
	void aBlockPlacedAgainstADefenceBelongsToIt() {
		assertThat(BedDefense.partOf(BED, new BedDefense.Cell(0, 2, 0))).isTrue();
		assertThat(BedDefense.partOf(BED, new BedDefense.Cell(0, 5, 0))).isTrue();
		assertThat(BedDefense.partOf(BED, new BedDefense.Cell(0, 6, 0))).isFalse();
		// The floor is not part of it, nor the bed itself.
		assertThat(BedDefense.partOf(BED, new BedDefense.Cell(0, 0, 0))).isFalse();
		assertThat(BedDefense.partOf(BED, BED.getFirst())).isFalse();
	}

	@Test
	void threeShellsOfOneMaterialAreStillOneLayer() {
		defend(WOOL, END_STONE, END_STONE, END_STONE);
		java.util.Map<BedDefense.Cell, BedDefense.Block> seen = new java.util.HashMap<>();
		for (var entry : blocks.entrySet()) {
			if (entry.getKey().y() > 0) {
				seen.put(entry.getKey(), entry.getValue());
			}
		}
		List<BedDefense.Layer> layers = BedDefense.layers(BED, seen);

		assertThat(layers).extracting(BedDefense.Layer::material).containsExactly("End Stone", "Wool");
		assertThat(layers.getFirst().thickness()).isEqualTo(3);
	}
}
