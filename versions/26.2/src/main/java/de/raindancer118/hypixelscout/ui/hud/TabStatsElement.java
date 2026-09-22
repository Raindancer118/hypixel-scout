package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.ui.Column;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.PlayerRow;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.function.Supplier;

/**
 * The tab list, with stats in it, in place of vanilla's — during a Bedwars game and only when
 * switched on. Everywhere else the vanilla list is drawn exactly as before.
 *
 * <p>Replacing the element rather than mixing into {@code PlayerTabOverlay} keeps the whole layout
 * this class's to decide and leaves nothing to break when the vanilla list changes.
 */
public final class TabStatsElement implements HudElement {
	private static final int PADDING = 6;
	private static final int ROW = 12;
	private static final int HEAD = 8;
	private static final int GAP = 10;

	private final HudElement vanilla;
	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	public TabStatsElement(HudElement vanilla, Roster roster, StatsService stats,
			Supplier<ScoutSettings> settings) {
		this.vanilla = vanilla;
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		ScoutSettings current = settings.get();

		if (!current.tab.enabled || !roster.isInGame() || roster.members().isEmpty()) {
			vanilla.extractRenderState(graphics, delta);
			return;
		}

		if (client.options.keyPlayerList.isDown() && client.gui.screen() == null) {
			draw(graphics, current);
		}
	}

	private void draw(GuiGraphicsExtractor graphics, ScoutSettings current) {
		// Teams together, strongest first inside each, whatever the table is sorted by.
		List<PlayerRow> rows = new java.util.ArrayList<>(PlayerRow.all(roster, stats, current, SortMode.STARS, 100));
		rows.sort(java.util.Comparator.comparing((PlayerRow row) -> row.team().name())
				.thenComparing(PlayerRow.by(SortMode.STARS)));

		List<Column> columns = Column.compact(current);
		int nameWidth = 0;
		int[] widths = new int[columns.size()];
		for (int i = 0; i < columns.size(); i++) {
			widths[i] = ScoutTheme.width(I18n.get(columns.get(i).translationKey()));
		}

		for (PlayerRow row : rows) {
			nameWidth = Math.max(nameWidth, ScoutTheme.width(row.displayName()));
			for (int i = 0; i < columns.size(); i++) {
				widths[i] = Math.max(widths[i], ScoutTheme.width(columns.get(i).text(row)));
			}
		}

		int content = 2 + 3 + HEAD + 4 + nameWidth;
		for (int width : widths) {
			content += GAP + width;
		}

		int width = content + PADDING * 2;
		int height = ScoutTheme.HEADER_HEIGHT + PADDING + 11 + rows.size() * ROW + PADDING - 2;

		// One column, shrunk until it fits: a list split in two panes reads worse than a smaller one.
		float scale = Math.min(1.0f, Math.min((graphics.guiWidth() - 8) / (float) width,
				(graphics.guiHeight() - 14) / (float) height));
		scale = Math.max(0.5f, scale);

		int left = Math.round((graphics.guiWidth() / scale - width) / 2);
		int top = Math.round(6 / scale);

		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);

		ScoutTheme.panel(graphics, left, top, width, height, 92);
		ScoutTheme.header(graphics, left, top, width, 92);
		ScoutTheme.text(graphics, ScoutTheme.accentCode() + "§lSCOUT §8· §7"
				+ I18n.get("message.hypixelscout.tab.title"), left + PADDING, top + 6, ScoutTheme.TEXT);
		ScoutTheme.textRight(graphics, "§7" + I18n.get("message.hypixelscout.table.players", rows.size()),
				left + width - PADDING, top + 6, ScoutTheme.TEXT);

		int x = left + PADDING;
		int right = x + content;
		int y = top + ScoutTheme.HEADER_HEIGHT + PADDING;

		int edge = right;
		int[] rights = new int[columns.size()];
		for (int i = columns.size() - 1; i >= 0; i--) {
			rights[i] = edge;
			edge -= widths[i] + GAP;
		}

		ScoutTheme.text(graphics, I18n.get("message.hypixelscout.column.player"), x + 2 + 3 + HEAD + 4,
				y, ScoutTheme.TEXT_FAINT);
		for (int i = 0; i < columns.size(); i++) {
			ScoutTheme.textRight(graphics, I18n.get(columns.get(i).translationKey()), rights[i], y,
					ScoutTheme.TEXT_FAINT);
		}
		y += 9;
		ScoutTheme.divider(graphics, x, y, content);
		y += 2;

		String previousTeam = null;
		for (int index = 0; index < rows.size(); index++) {
			PlayerRow row = rows.get(index);
			if (previousTeam != null && !previousTeam.equals(row.team().name())) {
				ScoutTheme.divider(graphics, x, y, content);
			}
			previousTeam = row.team().name();

			if (index % 2 == 1) {
				graphics.fill(x, y, right, y + ROW, ScoutTheme.STRIPE);
			}

			ScoutTheme.pill(graphics, x, y + 2, 2, ROW - 4, row.team().argb());
			Heads.draw(graphics, row.uuid(), x + 5, y + 2, HEAD);
			ScoutTheme.text(graphics, row.displayName(), x + 2 + 3 + HEAD + 4, y + 2, ScoutTheme.TEXT);
			for (int i = 0; i < columns.size(); i++) {
				ScoutTheme.textRight(graphics, columns.get(i).text(row), rights[i], y + 2, ScoutTheme.TEXT);
			}

			y += ROW;
		}

		graphics.pose().popMatrix();
	}
}
