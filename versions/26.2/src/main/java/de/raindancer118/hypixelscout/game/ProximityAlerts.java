package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.ProximityWatch;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Feeds {@link ProximityWatch} with the enemies around the player, once per tick.
 *
 * <p>Only players in the game's roster count — Hypixel's NPCs are players too, and never in the tab
 * list — and only players the scoreboard puts on another team than the player's. Whoever comes within the radius is looked up at once, so
 * their numbers are usually there by the time the popup is read.
 */
public final class ProximityAlerts {
	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;
	private final BooleanSupplier teamsReady;
	private final ProximityWatch watch = new ProximityWatch(Clock.SYSTEM);

	/**
	 * @param teamsReady whether the scoreboard shows the game's real teams. In the waiting lobby the
	 *                   name prefixes are rank colours, which would pass for teams.
	 */
	public ProximityAlerts(Roster roster, StatsService stats, Supplier<ScoutSettings> settings,
			BooleanSupplier teamsReady) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
		this.teamsReady = teamsReady;
	}

	public void tick(Minecraft client) {
		ScoutSettings.Proximity proximity = settings.get().proximity;
		if (!proximity.enabled || !roster.hasStarted() || !teamsReady.getAsBoolean() || client.player == null
				|| client.level == null) {
			return;
		}

		List<ProximityWatch.Sighting> sightings = new ArrayList<>();
		for (Player other : client.level.players()) {
			if (other == client.player) {
				continue;
			}

			String name = other.getScoreboardName();
			UUID uuid = roster.uuidOf(name);
			// Only somebody known to be on another team: a teammate, or anybody while the teams are
			// not readable (the waiting lobby), gets no popup.
			if (uuid == null || !Teams.isEnemy(name)) {
				continue;
			}

			double distance = other.distanceTo(client.player);
			if (distance <= proximity.radius) {
				stats.request(uuid, name);
			}
			sightings.add(new ProximityWatch.Sighting(uuid, name, distance));
		}

		watch.observe(sightings, proximity.radius, proximity.seconds * 1000L);
	}

	/** The popups up right now, newest first. */
	public List<ProximityWatch.Popup> showing() {
		return watch.showing();
	}

	/** Forgets everybody, for a new game or when leaving. */
	public void reset() {
		watch.reset();
	}
}
