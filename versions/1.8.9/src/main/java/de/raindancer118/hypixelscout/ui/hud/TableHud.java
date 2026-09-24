package de.raindancer118.hypixelscout.ui.hud;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Column;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.PlayerRow;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The in-game table: a card you open and close, not text printed onto the screen.
 *
 * <p>Faces on the left, a bar in the team's colour, each figure right-aligned in a measured column
 * under its heading. The same {@link #draw} serves the HUD and the editor that moves it, which is
 * what keeps "where it looks like it will be" and "where it ends up" the same thing.
 *
 * <p>Ported from 26.2's {@code ui.hud.TableHud}: a {@code GuiGraphicsExtractor} becomes an explicit
 * {@link FontRenderer} parameter plus static {@link Gui#drawRect} for the row stripe, {@code
 * I18n.get} becomes {@link StatCollector}, and the {@code record Layout} becomes a plain final
 * class since Java 8 has no records.
 */
public final class TableHud {
	private static final int PADDING = 6;
	private static final int BAR = 2;
	private static final int HEAD = 8;
	private static final int GAP = 10;
	private static final int ROW = 12;
	private static final int COLUMN_HEADER = 12;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;
	private final BooleanSupplier hasKey;
	private final Function<String, Teams.Team> teamOf;

	public TableHud(Roster roster, StatsService stats, Supplier<ScoutSettings> settings, BooleanSupplier hasKey) {
		this(roster, stats, settings, hasKey, new Function<String, Teams.Team>() {
			@Override
			public Teams.Team apply(String name) {
				return Teams.of(name);
			}
		});
	}

	/** With the teams answered by something other than the scoreboard — the editor's sample. */
	public TableHud(Roster roster, StatsService stats, Supplier<ScoutSettings> settings, BooleanSupplier hasKey,
			Function<String, Teams.Team> teamOf) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
		this.hasKey = hasKey;
		this.teamOf = teamOf;
	}

	/** What the table would show now, and how big it is. Measuring is drawing without the ink. */
	public static final class Layout {
		private final List<PlayerRow> rows;
		private final List<Column> columns;
		private final int[] columnWidths;
		private final int nameWidth;
		private final int width;
		private final int height;
		private final String message;

		Layout(List<PlayerRow> rows, List<Column> columns, int[] columnWidths, int nameWidth, int width,
				int height, String message) {
			this.rows = rows;
			this.columns = columns;
			this.columnWidths = columnWidths;
			this.nameWidth = nameWidth;
			this.width = width;
			this.height = height;
			this.message = message;
		}

		public List<PlayerRow> rows() {
			return rows;
		}

		public List<Column> columns() {
			return columns;
		}

		public int[] columnWidths() {
			return columnWidths;
		}

		public int nameWidth() {
			return nameWidth;
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		public String message() {
			return message;
		}
	}

	public Layout measure(FontRenderer font) {
		ScoutSettings current = settings.get();
		List<Column> columns = Column.compact(current);
		String message = null;
		List<PlayerRow> rows = Collections.emptyList();

		if (!hasKey.getAsBoolean()) {
			// One line rather than a failed row per player: with no key nothing can be looked up,
			// and sixteen identical errors say it no better than one sentence does.
			message = StatCollector.translateToLocal("message.hypixelscout.table.no_key");
		} else {
			rows = PlayerRow.all(roster, stats, current, current.table.sort, current.table.maxRows, teamOf);
			if (rows.isEmpty()) {
				message = StatCollector.translateToLocal(roster.isInGame() ? "message.hypixelscout.table.waiting"
						: "message.hypixelscout.table.not_in_game");
			}
		}

		int nameWidth = ScoutTheme.width(font, StatCollector.translateToLocal("message.hypixelscout.column.player"));
		int[] widths = new int[columns.size()];
		for (int i = 0; i < columns.size(); i++) {
			widths[i] = ScoutTheme.width(font, StatCollector.translateToLocal(columns.get(i).translationKey()));
		}

		for (PlayerRow row : rows) {
			nameWidth = Math.max(nameWidth, ScoutTheme.width(font, row.displayName()));
			for (int i = 0; i < columns.size(); i++) {
				widths[i] = Math.max(widths[i], ScoutTheme.width(font, columns.get(i).text(row)));
			}
		}

		int content = BAR + 3 + HEAD + 4 + nameWidth;
		for (int width : widths) {
			content += GAP + width;
		}

		content = Math.max(content, ScoutTheme.width(font, title()) + ScoutTheme.width(font, counter(rows)) + 16);
		if (message != null) {
			content = Math.max(content, ScoutTheme.width(font, message));
		}

		int body = message != null ? 14 : COLUMN_HEADER + rows.size() * ROW;
		int height = ScoutTheme.HEADER_HEIGHT + 1 + PADDING - 1 + body + PADDING - 2;

		return new Layout(rows, columns, widths, nameWidth, content + PADDING * 2, height, message);
	}

	public void draw(FontRenderer font, Layout layout, int left, int top) {
		ScoutSettings current = settings.get();
		int opacity = current.table.opacity;

		ScoutTheme.panel(left, top, layout.width(), layout.height(), opacity);
		ScoutTheme.header(left, top, layout.width(), opacity);

		int x = left + PADDING;
		int right = left + layout.width() - PADDING;

		ScoutTheme.text(font, title(), x, top + 6, ScoutTheme.TEXT);
		ScoutTheme.textRight(font, counter(layout.rows()), right, top + 6, ScoutTheme.TEXT_DIM);

		int y = top + ScoutTheme.HEADER_HEIGHT + PADDING;

		if (layout.message() != null) {
			ScoutTheme.text(font, layout.message(), x, y + 1, ScoutTheme.TEXT_DIM);
			return;
		}

		int nameX = x + BAR + 3 + HEAD + 4;
		ScoutTheme.text(font, StatCollector.translateToLocal("message.hypixelscout.column.player"), nameX, y,
				ScoutTheme.TEXT_FAINT);

		int[] rights = columnRights(layout, right);
		for (int i = 0; i < layout.columns().size(); i++) {
			ScoutTheme.textRight(font, StatCollector.translateToLocal(layout.columns().get(i).translationKey()),
					rights[i], y, layout.columns().get(i).sort() == current.table.sort
							? ScoutTheme.accent() : ScoutTheme.TEXT_FAINT);
		}

		y += COLUMN_HEADER - 3;
		ScoutTheme.divider(x, y, right - x);
		y += 2;

		String previousTeam = null;
		boolean stripe = false;

		for (PlayerRow row : layout.rows()) {
			if (current.table.groupByTeam && previousTeam != null && !previousTeam.equals(row.team().name())) {
				ScoutTheme.divider(x, y, right - x);
				stripe = false;
			}
			previousTeam = row.team().name();

			if (stripe) {
				Gui.drawRect(x, y, right, y + ROW, ScoutTheme.STRIPE);
			}
			stripe = !stripe;

			ScoutTheme.pill(x, y + 2, BAR, ROW - 4, row.team().argb());
			Heads.draw(row.uuid(), x + BAR + 3, y + 2, HEAD);

			ScoutTheme.text(font, ScoutTheme.fit(font, row.displayName(), layout.nameWidth()), nameX, y + 2,
					ScoutTheme.TEXT);

			for (int i = 0; i < layout.columns().size(); i++) {
				ScoutTheme.textRight(font, layout.columns().get(i).text(row), rights[i], y + 2, ScoutTheme.TEXT);
			}

			y += ROW;
		}
	}

	private static int[] columnRights(Layout layout, int right) {
		int[] rights = new int[layout.columns().size()];
		int edge = right;

		for (int i = rights.length - 1; i >= 0; i--) {
			rights[i] = edge;
			edge -= layout.columnWidths()[i] + GAP;
		}

		return rights;
	}

	/** The mod, and the map being played. */
	private String title() {
		StringBuilder title = new StringBuilder(ScoutTheme.accentCode()).append("§lSCOUT");

		if (roster.map() != null) {
			title.append(" §8· §f").append(roster.map());
		}
		if (roster.mode() != null) {
			title.append(" §7").append(BedwarsModes.shortName(roster.mode()));
		}

		return title.toString();
	}

	/** How many players, and how many are still being looked up. */
	private String counter(List<PlayerRow> rows) {
		if (roster.isInGame() && !roster.hasStarted()) {
			return "§e" + StatCollector.translateToLocal("message.hypixelscout.game.lobby") + " §7"
					+ StatCollector.translateToLocalFormatted("message.hypixelscout.table.players", rows.size());
		}

		int waiting = 0;
		for (PlayerRow row : rows) {
			if (row.stats() == null && row.pending()) {
				waiting++;
			}
		}
		String count = StatCollector.translateToLocalFormatted("message.hypixelscout.table.players", rows.size());
		return waiting > 0 ? "§8" + waiting + "… §7" + count : "§7" + count;
	}
}
