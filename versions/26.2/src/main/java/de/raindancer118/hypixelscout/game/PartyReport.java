package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatLines;
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
 * Puts the enemy teams' threats into team chat or party chat, one line per team.
 *
 * <p>Only ever when the player asks. Sending this by itself when a game starts would be a chat
 * macro, which Hypixel bans people for.
 *
 * <p>The lines go out one at a time: Hypixel's anti-spam swallows a burst, and players without a
 * rank may only chat every few seconds, so their report is paced slower.
 */
public final class PartyReport {
	/** Where a report goes. */
	public enum Channel {
		/** {@code /pc}: the party, wherever its members are. */
		PARTY,
		/** Ordinary game chat, which in a team mode reaches only the own team. */
		TEAM
	}

	private record Line(Channel channel, String text) {
	}

	private static final int TICKS_RANKED = 22;
	/** Hypixel lets players without a rank chat once every three seconds. */
	private static final int TICKS_UNRANKED = 64;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	private final Deque<Line> pending = new ArrayDeque<>();
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
		reports.sort((left, right) -> Double.compare(danger(right), danger(left)));
		return reports;
	}

	private static double danger(TeamReport report) {
		return report.getCombinedFkdr() * report.getCombinedStars();
	}

	/** Whether a report into this channel makes sense right now — the buttons grey out if not. */
	public boolean canSend(Channel channel) {
		return roster.isInGame() && (channel != Channel.TEAM || BedwarsModes.hasTeammates(roster.mode()));
	}

	/** Queues the report and says in the player's own chat what is about to be sent. */
	public void send(Channel channel) {
		if (!roster.isInGame()) {
			Chat.sayTranslated("message.hypixelscout.party.not_in_game");
			return;
		}

		if (channel == Channel.TEAM && !BedwarsModes.hasTeammates(roster.mode())) {
			// Team chat in Solo is everybody's chat; the report is not for the enemies to read.
			Chat.sayTranslated("message.hypixelscout.report.solo");
			return;
		}

		List<TeamReport> reports = enemyTeams();
		if (reports.isEmpty()) {
			Chat.sayTranslated("message.hypixelscout.party.no_teams");
			return;
		}

		// A second press replaces the first report rather than queueing a duplicate behind it.
		pending.clear();
		int threshold = settings.get().alerts.streakThreshold;
		reports.forEach(report -> pending.add(new Line(channel, report.toThreatMessage(threshold))));
		Chat.sayTranslated(channel == Channel.TEAM ? "message.hypixelscout.report.sending_team"
				: "message.hypixelscout.party.sending", reports.size());
	}

	/**
	 * Whether one player's stats can go into this channel right now: party chat anywhere on
	 * Hypixel, team chat only inside a game that has teams.
	 */
	public boolean canSendPlayer(Channel channel) {
		return channel == Channel.PARTY ? roster.isInGame() || QuickQueue.onHypixel() : canSend(channel);
	}

	/** Sends one player's stats as a single line. Queued behind anything already going out. */
	public void sendPlayer(Channel channel, String name, PlayerStats playerStats) {
		if (!canSendPlayer(channel)) {
			Chat.sayTranslated(channel == Channel.TEAM && roster.isInGame()
					? "message.hypixelscout.report.solo" : "message.hypixelscout.report.nowhere");
			return;
		}

		if (playerStats == null) {
			Chat.sayTranslated("message.hypixelscout.report.no_stats", name);
			return;
		}

		pending.add(new Line(channel, StatLines.chatLine(name, playerStats)));
		Chat.sayTranslated(channel == Channel.TEAM ? "message.hypixelscout.report.player_team"
				: "message.hypixelscout.report.player_party", name);
	}

	/** The lines the report is about to send, for the client game test. */
	public List<String> pendingLines() {
		return pending.stream().map(Line::text).toList();
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

		Line line = pending.pollFirst();
		if (line.channel() == Channel.PARTY) {
			client.getConnection().sendCommand("pc " + line.text());
		} else {
			client.getConnection().sendChat(line.text());
		}

		cooldown = ownRanked(client) ? TICKS_RANKED : TICKS_UNRANKED;
	}

	/** Players with any rank may chat quickly; without one, Hypixel enforces a pause. */
	private boolean ownRanked(Minecraft client) {
		PlayerStats own = stats.peek(client.player.getUUID());
		return own != null && own.getRank() != null;
	}

	/** Drops anything still queued, for when the player leaves the game mid-report. */
	public void cancel() {
		if (!pending.isEmpty()) {
			pending.clear();
			Chat.say(Component.translatable("message.hypixelscout.party.cancelled"));
		}
	}
}
