package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsListener;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The warnings that arrive by themselves: somebody in the game is nicked, or somebody is on a run.
 *
 * <p>Both are said once per player per game. The lookup finishes on a background thread, so the
 * message is handed to the game thread rather than being printed from there — Minecraft's chat is
 * not safe to touch from anywhere else.
 */
public final class ScoutAlerts implements StatsListener {
	private final ScoutConfig config;
	private final RosterTracker roster;

	private final Set<UUID> announced =
			Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

	public ScoutAlerts(ScoutConfig config, RosterTracker roster) {
		this.config = config;
		this.roster = roster;
	}

	/** A new game is a clean slate: the same nick in the next lobby is worth saying again. */
	public void reset() {
		announced.clear();
	}

	@Override
	public void onStats(UUID uuid, PlayerStats stats) {
		if (!roster.isInBedwars() || !announced.add(uuid)) {
			return;
		}

		if (stats.isNicked() && config.isNickAlert()) {
			say("§d⚠ §f" + stats.getName()
					+ "§7 is nicked §8- Hypixel has no profile under that name.");
			return;
		}

		Integer winstreak = stats.getWinstreak();
		if (config.isStreakAlert() && winstreak != null
				&& winstreak.intValue() > config.getStreakThreshold()) {
			say("§c⚠ §f" + stats.getName() + "§7 is on a §f"
					+ winstreak + "§7 game winstreak §8("
					+ StatFormat.ratio(stats.getFkdr()) + " fkdr)");
		}
	}

	/**
	 * Client side only: this writes into the player's own chat log and sends nothing to the
	 * server, which is what keeps it clear of Hypixel's rules about automated messages.
	 */
	private void say(final String message) {
		final Minecraft mc = Minecraft.getMinecraft();
		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				if (mc.thePlayer == null) {
					return;
				}

				IChatComponent line = new ChatComponentText("§6[Scout] §r" + message);
				mc.thePlayer.addChatMessage(line);
			}
		});
	}

	/** Kept so a set of stale announcements never grows without bound in a long session. */
	public int announcedCount() {
		return new HashSet<UUID>(announced).size();
	}
}
