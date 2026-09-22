package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Nine slots that queue for a mode, and a way to pick one of them at random.
 *
 * <p>Each call sends one {@code /play} for one key press, which is what keeps this a shortcut
 * rather than automation: nothing here fires on its own, repeats, or reacts to the game. The keys
 * themselves are in {@link ScoutKeys} with the rest of the mod's bindings.
 *
 * <p>Bedwars has no map picking of any kind — there is no command for it and no lobby setting — so
 * a slot is a mode and not a map.
 */
public final class QuickQueue {
	private final ScoutConfig config;
	private final Random random = new Random();

	public QuickQueue(ScoutConfig config) {
		this.config = config;
	}

	/** Queues for one slot's mode. Called from the key that was bound to that slot. */
	public void queueSlot(int slot) {
		queue(config.getQueueMode(slot));
	}

	/** Queues for one of the assigned slots, chosen at random. */
	public void queueRandom() {
		queue(randomMode());
	}

	private String randomMode() {
		List<String> assigned = new ArrayList<String>();
		for (int slot = 0; slot < ScoutConfig.QUEUE_SLOTS; slot++) {
			String mode = config.getQueueMode(slot);
			if (mode != null && !mode.trim().isEmpty()) {
				assigned.add(mode.trim());
			}
		}

		return assigned.isEmpty() ? "" : assigned.get(random.nextInt(assigned.size()));
	}

	private void queue(String mode) {
		if (mode == null || mode.trim().isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (config.isQueueOnlyOnHypixel() && !HypixelScout.onHypixel()) {
			mc.thePlayer.addChatMessage(new ChatComponentText(
					"§6[Scout] §7Quick queue only works on Hypixel."));
			return;
		}

		mc.thePlayer.sendChatMessage("/play " + mode.trim());
	}
}
