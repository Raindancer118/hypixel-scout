package de.raindancer118.hypixelscout.mc;

import net.hypixel.data.type.GameType;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.handler.ClientboundPacketHandler;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;

import java.util.Optional;

/**
 * Where the mod learns that a Bedwars game has started.
 *
 * <p>Hypixel's own Mod API says so outright, in a plugin message carrying the server type, the mode
 * and the map. That is the whole reason this mod depends on it: the alternative is matching English
 * chat lines, which breaks the day Hypixel rewords one of them.
 *
 * <p>The subscription is made once per session — the server remembers it and sends the packet again
 * on every server switch by itself.
 */
public final class HypixelLocationBridge {
	private final RosterTracker roster;

	private volatile String mode;
	private volatile String map;

	public HypixelLocationBridge(RosterTracker roster) {
		this.roster = roster;
	}

	public void register() {
		HypixelModAPI api = HypixelModAPI.getInstance();

		api.createHandler(ClientboundLocationPacket.class,
				new ClientboundPacketHandler<ClientboundLocationPacket>() {
					@Override
					public void handle(ClientboundLocationPacket packet) {
						onLocation(packet);
					}
				});
		api.subscribeToEventPacket(ClientboundLocationPacket.class);
	}

	private void onLocation(ClientboundLocationPacket packet) {
		Optional<ServerType> serverType = packet.getServerType();
		boolean bedwars = serverType.isPresent() && serverType.get() == GameType.BEDWARS;

		this.mode = packet.getMode().orElse(null);
		this.map = packet.getMap().orElse(null);

		roster.onLocationChanged(bedwars);
	}

	/** The Bedwars mode, such as {@code BEDWARS_FOUR_FOUR}, or {@code null} outside a game. */
	public String getMode() {
		return mode;
	}

	public String getMap() {
		return map;
	}
}
