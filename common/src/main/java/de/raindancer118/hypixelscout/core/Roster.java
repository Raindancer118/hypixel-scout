package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Who is in the current game.
 *
 * <p>Filled from the tab list rather than from {@code /who}: the tab list already carries the real
 * UUID of every player the server has told the client about, so there is no Mojang lookup, no
 * command spam, and a name change cannot desynchronise anything. Reading the tab list is the
 * version module's job; this class decides what to keep.
 *
 * <p>When a roster is worth having is not guessed from chat but taken from the Hypixel Mod API's
 * location packet, which names the game type outright. Outside a game nothing is listed and nothing
 * is fetched — a Bedwars lobby holds sixty people nobody is about to fight.
 */
public final class Roster {
	/**
	 * Hypixel fills the tab list with decoration too — headers, counters, team labels. A Minecraft
	 * account name is three to sixteen word characters and nothing else.
	 */
	private static final Pattern PLAYER_NAME = Pattern.compile("^\\w{3,16}$");

	/** A player in the game: the name on their nametag and the UUID the server gave for it. */
	public record Member(String name, UUID uuid) {
	}

	private final StatsService stats;

	/** Insertion-ordered so nothing reshuffles itself between frames. */
	private final Map<String, UUID> members = new LinkedHashMap<>();

	private volatile boolean inGame;
	/** On the game server and the match has begun, as opposed to the waiting lobby before it. */
	private volatile boolean started;
	private volatile String mode;
	private volatile String map;
	private volatile long gameStartedAt;

	public Roster(StatsService stats) {
		this.stats = stats;
	}

	/**
	 * The player moved somewhere else on the network. Leaving a game empties the list at once rather
	 * than leaving the last lobby's numbers up in the next one.
	 */
	public void onLocationChanged(boolean inGame, String mode, String map) {
		boolean wasIn = this.inGame;
		this.inGame = inGame;
		this.mode = inGame ? mode : null;
		this.map = inGame ? map : null;

		if (inGame && !wasIn) {
			gameStartedAt = System.currentTimeMillis();
		}

		if (!inGame) {
			gameStartedAt = 0L;
			started = false;
			clear();
		}
	}

	/**
	 * The match itself has begun. Until now the players were only listed: the waiting lobby fills
	 * and empties for minutes, and looking up somebody who leaves before the start is a request
	 * spent on nothing. Everybody listed at this moment is looked up now.
	 */
	public void markStarted() {
		if (!inGame || started) {
			return;
		}

		started = true;
		gameStartedAt = System.currentTimeMillis();
		members().forEach(member -> stats.request(member.uuid(), member.name()));
	}

	public boolean hasStarted() {
		return started;
	}

	/**
	 * Takes the tab list as it is now. Players who left are dropped, players who stayed keep their
	 * place, and — once the match has started — anybody new is looked up.
	 */
	public void refresh(Collection<Member> tabList) {
		if (!inGame) {
			return;
		}

		Map<String, UUID> seen = new LinkedHashMap<>();
		for (Member entry : tabList) {
			if (entry.name() != null && entry.uuid() != null
					&& PLAYER_NAME.matcher(entry.name()).matches()) {
				seen.put(entry.name(), entry.uuid());
			}
		}

		synchronized (this) {
			Map<String, UUID> merged = new LinkedHashMap<>();
			members.forEach((name, uuid) -> {
				if (seen.containsKey(name)) {
					merged.put(name, uuid);
				}
			});
			merged.putAll(seen);

			members.clear();
			members.putAll(merged);
		}

		if (started) {
			seen.forEach((name, uuid) -> stats.request(uuid, name));
		}
	}

	public synchronized List<Member> members() {
		List<Member> list = new ArrayList<>(members.size());
		members.forEach((name, uuid) -> list.add(new Member(name, uuid)));
		return list;
	}

	public synchronized UUID uuidOf(String name) {
		return members.get(name);
	}

	public synchronized boolean contains(String name) {
		return members.containsKey(name);
	}

	public synchronized void clear() {
		members.clear();
	}

	public boolean isInGame() {
		return inGame;
	}

	/** The Bedwars mode of the running game, such as {@code BEDWARS_FOUR_FOUR}, or {@code null}. */
	public String mode() {
		return mode;
	}

	/** The map of the running game, or {@code null} outside one. */
	public String map() {
		return map;
	}

	/** How long the current game has been running, in milliseconds. */
	public long millisSinceStart() {
		long started = gameStartedAt;
		return started == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - started;
	}
}
