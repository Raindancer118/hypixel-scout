package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * A bed's loaded defence: all blocks within {@link #RADIUS} axial steps, from bed height up.
 * The layer snapshot includes covered blocks; exposed materials and the weakest accessible block
 * are tracked separately. The supporting floor is excluded.
 */
public final class BedDefense {
	/** Further than this from the bed a block is scenery, not defence. */
	public static final int RADIUS = 4;

	private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	public static final class Cell {
		private final int x;
		private final int y;
		private final int z;

		public Cell(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public int x() {
			return x;
		}

		public int y() {
			return y;
		}

		public int z() {
			return z;
		}

		Cell offset(int[] by) {
			return new Cell(x + by[0], y + by[1], z + by[2]);
		}

		int distanceTo(Cell other) {
			return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Cell)) return false;
			Cell other = (Cell) obj;
			return x == other.x && y == other.y && z == other.z;
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y, z);
		}

		@Override
		public String toString() {
			return "Cell[x=" + x + ", y=" + y + ", z=" + z + "]";
		}
	}

	/** A solid block: its name and how long it takes to break (vanilla's hardness). */
	public static final class Block {
		private final String name;
		private final double hardness;

		public Block(String name, double hardness) {
			this.name = name;
			this.hardness = hardness;
		}

		public String name() {
			return name;
		}

		public double hardness() {
			return hardness;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Block)) return false;
			Block other = (Block) obj;
			return Double.doubleToLongBits(hardness) == Double.doubleToLongBits(other.hardness)
					&& Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, hardness);
		}

		@Override
		public String toString() {
			return "Block[name=" + name + ", hardness=" + hardness + "]";
		}
	}

	/** One exposed material: how many of its blocks face the air. */
	public static final class Material {
		private final String name;
		private final double hardness;
		private final int exposed;

		public Material(String name, double hardness, int exposed) {
			this.name = name;
			this.hardness = hardness;
			this.exposed = exposed;
		}

		public String name() {
			return name;
		}

		public double hardness() {
			return hardness;
		}

		public int exposed() {
			return exposed;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Material)) return false;
			Material other = (Material) obj;
			return exposed == other.exposed
					&& Double.doubleToLongBits(hardness) == Double.doubleToLongBits(other.hardness)
					&& Objects.equals(name, other.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, hardness, exposed);
		}

		@Override
		public String toString() {
			return "Material[name=" + name + ", hardness=" + hardness + ", exposed=" + exposed + "]";
		}
	}

	/**
	 * @param open    whether the bed itself has a face to the air
	 * @param outside the exposed materials, softest first
	 * @param weakest the exposed block of the softest material closest to the bed; {@code null} if none
	 * @param seen    every loaded defence block, including fully covered inner layers
	 */
	public static final class Report {
		private final boolean open;
		private final List<Material> outside;
		private final Cell weakest;
		private final Map<Cell, Block> seen;

		public Report(boolean open, List<Material> outside, Cell weakest, Map<Cell, Block> seen) {
			this.open = open;
			this.outside = outside;
			this.weakest = weakest;
			Map<Cell, Block> copy = new LinkedHashMap<>();
			for (Map.Entry<Cell, Block> entry : seen.entrySet()) {
				copy.put(Objects.requireNonNull(entry.getKey()), Objects.requireNonNull(entry.getValue()));
			}
			this.seen = Collections.unmodifiableMap(copy);
		}

		public Report(boolean open, List<Material> outside, Cell weakest) {
			this(open, outside, weakest, Collections.emptyMap());
		}

		public boolean open() {
			return open;
		}

		public List<Material> outside() {
			return outside;
		}

		public Cell weakest() {
			return weakest;
		}

		public Map<Cell, Block> seen() {
			return seen;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Report)) return false;
			Report other = (Report) obj;
			return open == other.open && Objects.equals(outside, other.outside)
					&& Objects.equals(weakest, other.weakest) && Objects.equals(seen, other.seen);
		}

		@Override
		public int hashCode() {
			return Objects.hash(open, outside, weakest, seen);
		}

		@Override
		public String toString() {
			return "Report[open=" + open + ", outside=" + outside + ", weakest=" + weakest + ", seen=" + seen + "]";
		}
	}

	/**
	 * One layer of a defence: one material wrapped round the bed, named by what most of its blocks
	 * are. {@code depth} is how far out its inner side is — 1 is right against the bed — and
	 * {@code thickness} how many blocks deep it goes outwards from there.
	 */
	public static final class Layer {
		private final int depth;
		private final String material;
		private final int count;
		private final int thickness;

		public Layer(int depth, String material, int count, int thickness) {
			this.depth = depth;
			this.material = material;
			this.count = count;
			this.thickness = thickness;
		}

		public int depth() {
			return depth;
		}

		public String material() {
			return material;
		}

		public int count() {
			return count;
		}

		public int thickness() {
			return thickness;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Layer)) return false;
			Layer other = (Layer) obj;
			return depth == other.depth && count == other.count && thickness == other.thickness
					&& Objects.equals(material, other.material);
		}

		@Override
		public int hashCode() {
			return Objects.hash(depth, material, count, thickness);
		}

		@Override
		public String toString() {
			return "Layer[depth=" + depth + ", material=" + material + ", count=" + count
					+ ", thickness=" + thickness + "]";
		}
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

		Map<Cell, Block> seen = new LinkedHashMap<>();
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, Double> hardness = new LinkedHashMap<>();
		List<Cell> exposed = new ArrayList<>();
		Cell origin = bed.get(0);
		for (int x = origin.x() - RADIUS - 1; x <= origin.x() + RADIUS + 1; x++) {
			for (int y = bottom; y <= bottom + RADIUS; y++) {
				for (int z = origin.z() - RADIUS - 1; z <= origin.z() + RADIUS + 1; z++) {
					Cell cell = new Cell(x, y, z);
					Block block = world.at(cell);
					if (block == null || bedCells.contains(cell) || distance(cell, bed) > RADIUS) {
						continue;
					}
					seen.put(cell, block);
					if (!facesAir(cell, bedCells, world)) {
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
			String softest = outside.get(0).name();
			weakest = exposed.stream()
					.filter(cell -> world.at(cell).name().equals(softest))
					.min(Comparator.comparingInt((Cell cell) -> distance(cell, bed)))
					.orElse(null);
		}
		return new Report(open, Collections.unmodifiableList(new ArrayList<>(outside)), weakest, seen);
	}

	/**
	 * The layers of a defence snapshot, outermost first, including covered blocks.
	 */
	public static List<Layer> layers(List<Cell> bed, Map<Cell, Block> seen) {
		Map<Integer, Map<String, int[]>> byDepth = new TreeMap<>(Comparator.reverseOrder());
		Map<String, Double> hardness = new HashMap<>();
		seen.forEach((cell, block) -> {
			int depth = distance(cell, bed);
			if (depth >= 1 && depth <= RADIUS) {
				byDepth.computeIfAbsent(depth, d -> new LinkedHashMap<>()).computeIfAbsent(block.name(), n -> new int[1])[0]++;
				hardness.put(block.name(), block.hardness());
			}
		});
		List<Layer> layers = new ArrayList<>();
		byDepth.forEach((depth, counts) -> {
			Map.Entry<String, int[]> most = counts.entrySet().stream()
					.max(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[0])
							.thenComparingDouble(e -> hardness.get(e.getKey())))
					.get();
			Layer outer = layers.isEmpty() ? null : layers.get(layers.size() - 1);
			// One material several blocks deep is one thick layer, not several — even across a depth
			// nobody saw a block of.
			if (outer != null && outer.material().equals(most.getKey())) {
				int outermost = outer.depth() + outer.thickness() - 1;
				layers.set(layers.size() - 1, new Layer(depth, outer.material(), outer.count() + most.getValue()[0],
						outermost - depth + 1));
			} else {
				layers.add(new Layer(depth, most.getKey(), most.getValue()[0], 1));
			}
		});
		return Collections.unmodifiableList(new ArrayList<>(layers));
	}

	/** Whether a block in this cell would be part of the bed's defence: from its height up, close enough. */
	public static boolean partOf(List<Cell> bed, Cell cell) {
		int bottom = bed.stream().mapToInt(Cell::y).min().orElse(0);
		return !bed.contains(cell) && cell.y() >= bottom && distance(cell, bed) <= RADIUS;
	}

	/** Whether there is more inside than has been seen: no layer seen right against the bed. */
	public static boolean unknownInside(List<Layer> layers) {
		return layers.isEmpty() || layers.get(layers.size() - 1).depth() > 1;
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
