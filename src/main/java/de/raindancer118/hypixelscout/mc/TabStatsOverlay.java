package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.mc.ui.ScoutTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The stats inside the tab list itself, in place of the vanilla one.
 *
 * <p>Vanilla's player list is cancelled and this is drawn instead, rather than a Mixin being put
 * into {@code GuiPlayerTabOverlay}: the whole layout is then this class's to decide, columns line
 * up because they are laid out here, and there is no coremod to keep working across Forge builds.
 *
 * <p>What it costs is that the head and the ping have to be drawn by hand, which is the block of
 * texture work at the bottom.
 */
public final class TabStatsOverlay extends Gui {
	private static final int ROW_HEIGHT = ScoutTheme.ROW_HEIGHT;
	private static final int HEAD_SIZE = 9;
	private static final int PADDING = 8;

	private final RosterTracker roster;
	private final StatsService stats;
	private final ScoutConfig config;

	public TabStatsOverlay(RosterTracker roster, StatsService stats, ScoutConfig config) {
		this.roster = roster;
		this.stats = stats;
		this.config = config;
	}

	/**
	 * Cancels the vanilla list. {@code Pre} rather than {@code Post}, because {@code Post} runs
	 * after it has already been drawn and cancelling there achieves nothing.
	 */
	@SubscribeEvent
	public void onRenderPlayerList(RenderGameOverlayEvent.Pre event) {
		if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) {
			return;
		}

		if (!config.isTabStatsEnabled() || !roster.isInBedwars()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || !mc.gameSettings.keyBindPlayerList.isKeyDown()) {
			return;
		}

		event.setCanceled(true);
		draw(mc, new ScaledResolution(mc));
	}

	private void draw(Minecraft mc, ScaledResolution resolution) {
		List<RosterTracker.Member> members = roster.members();
		Collections.sort(members, byTeamThenStars());

		List<Row> rows = new ArrayList<Row>(members.size());
		int nameWidth = mc.fontRendererObj.getStringWidth("Player");
		int statsWidth = 0;

		for (RosterTracker.Member member : members) {
			PlayerStats playerStats = stats.peek(member.getUuid());
			Row row = new Row(member, nameOf(member, playerStats), statsOf(playerStats));

			nameWidth = Math.max(nameWidth, mc.fontRendererObj.getStringWidth(row.name));
			statsWidth = Math.max(statsWidth, mc.fontRendererObj.getStringWidth(row.stats));
			rows.add(row);
		}

		int contentWidth = HEAD_SIZE + 4 + nameWidth + 16 + statsWidth;
		int width = contentWidth + PADDING * 2;
		int height = ScoutTheme.HEADER_HEIGHT + PADDING + rows.size() * ROW_HEIGHT + PADDING;

		int left = (resolution.getScaledWidth() - width) / 2;
		int top = 10;

		ScoutTheme.panel(left, top, width, height);
		ScoutTheme.header(left, top, width);

		ScoutTheme.text("\u00a76\u00a7lSCOUT", left + PADDING, top + 7, ScoutTheme.TEXT);
		ScoutTheme.textRight(rows.size() + " \u00a78players", left + PADDING + contentWidth,
				top + 7, ScoutTheme.TEXT_DIM);

		int y = top + ScoutTheme.HEADER_HEIGHT + PADDING;
		boolean stripe = false;

		for (Row row : rows) {
			if (stripe) {
				ScoutTheme.fill(left + PADDING, y, contentWidth, ROW_HEIGHT, ScoutTheme.STRIPE);
			}
			stripe = !stripe;

			ScoutTheme.head(row.member.getUuid(), left + PADDING, y + 3, HEAD_SIZE);
			ScoutTheme.text(row.name, left + PADDING + HEAD_SIZE + 4, y + 4, ScoutTheme.TEXT);
			ScoutTheme.textRight(row.stats, left + PADDING + contentWidth, y + 4,
					ScoutTheme.TEXT);

			y += ROW_HEIGHT;
		}
	}

	private String nameOf(RosterTracker.Member member, PlayerStats playerStats) {
		String team = Teams.teamOf(member.getName());
		String prefix = team == null ? "" : "§8" + team.charAt(0) + " ";

		if (playerStats == null) {
			return prefix + "§7" + member.getName();
		}
		if (playerStats.isNicked()) {
			return prefix + "§d" + member.getName();
		}

		return prefix + StatFormat.star(playerStats.getStars()) + " "
				+ StatsLines.rankColour(playerStats.getRank()) + member.getName();
	}

	private String statsOf(PlayerStats playerStats) {
		if (playerStats == null) {
			return "§8…";
		}
		if (playerStats.isNicked()) {
			return "§dNICK";
		}

		StringBuilder text = new StringBuilder();
		text.append(StatFormat.ratioColour(playerStats.getFkdr()))
				.append(StatFormat.ratio(playerStats.getFkdr()));

		if (config.isShowWlr()) {
			text.append(" §8| ").append(StatFormat.ratioColour(playerStats.getWlr()))
					.append(StatFormat.ratio(playerStats.getWlr()));
		}
		if (config.isShowWinstreak()) {
			text.append(" §8| §f").append(StatFormat.winstreak(playerStats.getWinstreak()));
		}

		return text.toString();
	}

	/** Teams stay together, and inside a team the biggest star is at the top. */
	private Comparator<RosterTracker.Member> byTeamThenStars() {
		final StatsService service = stats;

		return new Comparator<RosterTracker.Member>() {
			@Override
			public int compare(RosterTracker.Member left, RosterTracker.Member right) {
				String leftTeam = String.valueOf(Teams.teamOf(left.getName()));
				String rightTeam = String.valueOf(Teams.teamOf(right.getName()));

				int byTeam = leftTeam.compareTo(rightTeam);
				if (byTeam != 0) {
					return byTeam;
				}

				return starsOf(service, right) - starsOf(service, left);
			}
		};
	}

	private static int starsOf(StatsService service, RosterTracker.Member member) {
		PlayerStats stats = service.peek(member.getUuid());
		return stats == null || stats.isNicked() ? -1 : stats.getStars();
	}

	private static final class Row {
		private final RosterTracker.Member member;
		private final String name;
		private final String stats;

		private Row(RosterTracker.Member member, String name, String stats) {
			this.member = member;
			this.name = name;
			this.stats = stats;
		}
	}
}
