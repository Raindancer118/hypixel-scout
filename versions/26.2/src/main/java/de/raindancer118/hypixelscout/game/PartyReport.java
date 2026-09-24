package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.ChatPacing;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.TeamReport;
import de.raindancer118.hypixelscout.core.ThreatCallout;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.Threats;
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
 * Puts the enemies worth a warning into team chat or party chat, one short line each.
 *
 * <p>Only ever when the player asks. Sending this by itself when a game starts would be a chat
 * macro, which Hypixel bans people for.
 *
 * <p>The lines go out one at a time, as far apart as the player set ({@link ChatPacing}): Hypixel's
 * anti-spam swallows a burst, and players without a rank may only chat every few seconds, so their
 * report is paced slower whatever the setting says.
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

	/**
	 * The most players the report names. Every line is a chat message, and without a rank Hypixel
	 * allows one every three seconds: six already take eighteen.
	 */
	private static final int MAX_CALLOUTS = 6;

	/** One report per enemy team, strongest-looking first. */
	public List<TeamReport> enemyTeams() {
		List<TeamReport> reports = new ArrayList<>();
		enemyPlayers().forEach((team, members) -> reports.add(TeamReport.of(team, members)));
		reports.sort((left, right) -> Double.compare(danger(right), danger(left)));
		return reports;
	}

	/** Every enemy player by team; {@code null} stands for somebody not looked up yet. */
	private Map<String, List<PlayerStats>> enemyPlayers() {
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

		return byTeam;
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

		Map<String, List<PlayerStats>> enemies = enemyPlayers();
		if (enemies.isEmpty()) {
			Chat.sayTranslated("message.hypixelscout.party.no_teams");
			return;
		}

		// A second press replaces the first report rather than queueing a duplicate behind it.
		pending.clear();
		List<String> lines = ThreatCallout.lines(enemies, settings.get().alerts.streakThreshold, Threats.scale(),
				settings.get().threatReportFrom, MAX_CALLOUTS);
		lines.forEach(text -> pending.add(new Line(channel, text)));
		Chat.sayTranslated(channel == Channel.TEAM ? "message.hypixelscout.report.sending_team"
				: "message.hypixelscout.party.sending", lines.size());
	}

	/**
	 * Queues every enemy, one line each, not only the ones worth a warning. Replaces whatever
	 * report was still going out, like {@link #send} does.
	 */
	public void sendAll(Channel channel) {
		if (!roster.isInGame()) {
			Chat.sayTranslated("message.hypixelscout.party.not_in_game");
			return;
		}

		if (channel == Channel.TEAM && !BedwarsModes.hasTeammates(roster.mode())) {
			Chat.sayTranslated("message.hypixelscout.report.solo");
			return;
		}

		Map<String, List<PlayerStats>> enemies = enemyPlayers();
		if (enemies.isEmpty()) {
			Chat.sayTranslated("message.hypixelscout.party.no_teams");
			return;
		}

		pending.clear();
		List<String> lines = ThreatCallout.everyone(enemies, settings.get().alerts.streakThreshold, Threats.scale());
		lines.forEach(text -> pending.add(new Line(channel, text)));
		Chat.sayTranslated(channel == Channel.TEAM ? "message.hypixelscout.report.sending_team"
				: "message.hypixelscout.party.sending", lines.size());
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

		StatLines.chatLines(name, playerStats, Threats.scale()).forEach(text -> pending.add(new Line(channel, text)));
		Chat.sayTranslated(channel == Channel.TEAM ? "message.hypixelscout.report.player_team"
				: "message.hypixelscout.report.player_party", name);
	}

	/**
	 * Sends one line typed by the player, from a callout hotkey. The same channel rules as a single
	 * player's stats; a line already waiting is not queued twice, so hammering the key does not flood
	 * the chat.
	 */
	public void sendCallout(Channel channel, String text) {
		if (!canSendPlayer(channel)) {
			Chat.sayTranslated(channel == Channel.TEAM && roster.isInGame()
					? "message.hypixelscout.report.solo" : "message.hypixelscout.report.nowhere");
			return;
		}

		Line line = new Line(channel, text);
		if (!pending.contains(line)) {
			pending.add(line);
		}
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

		cooldown = ChatPacing.ticksBetween(ownRanked(client), settings.get().reportIntervalTicks);
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
