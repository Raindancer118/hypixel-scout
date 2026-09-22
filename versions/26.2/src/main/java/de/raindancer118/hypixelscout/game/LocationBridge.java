package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.BedwarsLocation;
import de.raindancer118.hypixelscout.core.Roster;
import net.hypixel.data.type.GameType;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.Minecraft;

import java.util.Optional;

/**
 * Where the mod learns that a Bedwars game has started.
 *
 * <p>Hypixel's own Mod API says so outright, in a packet carrying the server type, the mode and the
 * map. That is the whole reason this mod depends on it: the alternative is matching English chat
 * lines, which breaks the day Hypixel rewords one.
 *
 * <p>The subscription is made once per session; the server sends the packet again on every server
 * switch by itself.
 */
public final class LocationBridge {
	private final Roster roster;
	private final Runnable onGameStart;

	private volatile String lastServer;

	public LocationBridge(Roster roster, Runnable onGameStart) {
		this.roster = roster;
		this.onGameStart = onGameStart;
	}

	public void register() {
		HypixelModAPI api = HypixelModAPI.getInstance();
		api.createHandler(ClientboundLocationPacket.class, this::onLocation);
		api.subscribeToEventPacket(ClientboundLocationPacket.class);
	}

	private void onLocation(ClientboundLocationPacket packet) {
		Optional<ServerType> serverType = packet.getServerType();
		boolean bedwars = serverType.isPresent() && serverType.get() == GameType.BEDWARS;
		String mode = packet.getMode().orElse(null);
		String map = packet.getMap().orElse(null);
		String server = packet.getServerName();

		// A lobby reports BEDWARS too, and listing the sixty people standing in it is worse than
		// useless: the map is what tells a match apart from the crowd waiting for one.
		boolean inGame = BedwarsLocation.isInGame(bedwars, mode, map);

		Minecraft.getInstance().execute(() -> {
			boolean newGame = inGame && (!roster.isInGame() || !java.util.Objects.equals(server, lastServer));
			if (newGame && roster.isInGame()) {
				// Straight from one game into the next: the old one has to end first.
				roster.onLocationChanged(false, null, null);
			}

			lastServer = server;
			roster.onLocationChanged(inGame, mode, map);

			if (newGame) {
				onGameStart.run();
			}
		});
	}
}
