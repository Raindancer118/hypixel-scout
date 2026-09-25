package de.raindancer118.hypixelscout.game;

import de.raindancer118.cheatwatch.CheatReport;
import de.raindancer118.cheatwatch.Suspicion;
import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.ChatPacing;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.TeamReport;
import de.raindancer118.hypixelscout.core.ThreatCallout;
import de.raindancer118.hypixelscout.core.ThreatScale;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

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
 *
 * <p>{@link #tick()} takes no argument: unlike 26.2's render thread, everything in this Forge 1.8.9
 * port already runs on the client thread inside Forge's own tick events, so there is nothing to hop
 * onto — the caller (a {@code TickEvent.ClientTickEvent} handler) calls this once a tick.
 */
public final class PartyReport {
	/** Where a report goes. */
	public enum Channel {
		/** {@code /pc}: the party, wherever its members are. */
		PARTY,
		/** Ordinary game chat, which in a team mode reaches only the own team. */
		TEAM
	}

	private static final class Line {
		private final Channel channel;
		private final String text;

		Line(Channel channel, String text) {
			this.channel = channel;
			this.text = text;
		}

		Channel channel() {
			return channel;
		}

		String text() {
			return text;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Line)) {
				return false;
			}
			Line other = (Line) obj;
			return channel == other.channel && text.equals(other.text);
		}

		@Override
		public int hashCode() {
			return channel.hashCode() * 31 + text.hashCode();
		}
	}

	/**
	 * The most players the report names. Every line is a chat message, and without a rank Hypixel
	 * allows one every three seconds: six already take eighteen.
	 */
	private static final int MAX_CALLOUTS = 6;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	private final Deque<Line> pending = new ArrayDeque<Line>();
	private int cooldown;

	public PartyReport(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	/** One report per enemy team, strongest-looking first. */
	public List<TeamReport> enemyTeams() {
		List<TeamReport> reports = new ArrayList<TeamReport>();
		for (Map.Entry<String, List<PlayerStats>> entry : enemyPlayers().entrySet()) {
			reports.add(TeamReport.of(entry.getKey(), entry.getValue()));
		}
		reports.sort((left, right) -> Double.compare(danger(right), danger(left)));
		return reports;
	}

	/** Every enemy player by team; {@code null} stands for somebody not looked up yet. */
	private Map<String, List<PlayerStats>> enemyPlayers() {
		Map<String, List<PlayerStats>> byTeam = new LinkedHashMap<String, List<PlayerStats>>();
		Teams.Team own = Teams.own();

		for (Roster.Member member : roster.members()) {
			Teams.Team team = Teams.of(member.name());
			if (team == Teams.NONE || team.equals(own)) {
				continue;
			}

			List<PlayerStats> group = byTeam.get(team.name());
			if (group == null) {
				group = new ArrayList<PlayerStats>();
				byTeam.put(team.name(), group);
			}

			// Somebody still being looked up goes in as null and is counted as unknown, which is
			// honest: the report says what it could not see rather than quietly dropping them.
			group.add(stats.peek(member.uuid()));
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
			say("§6[Scout] §7Not in a game.");
			return;
		}

		if (channel == Channel.TEAM && !BedwarsModes.hasTeammates(roster.mode())) {
			// Team chat in Solo is everybody's chat; the report is not for the enemies to read.
			say("§6[Scout] §7Solo has no team chat to report into.");
			return;
		}

		Map<String, List<PlayerStats>> enemies = enemyPlayers();
		if (enemies.isEmpty()) {
			say("§6[Scout] §7No enemy teams found.");
			return;
		}

		// A second press replaces the first report rather than queueing a duplicate behind it.
		pending.clear();
		List<String> lines = ThreatCallout.lines(enemies, settings.get().alerts.streakThreshold, scale(),
				settings.get().threatReportFrom, MAX_CALLOUTS);
		for (String text : lines) {
			pending.add(new Line(channel, text));
		}
		say("§6[Scout] §7Sending " + lines.size() + " line(s) to "
				+ (channel == Channel.TEAM ? "team" : "party") + " chat.");
	}

	/**
	 * Queues every enemy, one line each, not only the ones worth a warning. Replaces whatever
	 * report was still going out, like {@link #send} does.
	 */
	public void sendAll(Channel channel) {
		if (!roster.isInGame()) {
			say("§6[Scout] §7Not in a game.");
			return;
		}

		if (channel == Channel.TEAM && !BedwarsModes.hasTeammates(roster.mode())) {
			say("§6[Scout] §7Solo has no team chat to report into.");
			return;
		}

		Map<String, List<PlayerStats>> enemies = enemyPlayers();
		if (enemies.isEmpty()) {
			say("§6[Scout] §7No enemy teams found.");
			return;
		}

		pending.clear();
		List<String> lines = ThreatCallout.everyone(enemies, settings.get().alerts.streakThreshold, scale());
		for (String text : lines) {
			pending.add(new Line(channel, text));
		}
		say("§6[Scout] §7Sending " + lines.size() + " line(s) to "
				+ (channel == Channel.TEAM ? "team" : "party") + " chat.");
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
			say(channel == Channel.TEAM && roster.isInGame()
					? "§6[Scout] §7Solo has no team chat to report into."
					: "§6[Scout] §7Nowhere to send that right now.");
			return;
		}

		if (playerStats == null) {
			say("§6[Scout] §7No stats for " + name + " yet.");
			return;
		}

		for (String text : StatLines.chatLines(name, playerStats, scale())) {
			pending.add(new Line(channel, text));
		}
		say("§6[Scout] §7Sending " + name + "'s stats to "
				+ (channel == Channel.TEAM ? "team" : "party") + " chat.");
	}

	/**
	 * Everybody flagged for cheating this round, one line each with their team, how sure the mod is
	 * and what they were seen doing. Only when the player asks, like every other report; queued
	 * behind anything already going out. Party chat works anywhere on Hypixel, team chat only in a
	 * game with teams.
	 *
	 * <p>{@code suspicion} is supplied by whichever later phase runs the cheat checks (a
	 * {@code CheatSensor} does not exist in this module yet) — this class only knows how to turn one
	 * into chat lines.
	 */
	public void sendCheats(Channel channel, Suspicion suspicion) {
		if (!canSendPlayer(channel)) {
			say(channel == Channel.TEAM && roster.isInGame()
					? "§6[Scout] §7Solo has no team chat to report into."
					: "§6[Scout] §7Nowhere to send that right now.");
			return;
		}

		List<String> lines = CheatReport.lines(suspicion, name -> Teams.of(name).name());
		if (lines.isEmpty()) {
			say("§6[Scout] §7Nobody is flagged this round.");
			return;
		}

		for (String text : lines) {
			Line line = new Line(channel, text);
			if (!pending.contains(line)) {
				pending.add(line);
			}
		}
		say("§6[Scout] §7Sending " + lines.size() + " line(s) to "
				+ (channel == Channel.TEAM ? "team" : "party") + " chat.");
	}

	/**
	 * Sends one line typed by the player, from a callout hotkey. The same channel rules as a single
	 * player's stats; a line already waiting is not queued twice, so hammering the key does not flood
	 * the chat.
	 */
	public void sendCallout(Channel channel, String text) {
		if (!canSendPlayer(channel)) {
			say(channel == Channel.TEAM && roster.isInGame()
					? "§6[Scout] §7Solo has no team chat to report into."
					: "§6[Scout] §7Nowhere to send that right now.");
			return;
		}

		Line line = new Line(channel, text);
		if (!pending.contains(line)) {
			pending.add(line);
		}
	}

	/** The lines the report is about to send, for a future game test. */
	public List<String> pendingLines() {
		List<String> texts = new ArrayList<String>(pending.size());
		for (Line line : pending) {
			texts.add(line.text());
		}
		return texts;
	}

	public void tick() {
		if (pending.isEmpty()) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		if (client.thePlayer == null || client.getNetHandler() == null) {
			pending.clear();
			return;
		}

		Line line = pending.pollFirst();
		if (line.channel() == Channel.PARTY) {
			client.thePlayer.sendChatMessage("/pc " + line.text());
		} else {
			client.thePlayer.sendChatMessage(line.text());
		}

		cooldown = ChatPacing.ticksBetween(ownRanked(client), settings.get().reportIntervalTicks);
	}

	/** Players with any rank may chat quickly; without one, Hypixel enforces a pause. */
	private boolean ownRanked(Minecraft client) {
		PlayerStats own = stats.peek(client.thePlayer.getUniqueID());
		return own != null && own.getRank() != null;
	}

	/** Drops anything still queued, for when the player leaves the game mid-report. */
	public void cancel() {
		if (!pending.isEmpty()) {
			pending.clear();
			say("§6[Scout] §7Report cancelled.");
		}
	}

	/**
	 * A fresh {@link ThreatScale} for right now, built the same way 26.2's {@code ui.Threats} does
	 * (which does not exist in this module yet): the player's own stats and their team's, at the
	 * settings' basis, sensitivity and focus. Computed on demand rather than cached once a second,
	 * since there is no HUD tick driving a cache here yet either.
	 */
	private ThreatScale scale() {
		ScoutSettings config = settings.get();
		EntityPlayer self = Minecraft.getMinecraft().thePlayer;
		PlayerStats ownStats = self == null ? null : stats.peek(self.getUniqueID());

		List<PlayerStats> teammates = new ArrayList<PlayerStats>();
		Teams.Team own = Teams.own();
		if (own != Teams.NONE) {
			for (Roster.Member member : roster.members()) {
				if (self != null && member.name().equals(self.getName())) {
					continue;
				}
				if (Teams.of(member.name()).equals(own)) {
					teammates.add(stats.peek(member.uuid()));
				}
			}
		}

		ThreatScale base = ThreatScale.of(config.threatBasis, ownStats, teammates);
		return base.withSensitivity(config.threatSensitivity / 100.0).withFocus(config.threatFocus);
	}

	private static void say(String line) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player != null) {
			player.addChatMessage(new ChatComponentText(line));
		}
	}
}
