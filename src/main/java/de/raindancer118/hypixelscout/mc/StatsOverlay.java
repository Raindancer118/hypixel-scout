package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatsService;
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
 * The table of everybody in the game.
 *
 * <p>Sorted by whatever the settings say, highest first — by stars out of the box, because that is
 * what Bedwars players judge a lobby by at a glance.
 */
public final class StatsOverlay extends Gui {
	private static final int LINE_HEIGHT = 10;
	private static final int BACKGROUND = 0x80000000;

	private final RosterTracker roster;
	private final StatsService stats;
	private final ScoutConfig config;

	public StatsOverlay(RosterTracker roster, StatsService stats, ScoutConfig config) {
		this.roster = roster;
		this.stats = stats;
		this.config = config;
	}

	@SubscribeEvent
	public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
		// One element type only, or the table would be drawn once for every part of the HUD.
		if (event.type != RenderGameOverlayEvent.ElementType.TEXT) {
			return;
		}

		if (!config.isTableEnabled() || roster.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		// The debug screen and the tab list both live in this corner; three layers of text on top
		// of each other help nobody.
		if (mc.gameSettings.showDebugInfo || mc.gameSettings.keyBindPlayerList.isKeyDown()) {
			return;
		}

		List<String> lines = rows();
		if (lines.isEmpty()) {
			return;
		}

		int width = 0;
		for (String line : lines) {
			width = Math.max(width, mc.fontRendererObj.getStringWidth(line));
		}

		ScaledResolution resolution = new ScaledResolution(mc);
		int x = Math.min(config.getTableX(), Math.max(0, resolution.getScaledWidth() - width));
		int y = Math.min(config.getTableY(),
				Math.max(0, resolution.getScaledHeight() - lines.size() * LINE_HEIGHT));

		if (config.isTableBackground()) {
			drawRect(x - 2, y - 2, x + width + 2, y + lines.size() * LINE_HEIGHT, BACKGROUND);
		}

		for (String line : lines) {
			mc.fontRendererObj.drawStringWithShadow(line, x, y, 0xFFFFFF);
			y += LINE_HEIGHT;
		}
	}

	private List<String> rows() {
		List<RosterTracker.Member> members = new ArrayList<RosterTracker.Member>();
		for (RosterTracker.Member member : roster.members()) {
			if (config.isHideOwnTeam() && Teams.isOwnTeam(member.getName())) {
				continue;
			}
			members.add(member);
		}

		Collections.sort(members, comparator());

		List<String> lines = new ArrayList<String>(members.size() + 1);
		lines.add("§6Hypixel Scout §8· §7" + members.size() + " players");

		for (RosterTracker.Member member : members) {
			lines.add(StatsLines.row(member.getName(), stats.peek(member.getUuid()),
					stats.isPending(member.getUuid()), stats.failureFor(member.getUuid()), config));
		}

		return lines;
	}

	private Comparator<RosterTracker.Member> comparator() {
		final ScoutConfig.Sort sort = config.getTableSort();
		final StatsService service = stats;

		return new Comparator<RosterTracker.Member>() {
			@Override
			public int compare(RosterTracker.Member left, RosterTracker.Member right) {
				if (sort == ScoutConfig.Sort.NAME) {
					return left.getName().compareToIgnoreCase(right.getName());
				}

				return Double.compare(value(service, right, sort), value(service, left, sort));
			}
		};
	}

	/** An unknown player sorts to the bottom rather than jumping around as the answer arrives. */
	private static double value(StatsService service, RosterTracker.Member member,
			ScoutConfig.Sort sort) {
		PlayerStats stats = service.peek(member.getUuid());
		if (stats == null || stats.isNicked()) {
			return -1.0;
		}

		switch (sort) {
			case FKDR:
				return stats.getFkdr();
			case WLR:
				return stats.getWlr();
			default:
				return stats.getStars();
		}
	}
}
