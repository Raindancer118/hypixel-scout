package de.raindancer118.hypixelscout.mc;

import com.mojang.authlib.GameProfile;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

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
 * <p>The roster comes from the tab list rather than from {@code /who}: the tab list already carries
 * the real UUID of every player the server has told the client about, which means no Mojang lookup,
 * no command spam, and a name change cannot desynchronise anything.
 *
 * <p>When a roster is worth having is not guessed from chat but taken from the Hypixel Mod API's
 * location packet, which names the game type outright.
 *
 * <p>Hypixel fills the tab list with decoration as well — headers, counters, team labels. Those are
 * dropped by name: a Minecraft account name is three to sixteen word characters and nothing else.
 */
public final class RosterTracker {
	private static final Pattern PLAYER_NAME = Pattern.compile("^\\w{3,16}$");
	/** Once a second is often enough for a lobby that fills over half a minute. */
	private static final int SCAN_INTERVAL_TICKS = 20;

	private final StatsService stats;

	/** Insertion-ordered so the table does not reshuffle itself between frames. */
	private final Map<String, UUID> roster = new LinkedHashMap<String, UUID>();

	private int ticks;
	/** Nothing is scanned or fetched anywhere but inside a Bedwars game. */
	private volatile boolean inBedwars;

	public RosterTracker(StatsService stats) {
		this.stats = stats;
	}

	/** The players currently in the game, in the order they were first seen. */
	public synchronized List<Member> members() {
		List<Member> members = new ArrayList<Member>(roster.size());
		for (Map.Entry<String, UUID> entry : roster.entrySet()) {
			members.add(new Member(entry.getKey(), entry.getValue()));
		}

		return members;
	}

	public synchronized UUID uuidOf(String name) {
		return roster.get(name);
	}

	public synchronized boolean isEmpty() {
		return roster.isEmpty();
	}

	public boolean isInBedwars() {
		return inBedwars;
	}

	/**
	 * The player moved somewhere else on the network. Leaving a Bedwars game empties the table at
	 * once rather than leaving the last lobby's numbers on screen in the next one.
	 */
	public void onLocationChanged(boolean bedwars) {
		this.inBedwars = bedwars;

		if (!bedwars) {
			clear();
			return;
		}

		scanTabList();
	}

	public void onTick() {
		if (!inBedwars) {
			return;
		}

		if (++ticks < SCAN_INTERVAL_TICKS) {
			return;
		}
		ticks = 0;

		scanTabList();
	}

	public synchronized void clear() {
		roster.clear();
	}

	private synchronized void remove(String name) {
		roster.remove(name);
	}

	/**
	 * Reads the tab list and asks for anybody new. Players who left are dropped, so switching
	 * lobbies replaces the table rather than growing it.
	 */
	public void scanTabList() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null) {
			return;
		}

		NetHandlerPlayClient handler = mc.thePlayer.sendQueue;
		if (handler == null) {
			return;
		}

		Collection<NetworkPlayerInfo> entries = handler.getPlayerInfoMap();
		Map<String, UUID> seen = new LinkedHashMap<String, UUID>();

		for (NetworkPlayerInfo info : entries) {
			GameProfile profile = info.getGameProfile();
			if (profile == null || profile.getName() == null || profile.getId() == null) {
				continue;
			}

			if (!PLAYER_NAME.matcher(profile.getName()).matches()) {
				continue;
			}

			seen.put(profile.getName(), profile.getId());
		}

		synchronized (this) {
			// Keeping the old order for players who stayed: only the newcomers go to the end.
			Map<String, UUID> merged = new LinkedHashMap<String, UUID>();
			for (Map.Entry<String, UUID> entry : roster.entrySet()) {
				if (seen.containsKey(entry.getKey())) {
					merged.put(entry.getKey(), entry.getValue());
				}
			}
			merged.putAll(seen);

			roster.clear();
			roster.putAll(merged);
		}

		for (Map.Entry<String, UUID> entry : seen.entrySet()) {
			stats.request(entry.getValue(), entry.getKey());
		}
	}

	/** A player in the game: the name on their nametag and the UUID the server gave for it. */
	public static final class Member {
		private final String name;
		private final UUID uuid;

		Member(String name, UUID uuid) {
			this.name = name;
			this.uuid = uuid;
		}

		public String getName() {
			return name;
		}

		public UUID getUuid() {
			return uuid;
		}
	}
}
