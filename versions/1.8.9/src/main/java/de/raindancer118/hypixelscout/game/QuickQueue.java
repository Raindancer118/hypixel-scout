package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.HypixelAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Nine slots that queue for a mode, and one that picks among them at random.
 *
 * <p>One {@code /play} per key press, which is what keeps this a shortcut rather than automation:
 * nothing here fires on its own, repeats, or reacts to the game. Bedwars has no map picking of any
 * kind, so a slot is a mode and not a map.
 */
public final class QuickQueue {
	private final Supplier<ScoutSettings> settings;
	private final Random random = new Random();

	public QuickQueue(Supplier<ScoutSettings> settings) {
		this.settings = settings;
	}

	public void queueSlot(int slot) {
		String[] slots = settings.get().queue.slots;
		if (slot >= 0 && slot < slots.length) {
			queue(slots[slot]);
		}
	}

	public void queueRandom() {
		List<String> assigned = new ArrayList<String>();
		for (String mode : settings.get().queue.slots) {
			if (mode != null && !mode.trim().isEmpty()) {
				assigned.add(mode);
			}
		}

		if (assigned.isEmpty()) {
			say("§6[Scout] §7No quick-queue slots are set.");
			return;
		}

		queue(assigned.get(random.nextInt(assigned.size())));
	}

	public void queue(String mode) {
		if (mode == null || mode.trim().isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getMinecraft();
		if (client.thePlayer == null || client.getNetHandler() == null) {
			return;
		}

		if (settings.get().queue.onlyOnHypixel && !onHypixel()) {
			say("§6[Scout] §7Quick queue only works on Hypixel.");
			return;
		}

		say("§6[Scout] §7Joining §f" + BedwarsModes.shortName(mode) + "§7...");
		client.thePlayer.sendChatMessage("/play " + mode.trim());
	}

	/** Whether the client is connected to Hypixel, for the features that only make sense there. */
	public static boolean onHypixel() {
		ServerData server = Minecraft.getMinecraft().getCurrentServerData();
		return server != null && HypixelAddress.matches(server.serverIP);
	}

	private static void say(String line) {
		if (Minecraft.getMinecraft().thePlayer != null) {
			Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(line));
		}
	}
}
