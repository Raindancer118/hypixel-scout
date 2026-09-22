package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
	private static final int ROW_HEIGHT = 11;
	private static final int HEAD_SIZE = 8;
	private static final int PADDING = 4;
	private static final int BACKGROUND = 0xA0000000;
	private static final int HEADER = 0xC0000000;

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

		int contentWidth = HEAD_SIZE + 2 + nameWidth + 8 + statsWidth;
		int width = contentWidth + PADDING * 2;
		int height = (rows.size() + 1) * ROW_HEIGHT + PADDING * 2;

		int left = (resolution.getScaledWidth() - width) / 2;
		int top = 10;

		drawRect(left, top, left + width, top + height, BACKGROUND);
		drawRect(left, top, left + width, top + ROW_HEIGHT + 2, HEADER);

		String title = "§6Hypixel Scout §8· §7" + rows.size() + " players";
		mc.fontRendererObj.drawStringWithShadow(title,
				left + PADDING, top + PADDING, 0xFFFFFF);

		int y = top + PADDING + ROW_HEIGHT + 2;
		for (Row row : rows) {
			drawHead(mc, row.member.getUuid(), left + PADDING, y);
			mc.fontRendererObj.drawStringWithShadow(row.name,
					left + PADDING + HEAD_SIZE + 2, y, 0xFFFFFF);
			mc.fontRendererObj.drawStringWithShadow(row.stats,
					left + PADDING + contentWidth - mc.fontRendererObj.getStringWidth(row.stats),
					y, 0xFFFFFF);

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

	/**
	 * The face from the player's skin: the 8×8 patch at (8,8) of a 64×64 texture, with the hat
	 * layer at (40,8) drawn over it.
	 */
	private void drawHead(Minecraft mc, UUID uuid, int x, int y) {
		NetworkPlayerInfo info = mc.thePlayer.sendQueue.getPlayerInfo(uuid);
		if (info == null) {
			return;
		}

		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		mc.getTextureManager().bindTexture(info.getLocationSkin());

		Gui.drawScaledCustomSizeModalRect(x, y, 8.0f, 8.0f, 8, 8, HEAD_SIZE, HEAD_SIZE,
				64.0f, 64.0f);
		Gui.drawScaledCustomSizeModalRect(x, y, 40.0f, 8.0f, 8, 8, HEAD_SIZE, HEAD_SIZE,
				64.0f, 64.0f);
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
