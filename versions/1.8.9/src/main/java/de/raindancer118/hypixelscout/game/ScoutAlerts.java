package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsListener;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The warnings that arrive by themselves: somebody in the game is nicked, or somebody is on a run.
 *
 * <p>Each is said once per player per game, into the player's own chat log only. The lookup finishes
 * on a worker thread — {@link #onStats} is called from there, so the actual chat line is posted via
 * {@link Minecraft#addScheduledTask}, the 1.8.9 equivalent of 26.2's {@code Minecraft::execute}.
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
	public void onStats(final UUID uuid, final PlayerStats stats) {
		if (!roster.isInGame() || !roster.contains(stats.getName()) || !announced.add(uuid)) {
			return;
		}

		Minecraft.getMinecraft().addScheduledTask(new Runnable() {
			@Override
			public void run() {
				say(stats);
			}
		});
	}

	private void say(PlayerStats stats) {
		ScoutSettings.Alerts alerts = settings.get().alerts;
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		if (player == null) {
			return;
		}

		if (stats.isNicked()) {
			if (alerts.nickAlert) {
				player.addChatMessage(new ChatComponentText(
						"§6[Scout] §f" + stats.getName() + " §7might be nicked."));
			}
			return;
		}

		Integer winstreak = stats.getWinstreak();
		if (alerts.streakAlert && winstreak != null && winstreak > alerts.streakThreshold) {
			IChatComponent message = new ChatComponentText("§6[Scout] §f" + stats.getName()
					+ " §7is on a §c" + winstreak + " §7winstreak (§f"
					+ StatFormat.ratio(stats.getFkdr()) + " §7FKDR)");
			player.addChatMessage(message);
		}
	}
}
