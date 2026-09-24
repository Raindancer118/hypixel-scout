package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Suspects;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * The suspects as a small card: each name in its team's colour with how sure the mod is, and — if
 * asked for — the checks behind it on a grey line underneath. Measured and drawn here for both the
 * HUD and its editor, so what is placed in the editor is exactly what shows in the game.
 *
 * <p>Ported from 26.2's {@code ui.hud.SuspectsHud}: a {@code GuiGraphicsExtractor} becomes an
 * explicit {@link FontRenderer}, {@code record Row}/{@code record Layout} become plain final
 * classes, and {@code Stream}-built joins become hand-written loops — Java 8 has none of them.
 */
public final class SuspectsHud {
	private static final int PADDING = 6;
	private static final int LINE = 11;
	private static final int MIN_WIDTH = 110;
	private static final int MAX_WIDTH = 210;
	private static final int CHECKS_INDENT = 8;

	/** One suspect's lines: the name with the confidence on the right, and the checks under it. */
	public static final class Row {
		private final String name;
		private final String percent;
		private final String checks;

		Row(String name, String percent, String checks) {
			this.name = name;
			this.percent = percent;
			this.checks = checks;
		}

		public String name() {
			return name;
		}

		public String percent() {
			return percent;
		}

		public String checks() {
			return checks;
		}
	}

	public static final class Layout {
		private final int width;
		private final int height;
		private final List<Row> rows;
		private final int total;

		Layout(int width, int height, List<Row> rows, int total) {
			this.width = width;
			this.height = height;
			this.rows = rows;
			this.total = total;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		public List<Row> rows() {
			return rows;
		}

		public int total() {
			return total;
		}
	}

	private SuspectsHud() {
	}

	/** Who the card shows: the flagged, and — unless only they are wanted — the unsure from the threshold up. */
	public static List<Suspicion.Suspect> shown(List<Suspicion.Suspect> all, ScoutSettings.Hud hud) {
		List<Suspicion.Suspect> result = new ArrayList<Suspicion.Suspect>();
		for (Suspicion.Suspect suspect : all) {
			if (suspect.flagged() || (!hud.onlyFlagged && suspect.confidence() * 100 >= hud.minPercent)) {
				result.add(suspect);
			}
		}
		return result;
	}

	public static Layout measure(FontRenderer font, List<Suspicion.Suspect> shown, ScoutSettings.Hud hud) {
		List<Row> rows = new ArrayList<Row>();
		int width = Math.max(MIN_WIDTH, ScoutTheme.width(font, title(shown.size())) + 2 * PADDING);

		int limit = Math.min(shown.size(), hud.maxRows);
		for (int i = 0; i < limit; i++) {
			Suspicion.Suspect suspect = shown.get(i);
			String name = (suspect.flagged() ? Suspects.MARK : "§e? ") + Suspects.teamCode(suspect.player())
					+ suspect.player();
			String percent = Suspects.percent(suspect.confidence());
			String checks = null;
			if (hud.showChecks) {
				StringBuilder builder = new StringBuilder("§7");
				boolean first = true;
				for (Suspicion.Seen seen : suspect.checks()) {
					if (!first) {
						builder.append("§8, ");
					}
					first = false;
					builder.append(seen.flagged() ? "§c" : "§7").append(seen.check().label())
							.append(" §8×").append(seen.count());
				}
				checks = builder.toString();
			}
			rows.add(new Row(name, percent, checks));
			width = Math.max(width, ScoutTheme.width(font, name) + ScoutTheme.width(font, percent) + 12 + 2 * PADDING);
			if (checks != null) {
				// The checks line sits indented under the name.
				width = Math.max(width, ScoutTheme.width(font, checks) + 2 * PADDING + CHECKS_INDENT);
			}
		}
		width = Math.min(width, MAX_WIDTH);

		int lines = 0;
		for (Row row : rows) {
			lines += row.checks() == null ? 1 : 2;
		}
		int height = ScoutTheme.HEADER_HEIGHT + 4 + lines * LINE + (shown.size() > rows.size() ? LINE : 0) + 3;
		return new Layout(width, height, rows, shown.size());
	}

	public static void draw(FontRenderer font, Layout layout, int x, int y, int opacity) {
		ScoutTheme.panel(x, y, layout.width(), layout.height(), opacity);
		ScoutTheme.header(x, y, layout.width(), opacity);
		ScoutTheme.text(font, title(layout.total()), x + PADDING, y + 6, ScoutTheme.TEXT);

		int lineY = y + ScoutTheme.HEADER_HEIGHT + 4;
		int inner = layout.width() - 2 * PADDING;
		for (Row row : layout.rows()) {
			int percentWidth = ScoutTheme.width(font, row.percent());
			ScoutTheme.text(font, ScoutTheme.fit(font, row.name(), inner - percentWidth - 8), x + PADDING, lineY,
					ScoutTheme.TEXT);
			ScoutTheme.textRight(font, row.percent(), x + layout.width() - PADDING, lineY, ScoutTheme.TEXT);
			lineY += LINE;
			if (row.checks() != null) {
				ScoutTheme.text(font, ScoutTheme.fit(font, row.checks(), inner - CHECKS_INDENT),
						x + PADDING + CHECKS_INDENT, lineY, ScoutTheme.TEXT_DIM);
				lineY += LINE;
			}
		}
		if (layout.total() > layout.rows().size()) {
			ScoutTheme.text(font, "§8" + StatCollector.translateToLocalFormatted("message.hypixelscout.suspects.more",
					layout.total() - layout.rows().size()), x + PADDING, lineY, ScoutTheme.TEXT);
		}
	}

	private static String title(int count) {
		return ScoutTheme.accentCode() + "§l" + StatCollector.translateToLocal("message.hypixelscout.suspects.title")
				+ " §r§8· §7" + count;
	}
}
