package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.BedwarsLocation;
import de.raindancer118.hypixelscout.core.Roster;
import net.hypixel.data.type.GameType;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.handler.ClientboundPacketHandler;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Where the mod learns that a Bedwars game has started.
 *
 * <p>Hypixel's own Mod API says so outright, in a packet carrying the server type, the mode and the
 * map. That is the whole reason this mod depends on it: the alternative is matching English chat
 * lines, which breaks the day Hypixel rewords one.
 *
 * <p>The subscription is made once per session; the server sends the packet again on every server
 * switch by itself.
 *
 * <p>Also reads {@link ClientboundPartyInfoPacket}, which {@link AutoRequeue} needs to know which
 * party members are playing in the same game. Unlike 26.2, there is no {@code Minecraft::execute}
 * to hop onto the render thread with here — this Forge 1.8.9 mod set's Mod API packet handlers are
 * already delivered on the client thread (the same guarantee {@code PartyReport.tick()} relies on),
 * so both handlers apply their state directly rather than queueing it for a tick.
 */
public final class LocationBridge {
	private final Roster roster;
	private final Runnable onGameStart;

	private volatile String lastServer;
	private volatile Set<UUID> party = Collections.emptySet();
	private volatile UUID partyLeader;

	public LocationBridge(Roster roster, Runnable onGameStart) {
		this.roster = roster;
		this.onGameStart = onGameStart;
	}

	public void register() {
		HypixelModAPI api = HypixelModAPI.getInstance();

		api.createHandler(ClientboundLocationPacket.class, new ClientboundPacketHandler<ClientboundLocationPacket>() {
			@Override
			public void handle(ClientboundLocationPacket packet) {
				onLocation(packet);
			}
		});
		api.subscribeToEventPacket(ClientboundLocationPacket.class);

		api.createHandler(ClientboundPartyInfoPacket.class, new ClientboundPacketHandler<ClientboundPartyInfoPacket>() {
			@Override
			public void handle(ClientboundPartyInfoPacket packet) {
				onPartyInfo(packet);
			}
		});
	}

	private void onLocation(ClientboundLocationPacket packet) {
		boolean bedwars = packet.getServerType().isPresent() && packet.getServerType().get() == GameType.BEDWARS;
		String mode = packet.getMode().isPresent() ? packet.getMode().get() : null;
		String map = packet.getMap().isPresent() ? packet.getMap().get() : null;
		String server = packet.getServerName();

		// A lobby reports BEDWARS too, and listing the sixty people standing in it is worse than
		// useless: the map is what tells a match apart from the crowd waiting for one.
		boolean inGame = BedwarsLocation.isInGame(bedwars, mode, map);

		boolean newGame = inGame && (!roster.isInGame() || !Objects.equals(server, lastServer));
		if (newGame && roster.isInGame()) {
			// Straight from one game into the next: the old one has to end first.
			roster.onLocationChanged(false, null, null);
		}

		lastServer = server;
		roster.onLocationChanged(inGame, mode, map);

		if (newGame) {
			onGameStart.run();
		}
	}

	private void onPartyInfo(ClientboundPartyInfoPacket packet) {
		party = packet.isInParty() ? packet.getMembers() : Collections.<UUID>emptySet();
		partyLeader = packet.getLeader().isPresent() ? packet.getLeader().get() : null;
	}

	/** Asks the server for the party again; {@link AutoRequeue} calls this once per game join. */
	public void requestPartyInfo() {
		HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
	}

	/** The party as last reported, empty without one. Read by {@link AutoRequeue}. */
	public Set<UUID> party() {
		return party;
	}

	/** The party leader, or {@code null} without a party. */
	public UUID partyLeader() {
		return partyLeader;
	}

	/** The other party members currently playing in this game, by name — for {@link AutoRequeue}. */
	public List<String> partyInGame(Roster roster, UUID self) {
		List<String> names = new ArrayList<String>();
		Set<UUID> members = party;
		for (Roster.Member member : roster.members()) {
			if (members.contains(member.uuid()) && !member.uuid().equals(self)) {
				names.add(member.name());
			}
		}
		return names;
	}
}
