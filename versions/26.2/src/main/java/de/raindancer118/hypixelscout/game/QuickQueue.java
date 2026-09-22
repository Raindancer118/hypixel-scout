package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.HypixelAddress;
import de.raindancer118.hypixelscout.ui.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
		List<String> assigned = Stream.of(settings.get().queue.slots)
				.filter(mode -> !mode.isBlank())
				.toList();

		if (assigned.isEmpty()) {
			Chat.sayTranslated("message.hypixelscout.queue.empty");
			return;
		}

		queue(assigned.get(random.nextInt(assigned.size())));
	}

	public void queue(String mode) {
		if (mode == null || mode.isBlank()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return;
		}

		if (settings.get().queue.onlyOnHypixel && !onHypixel()) {
			Chat.sayTranslated("message.hypixelscout.queue.not_hypixel");
			return;
		}

		Chat.sayTranslated("message.hypixelscout.queue.joining", BedwarsModes.shortName(mode));
		client.getConnection().sendCommand("play " + mode.trim());
	}

	/** Whether the client is connected to Hypixel, for the features that only make sense there. */
	public static boolean onHypixel() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && HypixelAddress.matches(server.ip);
	}
}
