package de.raindancer118.hypixelscout.ui.screen;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.hypixelscout.core.BedLedger;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.TeamReport;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Column;
import de.raindancer118.hypixelscout.ui.Heads;
import de.raindancer118.hypixelscout.ui.PlayerRow;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Threats;
import de.raindancer118.hypixelscout.ui.hud.HazardElement;
import de.raindancer118.hypixelscout.ui.widget.ActionButton;
import de.raindancer118.hypixelscout.ui.widget.CycleButton;
import de.raindancer118.hypixelscout.ui.widget.Scissor;
import de.raindancer118.hypixelscout.ui.widget.TabBar;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The mod's front door: everybody in the game, the teams they make up, a lookup for anybody at
 * all, the queue slots and the suspected cheaters — five tabs of their own, drawn under a
 * {@link TabBar} rather than vanilla's {@code MenuTabBar} (26.2's own, which 1.8.9 has nothing
 * like — see that class's own documentation).
 *
 * <p>Ported from 26.2's {@code ui.screen.ScoutScreen}. Nothing here waits on the network: rows are
 * rebuilt from the roster and the cache every tick ({@link #updateScreen}), which is also what
 * fills them in as the lookups come back while the screen is open. The game list and the team
 * cards have no vanilla scrolling widget to build on ({@code ObjectSelectionList} is a 26.2 class);
 * both keep a plain scroll offset instead, moved by {@link #handleMouseInput}'s own {@code
 * Mouse.getEventDWheel()} check, and clip with {@link Scissor}.
 */
public final class ScoutScreen extends GuiScreen {
	/** The tabs, in the order the bar shows them. */
	public enum Page {
		GAME, TEAMS, LOOKUP, QUEUE, CHEATS;

		String title() {
			return StatCollector.translateToLocal("message.hypixelscout.page." + name().toLowerCase(Locale.ROOT));
		}
	}

	private static final int FOOTER = 33;
	private static final int ROW = 24;
	private static final int HEAD = 16;
	private static final int GAP = 12;
	private static final int MAX_CONTENT = 470;

	private final HypixelScout mod;
	private final GuiScreen parent;
	private final Roster roster;
	private final StatsService stats;
	private final CheatsPage cheatsPage;

	private Page page;
	private TabBar tabBar;
	private int contentTop;
	private int contentBottom;
	private int nextId;

	private List<PlayerRow> gameRows = new ArrayList<PlayerRow>();
	private List<Column> columns = new ArrayList<Column>();
	private int[] columnWidths = new int[0];
	private int gameScroll;

	private GuiTextField lookupBox;
	private int teamScroll;

	public ScoutScreen(HypixelScout mod, GuiScreen parent) {
		this(mod, parent, Page.GAME);
	}

	public ScoutScreen(HypixelScout mod, GuiScreen parent, Page page) {
		this.mod = mod;
		this.parent = parent;
		this.roster = mod.roster();
		this.stats = mod.stats();
		this.cheatsPage = new CheatsPage(mod, this);
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

	private int contentWidth() {
		return Math.min(width - 24, MAX_CONTENT);
	}

	private int contentLeft() {
		return (width - contentWidth()) / 2;
	}

	// --- layout -------------------------------------------------------------------------------

	@Override
	public void initGui() {
		buttonList.clear();
		nextId = 0;

		String[] labels = new String[Page.values().length];
		for (int i = 0; i < labels.length; i++) {
			labels[i] = Page.values()[i].title();
		}
		tabBar = new TabBar(labels, page.ordinal(), new java.util.function.IntConsumer() {
			@Override
			public void accept(int value) {
				page = Page.values()[value];
				teamScroll = 0;
				gameScroll = 0;
				initGui();
			}
		});
		tabBar.layout(0, 0, width);
		contentTop = tabBar.bottom() + 6;
		contentBottom = height - FOOTER;

		lookupBox = null;

		switch (page) {
			case GAME:
				initGame();
				break;
			case TEAMS:
				initTeams();
				break;
			case LOOKUP:
				initLookup();
				break;
			case QUEUE:
				initQueue();
				break;
			case CHEATS:
				cheatsPage.init(contentLeft(), contentTop, contentWidth(), contentBottom,
						new java.util.function.IntSupplier() {
							@Override
							public int getAsInt() {
								return nextId++;
							}
						}, new java.util.function.Consumer<GuiButton>() {
							@Override
							public void accept(GuiButton button) {
								buttonList.add(button);
							}
						});
				break;
			default:
		}

		int footerY = height - FOOTER + 7;
		buttonList.add(new ActionButton(nextId++, width / 2 - 154, footerY, 100, 20,
				StatCollector.translateToLocal("message.hypixelscout.settings"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(mod.settingsScreen(ScoutScreen.this));
					}
				}));
		buttonList.add(new ActionButton(nextId++, width / 2 - 50, footerY, 100, 20,
				StatCollector.translateToLocal("message.hypixelscout.move_table"), new Runnable() {
					@Override
					public void run() {
						mc.displayGuiScreen(mod.tableEditor(ScoutScreen.this));
					}
				}));
		buttonList.add(new ActionButton(nextId++, width / 2 + 54, footerY, 100, 20,
				StatCollector.translateToLocal("gui.done"), new Runnable() {
					@Override
					public void run() {
						ScoutScreen.this.onGuiClosed();
						mc.displayGuiScreen(parent);
					}
				}));
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button instanceof de.raindancer118.hypixelscout.ui.widget.Clickable) {
			((de.raindancer118.hypixelscout.ui.widget.Clickable) button).onClick();
		}
	}

	// --- game ---------------------------------------------------------------------------------

	private void initGame() {
		if (!hasKey()) {
			buttonList.add(new ActionButton(nextId++, width / 2 - 75, (contentTop + contentBottom) / 2 + 14, 150, 20,
					StatCollector.translateToLocal("message.hypixelscout.game.set_key"), new Runnable() {
						@Override
						public void run() {
							mc.displayGuiScreen(mod.settingsScreen(ScoutScreen.this));
						}
					}));
			return;
		}

		String refreshKey = roster.isInGame() && !roster.hasStarted()
				? "message.hypixelscout.game.look_up_now" : "message.hypixelscout.refresh";
		buttonList.add(new ActionButton(nextId++, contentLeft() + contentWidth() - 70, contentTop - 2, 70, 16,
				StatCollector.translateToLocal(refreshKey), new Runnable() {
					@Override
					public void run() {
						mod.refresh();
						syncRows();
					}
				}));

		syncRows();
	}

	private void syncRows() {
		gameRows = PlayerRow.all(roster, stats, settings(), settings().table.sort, Integer.MAX_VALUE);
		columns = Column.full(settings());
		columnWidths = new int[columns.size()];

		FontRenderer font = fontRendererObj;
		for (int i = 0; i < columns.size(); i++) {
			columnWidths[i] = ScoutTheme.width(font, StatCollector.translateToLocal(columns.get(i).translationKey())) + 4;
			for (PlayerRow row : gameRows) {
				columnWidths[i] = Math.max(columnWidths[i], ScoutTheme.width(font, columns.get(i).text(row)) + 4);
			}
		}
	}

	private int[] columnRights(int right) {
		int[] rights = new int[columns.size()];
		int edge = right;
		for (int i = rights.length - 1; i >= 0; i--) {
			rights[i] = edge;
			edge -= columnWidths[i] + GAP;
		}
		return rights;
	}

	private int gameListTop() {
		return contentTop + 30;
	}

	private void drawGame(FontRenderer font, int mouseX, int mouseY) {
		int left = contentLeft();
		int right = left + contentWidth();

		if (!hasKey()) {
			int middle = (contentTop + contentBottom) / 2;
			ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.game.no_key"),
					width / 2, middle - 14, ScoutTheme.TEXT);
			ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.game.no_key.hint"),
					width / 2, middle - 2, ScoutTheme.TEXT_DIM);
			return;
		}

		String where = roster.isInGame()
				? (roster.hasStarted() ? "" : "§e" + StatCollector.translateToLocal("message.hypixelscout.game.lobby.title") + " §8· ")
						+ "§f" + roster.map() + " §7" + BedwarsModes.shortName(roster.mode()) + " §8· §7"
						+ StatCollector.translateToLocalFormatted("message.hypixelscout.table.players", roster.members().size())
				: "§7" + StatCollector.translateToLocal("message.hypixelscout.game.not_in_game.short");
		String budget = "§8" + StatCollector.translateToLocalFormatted("message.hypixelscout.game.budget",
				mod.client().getLimiter().remaining(), mod.client().getLimiter().limit());
		ScoutTheme.textRight(font, budget, right - 76, contentTop + 2, ScoutTheme.TEXT);
		ScoutTheme.text(font, ScoutTheme.fit(font, where, contentWidth() - 76 - ScoutTheme.width(font, budget) - 10),
				left, contentTop + 2, ScoutTheme.TEXT);

		if (gameRows.isEmpty()) {
			String line = roster.isInGame() ? "message.hypixelscout.game.waiting" : "message.hypixelscout.game.not_in_game";
			int middle = (contentTop + contentBottom) / 2;
			ScoutTheme.textCentred(font, StatCollector.translateToLocal(line), width / 2, middle - 6, ScoutTheme.TEXT);
			ScoutTheme.textCentred(font, StatCollector.translateToLocal(line + ".hint"), width / 2, middle + 6, ScoutTheme.TEXT_DIM);
			return;
		}

		drawColumnHeader(font, mouseX, mouseY);
		drawGameList(font, mouseX, mouseY);
	}

	private void drawColumnHeader(FontRenderer font, int mouseX, int mouseY) {
		int rowLeft = contentLeft();
		int rowRight = rowLeft + contentWidth();
		int y = contentTop + 17;
		SortMode sort = settings().table.sort;

		String player = StatCollector.translateToLocal("message.hypixelscout.column.player");
		int playerX = rowLeft + 5 + HEAD + 6;
		boolean playerHover = mouseY >= y - 2 && mouseY < y + 10 && mouseX >= playerX
				&& mouseX < playerX + ScoutTheme.width(font, player);
		ScoutTheme.text(font, player + (sort == SortMode.NAME ? " ▾" : ""), playerX, y,
				sort == SortMode.NAME ? ScoutTheme.accent() : playerHover ? ScoutTheme.TEXT : ScoutTheme.TEXT_FAINT);

		int[] rights = columnRights(rowRight - 4);
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			String label = StatCollector.translateToLocal(column.translationKey());
			boolean active = column.sort() != null && column.sort() == sort;
			boolean hover = column.sort() != null && mouseY >= y - 2 && mouseY < y + 10
					&& mouseX >= rights[i] - columnWidths[i] && mouseX < rights[i];
			ScoutTheme.textRight(font, (active ? "▾ " : "") + label, rights[i], y,
					active ? ScoutTheme.accent() : hover ? ScoutTheme.TEXT : ScoutTheme.TEXT_FAINT);
		}

		ScoutTheme.divider(rowLeft, y + 11, rowRight - rowLeft);
	}

	private boolean clickColumnHeader(int mouseX, int mouseY) {
		if (gameRows.isEmpty()) {
			return false;
		}

		int y = contentTop + 17;
		if (mouseY < y - 2 || mouseY >= y + 10) {
			return false;
		}

		int rowLeft = contentLeft();
		int rowRight = rowLeft + contentWidth();
		int playerX = rowLeft + 5 + HEAD + 6;
		if (mouseX >= playerX && mouseX < playerX + ScoutTheme.width(fontRendererObj,
				StatCollector.translateToLocal("message.hypixelscout.column.player"))) {
			sortBy(SortMode.NAME);
			return true;
		}

		int[] rights = columnRights(rowRight - 4);
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).sort() != null && mouseX >= rights[i] - columnWidths[i] && mouseX < rights[i]) {
				sortBy(columns.get(i).sort());
				return true;
			}
		}

		sortBy(SortMode.STARS);
		return true;
	}

	private void sortBy(SortMode sort) {
		settings().table.sort = sort;
		mod.saveSettings();
		syncRows();
	}

	private int gameContentHeight() {
		return gameRows.size() * ROW;
	}

	private void drawGameList(FontRenderer font, int mouseX, int mouseY) {
		int left = contentLeft();
		int right = left + contentWidth();
		int listTop = gameListTop();
		int listBottom = contentBottom - 2;
		int max = Math.max(0, gameContentHeight() - (listBottom - listTop));
		gameScroll = clamp(gameScroll, 0, max);

		Scissor.enable(left, listTop, right - left, listBottom - listTop);
		int y = listTop - gameScroll;
		for (PlayerRow row : gameRows) {
			if (y + ROW > listTop && y < listBottom) {
				drawGameRow(font, row, left, y, right, mouseX, mouseY);
			}
			y += ROW;
		}
		Scissor.disable();
	}

	private void drawGameRow(FontRenderer font, PlayerRow row, int x, int y, int right, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW;
		if (hover) {
			Gui.drawRect(x - 2, y - 1, right + 2, y + ROW + 1, ScoutTheme.HOVER);
		}

		ScoutTheme.pill(x, y + 1, 3, ROW - 2, row.team().argb());
		Heads.draw(row.uuid(), x + 6, y + (ROW - HEAD) / 2, HEAD);

		int[] rights = columnRights(right - 4);
		int nameX = x + 5 + HEAD + 6;
		int nameRoom = (rights.length == 0 ? right : rights[0] - columnWidths[0]) - nameX - 6;

		ScoutTheme.text(font, ScoutTheme.fit(font, row.displayName(), nameRoom), nameX, y + 2, ScoutTheme.TEXT);
		ScoutTheme.text(font, ScoutTheme.fit(font, subtitle(row), nameRoom), nameX, y + 12, ScoutTheme.TEXT_FAINT);

		for (int i = 0; i < columns.size(); i++) {
			ScoutTheme.textRight(font, columns.get(i).text(row), rights[i], y + 7, ScoutTheme.TEXT);
		}
	}

	private boolean clickGameRow(int mouseX, int mouseY) {
		int left = contentLeft();
		int right = left + contentWidth();
		int listTop = gameListTop();
		int listBottom = contentBottom - 2;
		if (mouseX < left || mouseX >= right || mouseY < listTop || mouseY >= listBottom) {
			return false;
		}

		int index = (mouseY - listTop + gameScroll) / ROW;
		if (index >= 0 && index < gameRows.size()) {
			mc.displayGuiScreen(mod.profileScreen(gameRows.get(index).name(), gameRows.get(index).uuid(), this));
			return true;
		}
		return false;
	}

	private String subtitle(PlayerRow row) {
		PlayerStats playerStats = row.stats();
		if (playerStats == null && !roster.hasStarted()) {
			return StatCollector.translateToLocal("message.hypixelscout.game.waiting_for_start");
		}
		if (playerStats == null) {
			return row.pending() || row.failure() == null
					? StatCollector.translateToLocal("message.hypixelscout.game.looking_up") : "§c" + row.failure();
		}
		if (playerStats.isNicked()) {
			return "§d" + StatCollector.translateToLocal("message.hypixelscout.game.nicked");
		}

		String team = row.team().name().isEmpty() ? "" : row.team().name() + " · ";
		return team + StatCollector.translateToLocalFormatted("message.hypixelscout.game.subtitle",
				(int) playerStats.getNetworkLevel(), StatFormat.age(playerStats.getFirstLogin(), System.currentTimeMillis()),
				StatFormat.count(playerStats.getWins()));
	}

	// --- teams --------------------------------------------------------------------------------

	private void initTeams() {
		int right = contentLeft() + contentWidth();
		for (final PartyReport.Channel channel : PartyReport.Channel.values()) {
			String key = "message.hypixelscout.teams." + channel.name().toLowerCase(Locale.ROOT);
			GuiButton report = new ActionButton(nextId++, channel == PartyReport.Channel.TEAM ? right - 164 : right - 80,
					contentTop - 2, 80, 16, StatCollector.translateToLocal(key), new Runnable() {
						@Override
						public void run() {
							mod.partyReport().send(channel);
						}
					});
			report.enabled = mod.partyReport().canSend(channel);
			buttonList.add(report);

			String listKey = "message.hypixelscout.teams.list." + channel.name().toLowerCase(Locale.ROOT);
			GuiButton list = new ActionButton(nextId++, channel == PartyReport.Channel.TEAM ? right - 332 : right - 248,
					contentTop - 2, 80, 16, StatCollector.translateToLocal(listKey), new Runnable() {
						@Override
						public void run() {
							mod.partyReport().sendAll(channel);
						}
					});
			list.enabled = mod.partyReport().canSend(channel);
			buttonList.add(list);
		}
	}

	private static final class TeamGroup {
		private final Teams.Team team;
		private final List<PlayerRow> rows;
		private final TeamReport report;
		private final boolean own;

		TeamGroup(Teams.Team team, List<PlayerRow> rows, TeamReport report, boolean own) {
			this.team = team;
			this.rows = rows;
			this.report = report;
			this.own = own;
		}
	}

	private List<TeamGroup> teamGroups() {
		Map<String, List<PlayerRow>> byTeam = new LinkedHashMap<String, List<PlayerRow>>();
		ScoutSettings grouped = groupedSettings();
		for (PlayerRow row : PlayerRow.all(roster, stats, grouped, SortMode.STARS, Integer.MAX_VALUE)) {
			List<PlayerRow> list = byTeam.get(row.team().name());
			if (list == null) {
				list = new ArrayList<PlayerRow>();
				byTeam.put(row.team().name(), list);
			}
			list.add(row);
		}

		Teams.Team own = Teams.own();
		List<TeamGroup> groups = new ArrayList<TeamGroup>();
		for (Map.Entry<String, List<PlayerRow>> entry : byTeam.entrySet()) {
			List<PlayerRow> rows = entry.getValue();
			List<PlayerStats> playerStats = new ArrayList<PlayerStats>();
			for (PlayerRow row : rows) {
				playerStats.add(row.stats());
			}
			groups.add(new TeamGroup(rows.get(0).team(), rows, TeamReport.of(entry.getKey(), playerStats),
					own != Teams.NONE && own.equals(rows.get(0).team())));
		}

		java.util.Collections.sort(groups, new java.util.Comparator<TeamGroup>() {
			@Override
			public int compare(TeamGroup left, TeamGroup right) {
				if (left.own != right.own) {
					return left.own ? 1 : -1;
				}
				return Double.compare(danger(right), danger(left));
			}
		});
		return groups;
	}

	private static double danger(TeamGroup group) {
		ThreatScale scale = Threats.scale();
		double max = -1;
		for (PlayerRow row : group.rows) {
			max = Math.max(max, scale.score(row.stats()));
		}
		return max;
	}

	private ScoutSettings groupedSettings() {
		ScoutSettings copy = new ScoutSettings();
		copy.table.groupByTeam = true;
		copy.table.hideOwnTeam = false;
		return copy;
	}

	private void drawTeams(FontRenderer font) {
		int left = contentLeft();
		String caption = "§7" + StatCollector.translateToLocalFormatted("message.hypixelscout.teams.caption",
				StatCollector.translateToLocal("message.hypixelscout.threat_basis.short." + settings().threatBasis.name().toLowerCase(Locale.ROOT))
						+ ", " + StatCollector.translateToLocal("message.hypixelscout.threat_focus.short." + settings().threatFocus.name().toLowerCase(Locale.ROOT))
						+ (settings().threatSensitivity == 100 ? ""
								: ", " + StatCollector.translateToLocalFormatted("message.hypixelscout.threat_sensitivity.short", settings().threatSensitivity)));
		ScoutTheme.text(font, ScoutTheme.fit(font, caption, contentWidth() - 340), left, contentTop + 2, ScoutTheme.TEXT);

		List<TeamGroup> groups = teamGroups();
		if (groups.isEmpty()) {
			int middle = (contentTop + contentBottom) / 2;
			String line = !hasKey() ? "message.hypixelscout.game.no_key"
					: roster.isInGame() ? "message.hypixelscout.game.waiting" : "message.hypixelscout.game.not_in_game";
			ScoutTheme.textCentred(font, StatCollector.translateToLocal(line), width / 2, middle - 6, ScoutTheme.TEXT);
			return;
		}

		int columnsCount = contentWidth() >= 380 ? 2 : 1;
		int gap = 8;
		int cardWidth = (contentWidth() - gap * (columnsCount - 1)) / columnsCount;
		int largest = 1;
		for (TeamGroup group : groups) {
			largest = Math.max(largest, group.rows.size());
		}
		int cardHeight = ScoutTheme.HEADER_HEIGHT + 6 + largest * 11 + 22 + 16;

		int top = contentTop + 18;
		int rows = (groups.size() + columnsCount - 1) / columnsCount;
		int total = rows * (cardHeight + gap) - gap;
		teamScroll = clamp(teamScroll, 0, Math.max(0, total - (contentBottom - top - 4)));

		Scissor.enable(0, top, width, contentBottom - top - 2);
		for (int i = 0; i < groups.size(); i++) {
			int x = left + (i % columnsCount) * (cardWidth + gap);
			int y = top + (i / columnsCount) * (cardHeight + gap) - teamScroll;
			if (y + cardHeight > top && y < contentBottom) {
				drawTeamCard(font, groups.get(i), x, y, cardWidth, cardHeight);
			}
		}
		Scissor.disable();
	}

	private void drawTeamCard(FontRenderer font, TeamGroup group, int x, int y, int width, int height) {
		ScoutTheme.panel(x, y, width, height, 80);
		ScoutTheme.rounded(x + 1, y + 1, width - 2, ScoutTheme.HEADER_HEIGHT - 1,
				(0x40 << 24) | (group.team.rgb() & 0xFFFFFF));
		Gui.drawRect(x + 1, y + ScoutTheme.HEADER_HEIGHT, x + width - 1, y + ScoutTheme.HEADER_HEIGHT + 1, group.team.argb());

		String name = group.team.name().isEmpty() ? StatCollector.translateToLocal("message.hypixelscout.teams.no_team") : group.team.name();
		ScoutTheme.text(font, "§l" + name + (group.own ? " §r§7" + StatCollector.translateToLocal("message.hypixelscout.teams.you") : ""),
				x + 6, y + 6, 0xFF000000 | group.team.rgb());

		if (!group.own) {
			Threat threat = Threat.UNKNOWN;
			for (PlayerRow row : group.rows) {
				Threat candidate = Threats.of(row.stats());
				if (threat == Threat.UNKNOWN || candidate.compareTo(threat) > 0) {
					threat = candidate;
				}
			}
			String badge = threat.colour() + threat.label();
			ScoutTheme.badge(font, badge, x + width - 6 - ScoutTheme.width(font, badge) - 6, y + 5, 0x60000000, ScoutTheme.TEXT);
		}

		int rowY = y + ScoutTheme.HEADER_HEIGHT + 5;
		for (PlayerRow row : group.rows) {
			Heads.draw(row.uuid(), x + 6, rowY, 8);
			String fkdr = Column.FKDR.text(row);
			ScoutTheme.text(font, ScoutTheme.fit(font, row.displayName(), width - 30 - ScoutTheme.width(font, fkdr)), x + 18, rowY, ScoutTheme.TEXT);
			ScoutTheme.textRight(font, fkdr, x + width - 6, rowY, ScoutTheme.TEXT);
			rowY += 11;
		}

		if (!group.team.name().isEmpty()) {
			drawBed(font, group.team.name(), x + 6, y + height - 38, width - 12);
		}

		TeamReport report = group.report;
		ScoutTheme.divider(x + 6, y + height - 15, width - 12);
		String stars = "§f" + report.getCombinedStars() + "✫";
		List<TeamReport.Streak> streaks = report.streaksAbove(settings().alerts.streakThreshold);
		if (!streaks.isEmpty()) {
			stars += " §c" + streaks.get(0).getWinstreak() + " WS";
		}
		ScoutTheme.text(font, stars, x + 6, y + height - 11, ScoutTheme.TEXT);
		ScoutTheme.textRight(font, "§7FKDR §f" + StatFormat.ratio(report.getCombinedFkdr()) + " §7WLR §f"
				+ StatFormat.ratio(report.getCombinedWlr()), x + width - 6, y + height - 11, ScoutTheme.TEXT);
	}

	private void drawBed(FontRenderer font, String team, int x, int y, int width) {
		String label = "§7" + StatCollector.translateToLocal("message.hypixelscout.teams.bed");
		BedLedger.Entry entry = mod.hazards().ledger().of(team).orElse(null);
		String detail;
		if (entry == null) {
			detail = "§8" + StatCollector.translateToLocal("message.hypixelscout.teams.bed.unseen");
		} else if (entry.gone()) {
			label += " §c§l" + StatCollector.translateToLocal("message.hypixelscout.teams.bed.gone");
			detail = "§8" + StatLines.plain(layersText(entry));
		} else {
			detail = layersText(entry);
			ScoutTheme.textRight(font, "§8" + StatCollector.translateToLocalFormatted("message.hypixelscout.teams.bed.ago",
					ago(entry.ageMillis(System.currentTimeMillis()))), x + width, y, ScoutTheme.TEXT);
		}
		ScoutTheme.text(font, label, x, y, ScoutTheme.TEXT);
		ScoutTheme.text(font, ScoutTheme.fit(font, detail, width), x, y + 11, ScoutTheme.TEXT);
	}

	private static String layersText(BedLedger.Entry entry) {
		List<BedDefense.Layer> layers = entry.layers();
		if (layers.isEmpty()) {
			return entry.report().open() ? "§c§l" + StatCollector.translateToLocal("message.hypixelscout.hazard.bed_open")
					: HazardElement.defenceText(entry.report());
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < layers.size(); i++) {
			BedDefense.Layer layer = layers.get(i);
			if (i > 0) {
				text.append(" §8› ");
			}
			text.append(layer.depth() == 1 ? "§e" : "§f").append(layer.material());
		}
		if (BedDefense.unknownInside(layers)) {
			text.append(" §8› §7?");
		}
		return entry.report().open()
				? "§c" + StatCollector.translateToLocal("message.hypixelscout.hazard.bed_open") + " §8· " + text
				: text.toString();
	}

	/** {@code 45s}, {@code 3m}, {@code 1h 5m}. */
	static String ago(long millis) {
		long seconds = millis / 1000;
		if (seconds < 60) {
			return seconds + "s";
		}
		long minutes = seconds / 60;
		return minutes < 60 ? minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	// --- lookup -------------------------------------------------------------------------------

	private void initLookup() {
		int boxWidth = Math.min(240, contentWidth() - 90);
		int x = width / 2 - (boxWidth + 84) / 2;
		int y = contentTop + 14;

		lookupBox = new GuiTextField(nextId++, fontRendererObj, x, y, boxWidth, 20);
		lookupBox.setMaxStringLength(16);
		lookupBox.setFocused(true);

		buttonList.add(new ActionButton(nextId++, x + boxWidth + 4, y, 80, 20,
				StatCollector.translateToLocal("message.hypixelscout.lookup.go"), new Runnable() {
					@Override
					public void run() {
						lookUp(lookupBox.getText());
					}
				}));
	}

	private void lookUp(String name) {
		String wanted = name == null ? "" : name.trim();
		if (!wanted.matches("\\w{1,16}")) {
			return;
		}

		mod.lookups().add(wanted);
		mod.saveSettings();
		mc.displayGuiScreen(mod.profileScreen(wanted, roster.uuidOf(wanted), this));
	}

	private static final class Slot {
		final int x;
		final int y;
		final int width;
		final int height;

		Slot(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		boolean contains(int mx, int my) {
			return mx >= x && mx < x + width && my >= y && my < y + height;
		}
	}

	private List<Slot> recentSlots(List<String> names) {
		List<Slot> slots = new ArrayList<Slot>();
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
			slots.add(new Slot(left + (i % perRow) * (slotWidth + 6), y, slotWidth, 22));
		}
		return slots;
	}

	private void drawLookup(FontRenderer font, int mouseX, int mouseY) {
		ScoutTheme.textCentred(font, "§7" + StatCollector.translateToLocal("message.hypixelscout.lookup.caption"),
				width / 2, contentTop + 2, ScoutTheme.TEXT);

		List<String> names = mod.lookups().names();
		if (names.isEmpty()) {
			ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.lookup.none"), width / 2,
					contentTop + 62, ScoutTheme.TEXT_FAINT);
			return;
		}

		ScoutTheme.textCentred(font, StatCollector.translateToLocal("message.hypixelscout.lookup.recent"), width / 2,
				contentTop + 48, ScoutTheme.TEXT_FAINT);

		List<Slot> slots = recentSlots(names);
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			boolean hover = slot.contains(mouseX, mouseY);
			ScoutTheme.panel(slot.x, slot.y, slot.width, slot.height, hover ? 95 : 70);
			if (hover) {
				ScoutTheme.rounded(slot.x, slot.y, slot.width, slot.height, ScoutTheme.HOVER);
			}
			Heads.drawByName(names.get(i), slot.x + 4, slot.y + 3, HEAD);
			ScoutTheme.text(font, ScoutTheme.fit(font, names.get(i), slot.width - 30), slot.x + 25, slot.y + 7,
					hover ? ScoutTheme.accent() : ScoutTheme.TEXT);
		}
	}

	private boolean clickRecent(int mouseX, int mouseY) {
		List<String> names = mod.lookups().names();
		List<Slot> slots = recentSlots(names);
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).contains(mouseX, mouseY)) {
				lookUp(names.get(i));
				return true;
			}
		}
		return false;
	}

	// --- queue --------------------------------------------------------------------------------

	private static final int BADGE = 50;

	private final class QueueGrid {
		final int columnsCount;
		final int left;
		final int top;
		final int rowHeight;
		final int modeWidth;
		final int columnWidth;

		QueueGrid() {
			top = contentTop + 14;
			int room = contentBottom - top - 30;
			columnsCount = room >= ScoutSettings.QUEUE_SLOTS * 22 ? 1 : 2;
			modeWidth = columnsCount == 1 ? 170 : Math.min(130, (contentWidth() - 12) / 2 - BADGE - 4 - 48);
			columnWidth = BADGE + modeWidth + 4 + 48;
			rowHeight = columnsCount == 1 ? 22 : clamp(room / 5, 21, 24);
			left = width / 2 - (columnsCount * columnWidth + (columnsCount - 1) * 12) / 2;
		}

		int perColumn() {
			return (ScoutSettings.QUEUE_SLOTS + columnsCount - 1) / columnsCount;
		}

		int x(int slot) {
			return left + (slot / perColumn()) * (columnWidth + 12);
		}

		int y(int slot) {
			return top + (slot % perColumn()) * rowHeight;
		}

		int bottom() {
			return top + perColumn() * rowHeight;
		}

		int width() {
			return columnsCount * columnWidth + (columnsCount - 1) * 12;
		}
	}

	private void initQueue() {
		final String[] slots = settings().queue.slots;
		final QueueGrid grid = new QueueGrid();

		String[] modeValues = new String[BedwarsModes.QUEUEABLE.size() + 1];
		modeValues[0] = "";
		for (int i = 0; i < BedwarsModes.QUEUEABLE.size(); i++) {
			modeValues[i + 1] = BedwarsModes.QUEUEABLE.get(i);
		}

		for (int slot = 0; slot < slots.length; slot++) {
			final int index = slot;
			String current = BedwarsModes.QUEUEABLE.contains(slots[slot]) ? slots[slot] : "";
			int x = grid.x(slot) + BADGE;
			int y = grid.y(slot);

			buttonList.add(new CycleButton<String>(nextId++, x, y, grid.modeWidth, 20,
					"message.hypixelscout.queue.slot", new Object[] {slot + 1}, modeValues, current,
					new java.util.function.Function<String, String>() {
						@Override
						public String apply(String value) {
							return value.isEmpty() ? StatCollector.translateToLocal("message.hypixelscout.queue.unused")
									: BedwarsModes.shortName(value);
						}
					}, new java.util.function.Consumer<String>() {
						@Override
						public void accept(String value) {
							slots[index] = value;
							initGui();
						}
					}));

			GuiButton play = new ActionButton(nextId++, x + grid.modeWidth + 4, y, 48, 20,
					StatCollector.translateToLocal("message.hypixelscout.queue.play"), new Runnable() {
						@Override
						public void run() {
							onGuiClosed();
							mc.displayGuiScreen(null);
							mod.queue().queueSlot(index);
						}
					});
			play.enabled = !slots[slot].isEmpty() && mc.getNetHandler() != null;
			buttonList.add(play);
		}

		int y = grid.bottom() + 4;
		int half = (grid.width() - 4) / 2;
		GuiButton random = new ActionButton(nextId++, grid.left, y, half, 20,
				StatCollector.translateToLocal("message.hypixelscout.queue.random"), new Runnable() {
					@Override
					public void run() {
						onGuiClosed();
						mc.displayGuiScreen(null);
						mod.queue().queueRandom();
					}
				});
		random.enabled = mc.getNetHandler() != null;
		buttonList.add(random);

		buttonList.add(CycleButton.onOff(nextId++, grid.left + half + 4, y, grid.width() - half - 4, 20,
				"message.hypixelscout.settings.hypixel_only", settings().queue.onlyOnHypixel,
				new java.util.function.Consumer<Boolean>() {
					@Override
					public void accept(Boolean value) {
						settings().queue.onlyOnHypixel = value;
						mod.saveSettings();
					}
				}));
	}

	private void drawQueue(FontRenderer font) {
		ScoutTheme.textCentred(font, "§7" + StatCollector.translateToLocal("message.hypixelscout.queue.caption"),
				width / 2, contentTop + 2, ScoutTheme.TEXT);

		QueueGrid grid = new QueueGrid();
		for (int slot = 0; slot < settings().queue.slots.length; slot++) {
			boolean bound = mod.keys().queueKeyBound(slot);
			String key = bound ? mod.keys().queueKeyName(slot) : StatCollector.translateToLocal("message.hypixelscout.queue.no_key");
			key = ScoutTheme.fit(font, key, BADGE - 10);
			int badge = ScoutTheme.width(font, key) + 6;
			ScoutTheme.badge(font, key, grid.x(slot) + BADGE - 4 - badge, grid.y(slot) + 5, 0x50000000,
					bound ? ScoutTheme.accent() : ScoutTheme.TEXT_FAINT);
		}
	}

	// --- frame ----------------------------------------------------------------------------------

	@Override
	public void updateScreen() {
		if (page == Page.GAME) {
			syncRows();
		}
		if (page == Page.CHEATS) {
			cheatsPage.tick();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		drawDefaultBackground();
		tabBar.layout(0, 0, width);
		tabBar.draw(fontRendererObj, mouseX, mouseY);

		Gui.drawRect(0, height - FOOTER - 2, width, height - FOOTER, 0x60000000);

		switch (page) {
			case GAME:
				drawGame(fontRendererObj, mouseX, mouseY);
				break;
			case TEAMS:
				drawTeams(fontRendererObj);
				break;
			case LOOKUP:
				drawLookup(fontRendererObj, mouseX, mouseY);
				if (lookupBox != null) {
					lookupBox.drawTextBox();
				}
				break;
			case QUEUE:
				drawQueue(fontRendererObj);
				break;
			case CHEATS:
				cheatsPage.draw(fontRendererObj, mouseX, mouseY);
				break;
			default:
		}

		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		if (tabBar.mouseClicked(mouseX, mouseY)) {
			initGui();
			return;
		}

		if (page == Page.LOOKUP && lookupBox != null) {
			lookupBox.mouseClicked(mouseX, mouseY, mouseButton);
		}

		super.mouseClicked(mouseX, mouseY, mouseButton);

		if (mouseButton != 0) {
			return;
		}

		switch (page) {
			case GAME:
				if (!clickColumnHeader(mouseX, mouseY)) {
					clickGameRow(mouseX, mouseY);
				}
				break;
			case LOOKUP:
				clickRecent(mouseX, mouseY);
				break;
			case CHEATS:
				cheatsPage.mouseClicked(mouseX, mouseY);
				break;
			default:
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();

		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) {
			return;
		}
		int direction = wheel > 0 ? 1 : -1;

		if (page == Page.TEAMS) {
			teamScroll = clamp(teamScroll - direction * 16, 0, Integer.MAX_VALUE);
		} else if (page == Page.GAME) {
			gameScroll -= direction * ROW;
		} else if (page == Page.CHEATS) {
			cheatsPage.onScroll(direction);
		}
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : value > max ? max : value;
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (page == Page.LOOKUP && lookupBox != null && lookupBox.isFocused()) {
			if (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
				lookUp(lookupBox.getText());
				return;
			}
			if (lookupBox.textboxKeyTyped(typedChar, keyCode)) {
				return;
			}
		}

		if (tabBar.keyTyped(keyCode)) {
			initGui();
			return;
		}

		super.keyTyped(typedChar, keyCode);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	public void onGuiClosed() {
		mod.saveSettings();
	}

	/** For the client game test: the rows the game tab is showing right now. */
	public int shownRows() {
		return gameRows.size();
	}

	/** For the client game test: the suspects the cheats tab lists. */
	public int shownSuspects() {
		return cheatsPage.shownRows();
	}

	/** For the client game test: the UUID in a given row of the game tab. */
	public UUID rowUuid(int index) {
		return gameRows.get(index).uuid();
	}
}
