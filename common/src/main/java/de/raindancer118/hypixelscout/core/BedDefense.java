package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a bed's defence looks like from outside: which materials are exposed, the softest first, and
 * where the softest one is — or that the bed is open.
 *
 * <p>Only blocks with a face to the air count. What sits under the outer shell is exactly what the
 * player cannot see, and this does not tell them: wool under end stone stays unknown until somebody
 * digs to it. The defence is the blocks from the bed's height up, within {@link #RADIUS} blocks of it
 * (counted along the axes) — the floor it stands on is not part of it.
 */
public final class BedDefense {
	/** Further than this from the bed a block is scenery, not defence. */
	public static final int RADIUS = 4;

	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	public record Cell(int x, int y, int z) {
		Cell offset(int[] by) {
			return new Cell(x + by[0], y + by[1], z + by[2]);
		}

		int distanceTo(Cell other) {
			return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
		}
	}

	/** A solid block: its name and how long it takes to break (vanilla's hardness). */
	public record Block(String name, double hardness) {
	}

	/** One exposed material: how many of its blocks face the air. */
	public record Material(String name, double hardness, int exposed) {
	}

	/**
	 * @param open    whether the bed itself has a face to the air
	 * @param outside the exposed materials, softest first
	 * @param weakest the exposed block of the softest material closest to the bed; {@code null} if none
	 */
	public record Report(boolean open, List<Material> outside, Cell weakest) {
	}

	/** The world, as far as this is concerned: the solid block in a cell, {@code null} for air. */
	@FunctionalInterface
	public interface World {
		Block at(Cell cell);
	}

	private BedDefense() {
	}

	public static Report analyse(List<Cell> bed, World world) {
		Set<Cell> bedCells = new HashSet<>(bed);
		int bottom = bed.stream().mapToInt(Cell::y).min().orElse(0);

		boolean open = false;
		for (Cell cell : bed) {
			for (int[] face : FACES) {
				Cell next = cell.offset(face);
				if (face[1] >= 0 && !bedCells.contains(next) && world.at(next) == null) {
					open = true;
				}
			}
		}

		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, Double> hardness = new LinkedHashMap<>();
		List<Cell> exposed = new ArrayList<>();
		Cell origin = bed.getFirst();
		for (int x = origin.x() - RADIUS - 1; x <= origin.x() + RADIUS + 1; x++) {
			for (int y = bottom; y <= bottom + RADIUS; y++) {
				for (int z = origin.z() - RADIUS - 1; z <= origin.z() + RADIUS + 1; z++) {
					Cell cell = new Cell(x, y, z);
					Block block = world.at(cell);
					if (block == null || bedCells.contains(cell) || distance(cell, bed) > RADIUS || !facesAir(cell, bedCells, world)) {
						continue;
					}
					counts.computeIfAbsent(block.name(), name -> new int[1])[0]++;
					hardness.put(block.name(), block.hardness());
					exposed.add(cell);
				}
			}
		}

		List<Material> outside = new ArrayList<>();
		counts.forEach((name, count) -> outside.add(new Material(name, hardness.get(name), count[0])));
		outside.sort(Comparator.comparingDouble(Material::hardness).thenComparing(Material::name));

		Cell weakest = null;
		if (!outside.isEmpty()) {
			String softest = outside.getFirst().name();
			weakest = exposed.stream()
					.filter(cell -> world.at(cell).name().equals(softest))
					.min(Comparator.comparingInt((Cell cell) -> distance(cell, bed)))
					.orElse(null);
		}
		return new Report(open, List.copyOf(outside), weakest);
	}

	private static boolean facesAir(Cell cell, Set<Cell> bed, World world) {
		for (int[] face : FACES) {
			Cell next = cell.offset(face);
			if (!bed.contains(next) && world.at(next) == null) {
				return true;
			}
		}
		return false;
	}

	private static int distance(Cell cell, List<Cell> bed) {
		int best = Integer.MAX_VALUE;
		for (Cell part : bed) {
			best = Math.min(best, cell.distanceTo(part));
		}
		return best;
	}
}
