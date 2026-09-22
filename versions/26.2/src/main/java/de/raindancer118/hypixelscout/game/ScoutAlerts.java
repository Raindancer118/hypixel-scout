package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsListener;
import de.raindancer118.hypixelscout.ui.Chat;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The warnings that arrive by themselves: somebody in the game is nicked, or somebody is on a run.
 *
 * <p>Each is said once per player per game, into the player's own chat log only. The lookup
 * finishes on a worker thread; {@link Chat} carries the line over to the render thread.
 */
public final class ScoutAlerts implements StatsListener {
	private final Supplier<ScoutSettings> settings;
	private final Roster roster;

	private final Set<UUID> announced = ConcurrentHashMap.newKeySet();

	public ScoutAlerts(Supplier<ScoutSettings> settings, Roster roster) {
		this.settings = settings;
		this.roster = roster;
	}

	/** A new game is a clean slate: the same nick in the next lobby is worth saying again. */
	public void reset() {
		announced.clear();
	}

	@Override
	public void onStats(UUID uuid, PlayerStats stats) {
		if (!roster.isInGame() || !roster.contains(stats.getName()) || !announced.add(uuid)) {
			return;
		}

		ScoutSettings.Alerts alerts = settings.get().alerts;

		if (stats.isNicked()) {
			if (alerts.nickAlert) {
				Chat.say(Component.translatable("message.hypixelscout.alert.nick",
						Component.literal(stats.getName()).withColor(0xFFFFFF)));
			}
			return;
		}

		Integer winstreak = stats.getWinstreak();
		if (alerts.streakAlert && winstreak != null && winstreak > alerts.streakThreshold) {
			Chat.say(Component.translatable("message.hypixelscout.alert.streak",
					Component.literal(stats.getName()).withColor(0xFFFFFF),
					Component.literal(String.valueOf(winstreak)).withColor(0xFF5555),
					StatFormat.ratio(stats.getFkdr())));
		}
	}
}
