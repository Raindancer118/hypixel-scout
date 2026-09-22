package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.TeamReport;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.game.QuickQueue;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Column;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.PlayerRow;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The mod's front door: everybody in the game, the teams they make up, a lookup for anybody at all,
 * and the queue slots — four tabs under vanilla's own tab bar, so it sits in the game like one of
 * its menus rather than a program that happens to run inside it.
 *
 * <p>Nothing here waits on the network. Rows are rebuilt from the roster and the cache every tick,
 * which is also what fills them in as the lookups come back while the screen is open.
 */
public final class ScoutScreen extends Screen {
	/** The four tabs, in the order the bar shows them. */
	public enum Page {
		GAME, TEAMS, LOOKUP, QUEUE;

		Component title() {
			return Component.translatable("message.hypixelscout.page." + name().toLowerCase(Locale.ROOT));
		}
	}

	private static final int FOOTER = 33;
	private static final int ROW = 24;
	private static final int HEAD = 16;
	private static final int GAP = 12;
	private static final int MAX_CONTENT = 470;

	private final HypixelScout mod;
	private final Screen parent;
	private final Roster roster;
	private final StatsService stats;

	private final TabManager tabManager = new TabManager(widget -> { }, widget -> { },
			this::onTabSelected, tab -> { });

	private Page page = Page.GAME;
	private MenuTabBar tabBar;
	private int contentTop;
	private int contentBottom;

	private GameList gameList;
	private List<Column> columns = List.of();
	private int[] columnWidths = new int[0];

	private EditBox lookupBox;
	private int teamScroll;

	public ScoutScreen(HypixelScout mod, Screen parent) {
		super(Component.translatable("message.hypixelscout.title"));
		this.mod = mod;
		this.parent = parent;
		this.roster = mod.roster();
		this.stats = mod.stats();
	}

	/** Chooses the tab the screen opens on; commands use it to land on the right one. */
	public void showPage(Page page) {
		this.page = page;
	}

	public Page page() {
		return page;
	}

	private ScoutSettings settings() {
		return mod.settings();
	}

	private boolean hasKey() {
		return mod.client().hasApiKey();
	}

	// --- layout -------------------------------------------------------------------------------

	@Override
	protected void init() {
		List<Tab> tabs = new ArrayList<>();
		for (Page each : Page.values()) {
			tabs.add(new PageTab(each));
		}

		tabBar = MenuTabBar.builder(tabManager, width).addTabs(tabs.toArray(Tab[]::new)).build();
		addRenderableWidget(tabBar);
		tabBar.arrangeElements(width);
		contentTop = tabBar.getRectangle().bottom() + 6;
		contentBottom = height - FOOTER;
		tabManager.setTabArea(new ScreenRectangle(0, contentTop, width, contentBottom - contentTop));
		tabBar.selectTab(page.ordinal(), false);

		gameList = null;
		lookupBox = null;

		switch (page) {
			case GAME -> initGame();
			case TEAMS -> initTeams();
			case LOOKUP -> initLookup();
			case QUEUE -> initQueue();
		}

		int footerY = height - FOOTER + 7;
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.settings"),
						button -> minecraft.gui.setScreen(mod.settingsScreen(this)))
				.bounds(width / 2 - 154, footerY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.move_table"),
						button -> minecraft.gui.setScreen(mod.tableEditor(this)))
				.bounds(width / 2 - 50, footerY, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 + 54, footerY, 100, 20).build());
	}

	private void onTabSelected(Tab tab) {
		if (tab instanceof PageTab(Page selected) && selected != page) {
			page = selected;
			teamScroll = 0;
			rebuildWidgets();
		}
	}

	private int contentWidth() {
		return Math.min(width - 24, MAX_CONTENT);
	}

	private int contentLeft() {
		return (width - contentWidth()) / 2;
	}

	// --- game ---------------------------------------------------------------------------------

	private void initGame() {
		if (!hasKey()) {
			addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.game.set_key"),
							button -> minecraft.gui.setScreen(mod.settingsScreen(this)))
					.bounds(width / 2 - 75, (contentTop + contentBottom) / 2 + 14, 150, 20).build());
			return;
		}

		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.refresh"),
						button -> mod.refresh())
				.tooltip(Tooltip.create(Component.translatable("message.hypixelscout.refresh.tooltip")))
				.bounds(contentLeft() + contentWidth() - 70, contentTop - 2, 70, 16).build());

		int listTop = contentTop + 30;
		gameList = new GameList(width, contentBottom - listTop - 2, listTop);
		addRenderableWidget(gameList);
		syncRows();
	}

	private List<PlayerRow> rows() {
		return PlayerRow.all(roster, stats, settings(), settings().table.sort, Integer.MAX_VALUE);
	}

	/** Measures the columns and refreshes the list. Called on open and every tick. */
	private void syncRows() {
		if (gameList == null) {
			return;
		}

		List<PlayerRow> rows = rows();
		columns = Column.full(settings());
		columnWidths = new int[columns.size()];

		for (int i = 0; i < columns.size(); i++) {
			columnWidths[i] = ScoutTheme.width(I18n.get(columns.get(i).translationKey())) + 4;
			for (PlayerRow row : rows) {
				columnWidths[i] = Math.max(columnWidths[i], ScoutTheme.width(columns.get(i).text(row)) + 4);
			}
		}

		gameList.sync(rows);
	}

	/** Right edge of each figure column inside a row whose content ends at {@code right}. */
	private int[] columnRights(int right) {
		int[] rights = new int[columns.size()];
		int edge = right;
		for (int i = rights.length - 1; i >= 0; i--) {
			rights[i] = edge;
			edge -= columnWidths[i] + GAP;
		}
		return rights;
	}

	private void drawGame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int left = contentLeft();
		int right = left + contentWidth();

		if (!hasKey()) {
			int middle = (contentTop + contentBottom) / 2;
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.game.no_key"), width / 2, middle - 14,
					ScoutTheme.TEXT);
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.game.no_key.hint"), width / 2, middle - 2,
					ScoutTheme.TEXT_DIM);
			return;
		}

		// The status strip: where you are, how many, and how much of the key's budget is left.
		String where = roster.isInGame()
				? "§f" + roster.map() + " §7" + BedwarsModes.shortName(roster.mode()) + " §8· §7"
						+ I18n.get("message.hypixelscout.table.players", roster.members().size())
				: "§7" + I18n.get("message.hypixelscout.game.not_in_game.short");
		ScoutTheme.text(g, where, left, contentTop + 2, ScoutTheme.TEXT);
		String budget = I18n.get("message.hypixelscout.game.budget", mod.client().getLimiter().remaining());
		ScoutTheme.textRight(g, "§8" + budget, right - 76, contentTop + 2, ScoutTheme.TEXT);

		if (gameList.children().isEmpty()) {
			String line = roster.isInGame() ? "message.hypixelscout.game.waiting" : "message.hypixelscout.game.not_in_game";
			int middle = (contentTop + contentBottom) / 2;
			ScoutTheme.textCentred(g, I18n.get(line), width / 2, middle - 6, ScoutTheme.TEXT);
			ScoutTheme.textCentred(g, I18n.get(line + ".hint"), width / 2, middle + 6, ScoutTheme.TEXT_DIM);
			return;
		}

		drawColumnHeader(g, mouseX, mouseY);
	}

	/** Column labels over the list; the ones that sort are clickable and the active one is lit. */
	private void drawColumnHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int rowLeft = gameList.getRowLeft();
		int rowRight = gameList.getRowRight();
		int y = contentTop + 17;
		SortMode sort = settings().table.sort;

		String player = I18n.get("message.hypixelscout.column.player");
		int playerX = rowLeft + 5 + HEAD + 6;
		boolean playerHover = mouseY >= y - 2 && mouseY < y + 10 && mouseX >= playerX
				&& mouseX < playerX + ScoutTheme.width(player);
		ScoutTheme.text(g, player + (sort == SortMode.NAME ? " ▾" : ""), playerX, y,
				sort == SortMode.NAME ? ScoutTheme.accent() : playerHover ? ScoutTheme.TEXT : ScoutTheme.TEXT_FAINT);

		int[] rights = columnRights(rowRight - 4);
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			String label = I18n.get(column.translationKey());
			boolean active = column.sort() != null && column.sort() == sort;
			boolean hover = column.sort() != null && mouseY >= y - 2 && mouseY < y + 10
					&& mouseX >= rights[i] - columnWidths[i] && mouseX < rights[i];
			ScoutTheme.textRight(g, (active ? "▾ " : "") + label, rights[i], y,
					active ? ScoutTheme.accent() : hover ? ScoutTheme.TEXT : ScoutTheme.TEXT_FAINT);
		}

		ScoutTheme.divider(g, rowLeft, y + 11, rowRight - rowLeft);
	}

	/** A click on a column label sorts by it. Returns whether it was one. */
	private boolean clickColumnHeader(double mouseX, double mouseY) {
		if (gameList == null || gameList.children().isEmpty()) {
			return false;
		}

		int y = contentTop + 17;
		if (mouseY < y - 2 || mouseY >= y + 10) {
			return false;
		}

		int playerX = gameList.getRowLeft() + 5 + HEAD + 6;
		if (mouseX >= playerX && mouseX < playerX + ScoutTheme.width(I18n.get("message.hypixelscout.column.player"))) {
			sortBy(SortMode.NAME);
			return true;
		}

		int[] rights = columnRights(gameList.getRowRight() - 4);
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).sort() != null && mouseX >= rights[i] - columnWidths[i] && mouseX < rights[i]) {
				sortBy(columns.get(i).sort());
				return true;
			}
		}

		// The stars have no column of their own; the rest of the header row goes back to them.
		sortBy(SortMode.STARS);
		return true;
	}

	private void sortBy(SortMode sort) {
		settings().table.sort = sort;
		mod.saveSettings();
		syncRows();
	}

	/** The players, one card-like row each. */
	final class GameList extends ObjectSelectionList<GameList.Entry> {
		GameList(int width, int height, int y) {
			super(ScoutScreen.this.minecraft, width, height, y, ROW);
		}

		@Override
		public int getRowWidth() {
			return contentWidth();
		}

		/** Keeps the entries — and so the scroll position and hover — when only the numbers changed. */
		void sync(List<PlayerRow> rows) {
			List<Entry> current = children();
			boolean same = current.size() == rows.size();
			for (int i = 0; same && i < rows.size(); i++) {
				same = current.get(i).row.uuid().equals(rows.get(i).uuid());
			}

			if (same) {
				for (int i = 0; i < rows.size(); i++) {
					current.get(i).row = rows.get(i);
				}
				return;
			}

			List<Entry> entries = new ArrayList<>();
			rows.forEach(row -> entries.add(new Entry(row)));
			replaceEntries(entries);
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private PlayerRow row;

			Entry(PlayerRow row) {
				this.row = row;
			}

			@Override
			public Component getNarration() {
				return Component.literal(StatLines.plain(row.displayName()));
			}

			@Override
			public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered,
					float partialTick) {
				int x = getContentX();
				int y = getContentY();
				int right = getContentRight();

				if (hovered) {
					ScoutTheme.rounded(g, x - 2, y - 1, right - x + 4, getContentHeight() + 2, ScoutTheme.HOVER);
				}

				ScoutTheme.pill(g, x, y + 1, 3, getContentHeight() - 2, row.team().argb());
				Heads.draw(g, row.uuid(), x + 6, y + (getContentHeight() - HEAD) / 2, HEAD);

				int[] rights = columnRights(right - 4);
				int nameX = x + 5 + HEAD + 6;
				int nameRoom = (rights.length == 0 ? right : rights[0] - columnWidths[0]) - nameX - 6;

				ScoutTheme.text(g, ScoutTheme.fit(row.displayName(), nameRoom), nameX, y + 2, ScoutTheme.TEXT);
				ScoutTheme.text(g, ScoutTheme.fit(subtitle(row), nameRoom), nameX, y + 12, ScoutTheme.TEXT_FAINT);

				for (int i = 0; i < columns.size(); i++) {
					ScoutTheme.textRight(g, columns.get(i).text(row), rights[i], y + 7, ScoutTheme.TEXT);
				}
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				minecraft.gui.setScreen(mod.profileScreen(row.name(), row.uuid(), ScoutScreen.this));
				return true;
			}
		}
	}

	/** The small line under a name: what is known about the account, or why nothing is. */
	private static String subtitle(PlayerRow row) {
		PlayerStats stats = row.stats();
		if (stats == null) {
			return row.pending() || row.failure() == null
					? I18n.get("message.hypixelscout.game.looking_up") : "§c" + row.failure();
		}
		if (stats.isNicked()) {
			return "§d" + I18n.get("message.hypixelscout.game.nicked");
		}

		String team = row.team().name().isEmpty() ? "" : row.team().name() + " · ";
		return team + I18n.get("message.hypixelscout.game.subtitle", (int) stats.getNetworkLevel(),
				StatFormat.age(stats.getFirstLogin(), System.currentTimeMillis()),
				StatFormat.count(stats.getWins()));
	}

	// --- teams --------------------------------------------------------------------------------

	private void initTeams() {
		Button party = addRenderableWidget(Button.builder(
						Component.translatable("message.hypixelscout.teams.party"), button -> mod.partyReport().send())
				.tooltip(Tooltip.create(Component.translatable("message.hypixelscout.teams.party.tooltip")))
				.bounds(contentLeft() + contentWidth() - 150, contentTop - 2, 150, 16).build());
		party.active = roster.isInGame() && QuickQueue.onHypixel();
	}

	private record TeamGroup(Teams.Team team, List<PlayerRow> rows, TeamReport report, boolean own) {
	}

	private List<TeamGroup> teamGroups() {
		Map<String, List<PlayerRow>> byTeam = new LinkedHashMap<>();
		for (PlayerRow row : PlayerRow.all(roster, stats, groupedSettings(), SortMode.STARS, Integer.MAX_VALUE)) {
			byTeam.computeIfAbsent(row.team().name(), key -> new ArrayList<>()).add(row);
		}

		Teams.Team own = Teams.own();
		List<TeamGroup> groups = new ArrayList<>();
		byTeam.forEach((name, rows) -> groups.add(new TeamGroup(rows.getFirst().team(), rows,
				TeamReport.of(name, rows.stream().map(PlayerRow::stats).toList()),
				own != Teams.NONE && own.equals(rows.getFirst().team()))));

		// The team to worry about most comes first; your own team goes last.
		groups.sort((left, right) -> {
			if (left.own() != right.own()) {
				return left.own() ? 1 : -1;
			}
			return Double.compare(danger(right), danger(left));
		});
		return groups;
	}

	private static double danger(TeamGroup group) {
		return group.rows().stream().mapToDouble(row -> Threat.index(row.stats())).max().orElse(-1);
	}

	/** Teams are always grouped, whatever the table does, and never hide the player's own team. */
	private ScoutSettings groupedSettings() {
		ScoutSettings copy = new ScoutSettings();
		copy.table.groupByTeam = true;
		copy.table.hideOwnTeam = false;
		return copy;
	}

	private void drawTeams(GuiGraphicsExtractor g) {
		int left = contentLeft();
		ScoutTheme.text(g, "§7" + I18n.get("message.hypixelscout.teams.caption"), left, contentTop + 2, ScoutTheme.TEXT);

		List<TeamGroup> groups = teamGroups();
		if (groups.isEmpty()) {
			int middle = (contentTop + contentBottom) / 2;
			String line = !hasKey() ? "message.hypixelscout.game.no_key"
					: roster.isInGame() ? "message.hypixelscout.game.waiting" : "message.hypixelscout.game.not_in_game";
			ScoutTheme.textCentred(g, I18n.get(line), width / 2, middle - 6, ScoutTheme.TEXT);
			return;
		}

		int columnsCount = contentWidth() >= 380 ? 2 : 1;
		int gap = 8;
		int cardWidth = (contentWidth() - gap * (columnsCount - 1)) / columnsCount;
		int largest = groups.stream().mapToInt(group -> group.rows().size()).max().orElse(1);
		int cardHeight = ScoutTheme.HEADER_HEIGHT + 6 + largest * 11 + 16;

		int top = contentTop + 18;
		int rows = (groups.size() + columnsCount - 1) / columnsCount;
		int total = rows * (cardHeight + gap) - gap;
		teamScroll = Math.clamp(teamScroll, 0, Math.max(0, total - (contentBottom - top - 4)));

		g.enableScissor(0, top, width, contentBottom - 2);
		for (int i = 0; i < groups.size(); i++) {
			int x = left + (i % columnsCount) * (cardWidth + gap);
			int y = top + (i / columnsCount) * (cardHeight + gap) - teamScroll;
			drawTeamCard(g, groups.get(i), x, y, cardWidth, cardHeight);
		}
		g.disableScissor();
	}

	private void drawTeamCard(GuiGraphicsExtractor g, TeamGroup group, int x, int y, int width, int height) {
		ScoutTheme.panel(g, x, y, width, height, 80);
		ScoutTheme.rounded(g, x + 1, y + 1, width - 2, ScoutTheme.HEADER_HEIGHT - 1,
				(0x40 << 24) | (group.team().rgb() & 0xFFFFFF));
		g.fill(x + 1, y + ScoutTheme.HEADER_HEIGHT, x + width - 1, y + ScoutTheme.HEADER_HEIGHT + 1, group.team().argb());

		String name = group.team().name().isEmpty() ? I18n.get("message.hypixelscout.teams.no_team") : group.team().name();
		ScoutTheme.text(g, "§l" + name + (group.own() ? " §r§7" + I18n.get("message.hypixelscout.teams.you") : ""),
				x + 6, y + 6, 0xFF000000 | group.team().rgb());

		Threat threat = group.rows().stream().map(row -> Threat.of(row.stats()))
				.max(Enum::compareTo).orElse(Threat.UNKNOWN);
		String badge = threat.colour() + threat.label();
		ScoutTheme.badge(g, badge, x + width - 6 - ScoutTheme.width(badge) - 6, y + 5, 0x60000000, ScoutTheme.TEXT);

		int rowY = y + ScoutTheme.HEADER_HEIGHT + 5;
		for (PlayerRow row : group.rows()) {
			Heads.draw(g, row.uuid(), x + 6, rowY, 8);
			String fkdr = Column.FKDR.text(row);
			ScoutTheme.text(g, ScoutTheme.fit(row.displayName(), width - 30 - ScoutTheme.width(fkdr)), x + 18, rowY,
					ScoutTheme.TEXT);
			ScoutTheme.textRight(g, fkdr, x + width - 6, rowY, ScoutTheme.TEXT);
			rowY += 11;
		}

		TeamReport report = group.report();
		ScoutTheme.divider(g, x + 6, y + height - 15, width - 12);
		String stars = "§f" + report.getCombinedStars() + "✫";
		List<TeamReport.Streak> streaks = report.streaksAbove(settings().alerts.streakThreshold);
		if (!streaks.isEmpty()) {
			stars += " §c" + streaks.getFirst().getWinstreak() + " WS";
		}
		ScoutTheme.text(g, stars, x + 6, y + height - 11, ScoutTheme.TEXT);
		ScoutTheme.textRight(g, "§7FKDR §f" + StatFormat.ratio(report.getCombinedFkdr()) + " §7WLR §f"
				+ StatFormat.ratio(report.getCombinedWlr()), x + width - 6, y + height - 11, ScoutTheme.TEXT);
	}

	// --- lookup -------------------------------------------------------------------------------

	private void initLookup() {
		int boxWidth = Math.min(240, contentWidth() - 90);
		int x = width / 2 - (boxWidth + 84) / 2;
		int y = contentTop + 14;

		lookupBox = new EditBox(font, x, y, boxWidth, 20, Component.translatable("message.hypixelscout.lookup.hint"));
		lookupBox.setHint(Component.translatable("message.hypixelscout.lookup.hint"));
		lookupBox.setMaxLength(16);
		addRenderableWidget(lookupBox);
		setInitialFocus(lookupBox);

		addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.lookup.go"),
						button -> lookUp(lookupBox.getValue()))
				.bounds(x + boxWidth + 4, y, 80, 20).build());
	}

	private void lookUp(String name) {
		String wanted = name == null ? "" : name.trim();
		if (!wanted.matches("\\w{1,16}")) {
			return;
		}

		mod.lookups().add(wanted);
		mod.saveSettings();
		minecraft.gui.setScreen(mod.profileScreen(wanted, roster.uuidOf(wanted), this));
	}

	/** The recent lookups, as faces you can click. */
	private List<ScreenRectangle> recentSlots(List<String> names) {
		List<ScreenRectangle> slots = new ArrayList<>();
		int slotWidth = 110;
		int perRow = Math.max(1, (contentWidth() + 6) / (slotWidth + 6));
		int total = Math.min(names.size(), perRow) * (slotWidth + 6) - 6;
		int left = width / 2 - total / 2;
		int top = contentTop + 62;

		for (int i = 0; i < names.size(); i++) {
			int y = top + (i / perRow) * 26;
			if (y + 22 > contentBottom) {
				break;
			}
			slots.add(new ScreenRectangle(left + (i % perRow) * (slotWidth + 6), y, slotWidth, 22));
		}
		return slots;
	}

	private void drawLookup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		ScoutTheme.textCentred(g, "§7" + I18n.get("message.hypixelscout.lookup.caption"), width / 2, contentTop + 2,
				ScoutTheme.TEXT);

		List<String> names = mod.lookups().names();
		if (names.isEmpty()) {
			ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.lookup.none"), width / 2, contentTop + 62,
					ScoutTheme.TEXT_FAINT);
			return;
		}

		ScoutTheme.textCentred(g, I18n.get("message.hypixelscout.lookup.recent"), width / 2, contentTop + 48,
				ScoutTheme.TEXT_FAINT);

		List<ScreenRectangle> slots = recentSlots(names);
		for (int i = 0; i < slots.size(); i++) {
			ScreenRectangle slot = slots.get(i);
			boolean hover = slot.containsPoint(mouseX, mouseY);
			ScoutTheme.panel(g, slot.left(), slot.top(), slot.width(), slot.height(), hover ? 95 : 70);
			if (hover) {
				ScoutTheme.rounded(g, slot.left(), slot.top(), slot.width(), slot.height(), ScoutTheme.HOVER);
			}
			Heads.drawByName(g, names.get(i), slot.left() + 4, slot.top() + 3, HEAD);
			ScoutTheme.text(g, ScoutTheme.fit(names.get(i), slot.width() - 30), slot.left() + 25, slot.top() + 7,
					hover ? ScoutTheme.accent() : ScoutTheme.TEXT);
		}
	}

	private boolean clickRecent(double mouseX, double mouseY) {
		List<String> names = mod.lookups().names();
		List<ScreenRectangle> slots = recentSlots(names);
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).containsPoint((int) mouseX, (int) mouseY)) {
				lookUp(names.get(i));
				return true;
			}
		}
		return false;
	}

	// --- queue --------------------------------------------------------------------------------

	/** Where each queue slot sits: one column where the window is tall enough, two where not. */
	private record QueueGrid(int columns, int left, int top, int rowHeight, int modeWidth, int columnWidth) {
		int x(int slot) {
			return left + (slot / perColumn()) * (columnWidth + 12);
		}

		int y(int slot) {
			return top + (slot % perColumn()) * rowHeight;
		}

		int perColumn() {
			return (ScoutSettings.QUEUE_SLOTS + columns - 1) / columns;
		}

		int bottom() {
			return top + perColumn() * rowHeight;
		}

		int width() {
			return columns * columnWidth + (columns - 1) * 12;
		}
	}

	/** Room for the key a slot is bound to, in front of it. */
	private static final int BADGE = 50;

	private QueueGrid queueGrid() {
		int top = contentTop + 14;
		int room = contentBottom - top - 30;
		int columns = room >= ScoutSettings.QUEUE_SLOTS * 22 ? 1 : 2;
		int modeWidth = columns == 1 ? 170 : Math.min(130, (contentWidth() - 12) / 2 - BADGE - 4 - 48);
		int columnWidth = BADGE + modeWidth + 4 + 48;
		int rowHeight = columns == 1 ? 22 : Math.clamp(room / 5, 21, 24);
		return new QueueGrid(columns, width / 2 - (columns * columnWidth + (columns - 1) * 12) / 2, top, rowHeight,
				modeWidth, columnWidth);
	}

	private void initQueue() {
		String[] slots = settings().queue.slots;
		QueueGrid grid = queueGrid();

		for (int slot = 0; slot < slots.length; slot++) {
			int index = slot;
			String current = BedwarsModes.QUEUEABLE.contains(slots[slot]) ? slots[slot] : "";
			int x = grid.x(slot) + BADGE;
			int y = grid.y(slot);

			addRenderableWidget(CycleButton.builder(ScoutScreen::modeLabel, current)
					.withValues(BedwarsModes.QUEUEABLE)
					.displayOnlyValue()
					.create(x, y, grid.modeWidth(), 20,
							Component.translatable("message.hypixelscout.queue.slot", slot + 1),
							(button, value) -> {
								settings().queue.slots[index] = value;
								mod.saveSettings();
								rebuildWidgets();
							}));

			Button play = addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.queue.play"),
							button -> {
								onClose();
								mod.queue().queueSlot(index);
							})
					.bounds(x + grid.modeWidth() + 4, y, 48, 20).build());
			play.active = !slots[slot].isBlank() && minecraft.getConnection() != null;
		}

		int y = grid.bottom() + 4;
		int half = (grid.width() - 4) / 2;
		Button random = addRenderableWidget(Button.builder(Component.translatable("message.hypixelscout.queue.random"),
						button -> {
							onClose();
							mod.queue().queueRandom();
						})
				.bounds(grid.left(), y, half, 20).build());
		random.active = minecraft.getConnection() != null;

		addRenderableWidget(CycleButton.onOffBuilder(settings().queue.onlyOnHypixel)
				.withTooltip(value -> Tooltip.create(
						Component.translatable("message.hypixelscout.settings.hypixel_only.tooltip")))
				.create(grid.left() + half + 4, y, grid.width() - half - 4, 20,
						Component.translatable("message.hypixelscout.settings.hypixel_only"),
						(button, value) -> {
							settings().queue.onlyOnHypixel = value;
							mod.saveSettings();
						}));
	}

	private static Component modeLabel(String mode) {
		return mode.isBlank()
				? Component.translatable("message.hypixelscout.queue.unused").withColor(0x808080)
				: Component.literal(BedwarsModes.shortName(mode));
	}

	private void drawQueue(GuiGraphicsExtractor g) {
		ScoutTheme.textCentred(g, "§7" + I18n.get("message.hypixelscout.queue.caption"), width / 2, contentTop + 2,
				ScoutTheme.TEXT);

		QueueGrid grid = queueGrid();
		for (int slot = 0; slot < settings().queue.slots.length; slot++) {
			boolean bound = mod.keys().queueKeyBound(slot);
			String key = bound ? mod.keys().queueKey(slot).getString() : I18n.get("message.hypixelscout.queue.no_key");
			key = ScoutTheme.fit(key, BADGE - 10);
			int badge = ScoutTheme.width(key) + 6;
			ScoutTheme.badge(g, key, grid.x(slot) + BADGE - 4 - badge, grid.y(slot) + 5, 0x50000000,
					bound ? ScoutTheme.accent() : ScoutTheme.TEXT_FAINT);
		}
	}

	// --- frame ----------------------------------------------------------------------------------

	@Override
	public void tick() {
		syncRows();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		g.blit(RenderPipelines.GUI_TEXTURED, Screen.INWORLD_FOOTER_SEPARATOR, 0, height - FOOTER - 2, 0.0f, 0.0f,
				width, 2, 32, 2);

		switch (page) {
			case GAME -> drawGame(g, mouseX, mouseY);
			case TEAMS -> drawTeams(g);
			case LOOKUP -> drawLookup(g, mouseX, mouseY);
			case QUEUE -> drawQueue(g);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}

		return switch (page) {
			case GAME -> clickColumnHeader(event.x(), event.y());
			case LOOKUP -> clickRecent(event.x(), event.y());
			default -> false;
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		if (page == Page.TEAMS && deltaY != 0) {
			teamScroll -= (int) Math.round(deltaY * 20);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (page == Page.LOOKUP && lookupBox != null && lookupBox.isFocused()
				&& (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			lookUp(lookupBox.getValue());
			return true;
		}

		// Ctrl+Tab and the tab bar's own keys switch tabs, as in vanilla's tabbed menus.
		if (tabBar != null && tabBar.keyPressed(event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		// You are usually still in a match; opening the list must not stop the game around you.
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** A tab of the bar. Its content is laid out by the screen, which knows what each page needs. */
	private record PageTab(Page page) implements Tab {
		private static final Layout EMPTY = LinearLayout.vertical();

		@Override
		public Component getTabTitle() {
			return page.title();
		}

		@Override
		public Component getTabExtraNarration() {
			return CommonComponents.EMPTY;
		}

		@Override
		public void visitChildren(Consumer<net.minecraft.client.gui.components.AbstractWidget> consumer) {
		}

		@Override
		public void doLayout(ScreenRectangle area) {
		}

		@Override
		public Layout getLayout() {
			return EMPTY;
		}
	}

	/** For the client game test: the rows the game tab is showing right now. */
	public int shownRows() {
		return gameList == null ? 0 : gameList.children().size();
	}

	/** For the client game test: the UUID in a given row of the game tab. */
	public UUID rowUuid(int index) {
		return gameList.children().get(index).row.uuid();
	}
}
