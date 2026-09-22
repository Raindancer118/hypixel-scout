package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.TeamReport;
import de.raindancer118.hypixelscout.ui.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Puts the enemy teams' combined numbers into party chat, one message per team.
 *
 * <p>Only ever when the player asks. Sending this by itself when a game starts would be a chat
 * macro, which Hypixel bans people for.
 *
 * <p>One message a second rather than all at once: Hypixel's anti-spam swallows a burst, and a
 * swallowed report is worse than a slow one.
 */
public final class PartyReport {
	private static final int TICKS_BETWEEN_MESSAGES = 20;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	private final Deque<String> pending = new ArrayDeque<>();
	private int cooldown;

	public PartyReport(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	/** One report per enemy team, strongest-looking first. Also what the Teams tab draws. */
	public List<TeamReport> enemyTeams() {
		Map<String, List<PlayerStats>> byTeam = new LinkedHashMap<>();
		Teams.Team own = Teams.own();

		for (Roster.Member member : roster.members()) {
			Teams.Team team = Teams.of(member.name());
			if (team == Teams.NONE || team.equals(own)) {
				continue;
			}

			// Somebody still being looked up goes in as null and is counted as unknown, which is
			// honest: the report says what it could not see rather than quietly dropping them.
			byTeam.computeIfAbsent(team.name(), key -> new ArrayList<>())
					.add(stats.peek(member.uuid()));
		}

		List<TeamReport> reports = new ArrayList<>();
		byTeam.forEach((team, members) -> reports.add(TeamReport.of(team, members)));
		reports.sort((left, right) -> Double.compare(right.getCombinedFkdr() * right.getCombinedStars(),
				left.getCombinedFkdr() * left.getCombinedStars()));
		return reports;
	}

	/** Queues the report and says in chat what is about to be sent. */
	public void send() {
		if (!roster.isInGame()) {
			Chat.sayTranslated("message.hypixelscout.party.not_in_game");
			return;
		}

		List<TeamReport> reports = enemyTeams();
		if (reports.isEmpty()) {
			Chat.sayTranslated("message.hypixelscout.party.no_teams");
			return;
		}

		int threshold = settings.get().alerts.streakThreshold;
		reports.forEach(report -> pending.add(report.toChatMessage(threshold)));
		Chat.sayTranslated("message.hypixelscout.party.sending", reports.size());
	}

	public void tick(Minecraft client) {
		if (pending.isEmpty()) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		if (client.player == null || client.getConnection() == null) {
			pending.clear();
			return;
		}

		client.getConnection().sendCommand("pc " + pending.pollFirst());
		cooldown = TICKS_BETWEEN_MESSAGES;
	}

	/** Drops anything still queued, for when the player leaves the game mid-report. */
	public void cancel() {
		if (!pending.isEmpty()) {
			pending.clear();
			Chat.say(Component.translatable("message.hypixelscout.party.cancelled"));
		}
	}
}
