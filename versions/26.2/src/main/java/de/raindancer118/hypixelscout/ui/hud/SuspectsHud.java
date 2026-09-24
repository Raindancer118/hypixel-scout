package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.cheat.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Suspects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The suspects as a small card: each name in its team's colour with how sure the mod is, and — if
 * asked for — the checks behind it on a grey line underneath. Measured and drawn here for both the
 * HUD and its editor, so what is placed in the editor is exactly what shows in the game.
 */
public final class SuspectsHud {
	private static final int PADDING = 6;
	private static final int LINE = 11;
	private static final int MIN_WIDTH = 110;
	private static final int MAX_WIDTH = 210;
	private static final int CHECKS_INDENT = 8;

	/** One suspect's lines: the name with the confidence on the right, and the checks under it. */
	public record Row(String name, String percent, String checks) {
	}

	public record Layout(int width, int height, List<Row> rows, int total) {
	}

	private SuspectsHud() {
	}

	/** Who the card shows: the flagged, and — unless only they are wanted — the unsure from the threshold up. */
	public static List<Suspicion.Suspect> shown(List<Suspicion.Suspect> all, ScoutSettings.Hud hud) {
		return all.stream()
				.filter(suspect -> suspect.flagged() || !hud.onlyFlagged && suspect.confidence() * 100 >= hud.minPercent)
				.toList();
	}

	public static Layout measure(List<Suspicion.Suspect> shown, ScoutSettings.Hud hud) {
		List<Row> rows = new ArrayList<>();
		int width = Math.max(MIN_WIDTH, ScoutTheme.width(title(shown.size())) + 2 * PADDING);
		for (Suspicion.Suspect suspect : shown.subList(0, Math.min(shown.size(), hud.maxRows))) {
			String name = (suspect.flagged() ? Suspects.MARK : "§e? ") + Suspects.teamCode(suspect.player()) + suspect.player();
			String percent = Suspects.percent(suspect.confidence());
			String checks = hud.showChecks ? "§7" + suspect.checks().stream()
					.map(seen -> (seen.flagged() ? "§c" : "§7") + seen.check().label() + " §8×" + seen.count())
					.collect(Collectors.joining("§8, ")) : null;
			rows.add(new Row(name, percent, checks));
			width = Math.max(width, ScoutTheme.width(name) + ScoutTheme.width(percent) + 12 + 2 * PADDING);
			if (checks != null) {
				// The checks line sits indented under the name.
				width = Math.max(width, ScoutTheme.width(checks) + 2 * PADDING + CHECKS_INDENT);
			}
		}
		width = Math.min(width, MAX_WIDTH);
		int lines = rows.stream().mapToInt(row -> row.checks() == null ? 1 : 2).sum();
		int height = ScoutTheme.HEADER_HEIGHT + 4 + lines * LINE + (shown.size() > rows.size() ? LINE : 0) + 3;
		return new Layout(width, height, List.copyOf(rows), shown.size());
	}

	public static void draw(GuiGraphicsExtractor g, Layout layout, int x, int y, int opacity) {
		ScoutTheme.panel(g, x, y, layout.width(), layout.height(), opacity);
		ScoutTheme.header(g, x, y, layout.width(), opacity);
		ScoutTheme.text(g, title(layout.total()), x + PADDING, y + 6, ScoutTheme.TEXT);

		int lineY = y + ScoutTheme.HEADER_HEIGHT + 4;
		int inner = layout.width() - 2 * PADDING;
		for (Row row : layout.rows()) {
			int percentWidth = ScoutTheme.width(row.percent());
			ScoutTheme.text(g, ScoutTheme.fit(row.name(), inner - percentWidth - 8), x + PADDING, lineY, ScoutTheme.TEXT);
			ScoutTheme.textRight(g, row.percent(), x + layout.width() - PADDING, lineY, ScoutTheme.TEXT);
			lineY += LINE;
			if (row.checks() != null) {
				ScoutTheme.text(g, ScoutTheme.fit(row.checks(), inner - CHECKS_INDENT), x + PADDING + CHECKS_INDENT, lineY,
						ScoutTheme.TEXT_DIM);
				lineY += LINE;
			}
		}
		if (layout.total() > layout.rows().size()) {
			ScoutTheme.text(g, "§8" + I18n.get("message.hypixelscout.suspects.more", layout.total() - layout.rows().size()),
					x + PADDING, lineY, ScoutTheme.TEXT);
		}
	}

	private static String title(int count) {
		return ScoutTheme.accentCode() + "§l" + I18n.get("message.hypixelscout.suspects.title") + " §r§8· §7" + count;
	}
}
