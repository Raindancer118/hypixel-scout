package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The star in front of a nametag, and the FKDR after it.
 *
 * <p>Added to the nametag the game already built rather than replacing it, so the team colour and
 * prefix the server gave it stay exactly as they were — this only puts numbers either side. Called
 * from the renderer mixin while the frame's render state is being filled in.
 */
public final class Nametags {
	private static volatile Nametags instance;

	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	public Nametags(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
		instance = this;
	}

	/** What the mixin calls. Returns the tag unchanged whenever there is nothing to add. */
	public static Component decorate(Avatar entity, Component tag) {
		Nametags current = instance;
		return current == null || tag == null ? tag : current.decorateTag(entity, tag);
	}

	/** The stars in front of the name; Scout adds its own cheat mark to the same tag. */
	private Component decorateTag(Avatar entity, Component tag) {
		return withStats(entity, tag);
	}

	private Component withStats(Avatar entity, Component tag) {
		ScoutSettings.Nametag nametag = settings.get().nametag;
		if (!nametag.stars || !roster.isInGame() || !(entity instanceof Player player)
				|| player == Minecraft.getInstance().player) {
			return tag;
		}

		String name = player.getScoreboardName();
		UUID uuid = roster.uuidOf(name);
		if (uuid == null) {
			uuid = player.getUUID();
		}

		PlayerStats playerStats = stats.peek(uuid);
		if (playerStats == null) {
			// Nothing known yet: leave the tag alone rather than flicker a placeholder for the first
			// second of every game.
			if (roster.hasStarted()) {
				stats.request(uuid, name);
			}
			return tag;
		}

		if (playerStats.isNicked()) {
			return Component.empty().append(Component.literal("§d[NICK] ")).append(tag);
		}

		var decorated = Component.empty()
				.append(Component.literal(StatFormat.star(playerStats.getStars()) + " "))
				.append(tag);

		if (nametag.fkdr) {
			decorated.append(Component.literal(" " + StatFormat.ratioColour(playerStats.getFkdr())
					+ StatFormat.ratio(playerStats.getFkdr())));
		}

		return decorated;
	}
}
