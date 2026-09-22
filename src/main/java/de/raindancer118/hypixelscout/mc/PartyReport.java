package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.TeamReport;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts the enemy teams' combined numbers into party chat, one message per team.
 *
 * <p>Only ever on {@code /scout party}. Sending this by itself when a game starts would be a chat
 * macro, which Hypixel bans people for, and the mod has no business getting anyone banned.
 *
 * <p>The messages go out one per second rather than all at once: Hypixel's anti-spam swallows a
 * burst, and a swallowed report is worse than a slow one.
 */
public final class PartyReport {
	private static final int TICKS_BETWEEN_MESSAGES = 20;

	private final RosterTracker roster;
	private final StatsService stats;
	private final ScoutConfig config;

	private final Deque<String> pending = new ArrayDeque<String>();
	private int cooldown;

	public PartyReport(RosterTracker roster, StatsService stats, ScoutConfig config) {
		this.roster = roster;
		this.stats = stats;
		this.config = config;
	}

	/** Builds the report and queues it. Returns what it will send, for the confirmation in chat. */
	public List<String> send() {
		List<String> messages = build();
		pending.addAll(messages);

		return messages;
	}

	private List<String> build() {
		Map<String, List<PlayerStats>> byTeam = new LinkedHashMap<String, List<PlayerStats>>();
		String ownTeam = Teams.ownTeam();

		for (RosterTracker.Member member : roster.members()) {
			String team = Teams.teamOf(member.getName());
			if (team == null || team.equals(ownTeam)) {
				continue;
			}

			List<PlayerStats> group = byTeam.get(team);
			if (group == null) {
				group = new ArrayList<PlayerStats>();
				byTeam.put(team, group);
			}

			// A player still being looked up goes in as null and is counted as unknown, which is
			// honest: the report says what it could not see rather than quietly dropping them.
			group.add(stats.peek(member.getUuid()));
		}

		List<String> messages = new ArrayList<String>(byTeam.size());
		for (Map.Entry<String, List<PlayerStats>> team : byTeam.entrySet()) {
			messages.add(TeamReport.of(team.getKey(), team.getValue())
					.toChatMessage(config.getStreakThreshold()));
		}

		return messages;
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || pending.isEmpty()) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null) {
			pending.clear();
			return;
		}

		mc.thePlayer.sendChatMessage("/pc " + pending.pollFirst());
		cooldown = TICKS_BETWEEN_MESSAGES;
	}

	/** Drops anything still queued, for when the player leaves the game mid-report. */
	public void cancel() {
		if (!pending.isEmpty()) {
			pending.clear();

			Minecraft mc = Minecraft.getMinecraft();
			if (mc.thePlayer != null) {
				mc.thePlayer.addChatMessage(new ChatComponentText(
						"§6[Scout] §7Party report cancelled."));
			}
		}
	}
}
