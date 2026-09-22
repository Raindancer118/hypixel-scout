package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Nine hotkeys that queue for a mode, and a tenth that picks one of them at random.
 *
 * <p>Each press sends one {@code /play} for one key press, which is what keeps this a shortcut
 * rather than automation: nothing here fires on its own, repeats, or reacts to the game.
 *
 * <p>The same keyboard group also holds the key that opens the lobby list, so everything this mod
 * binds sits together in the controls screen.
 *
 * <p>Bedwars has no map picking of any kind — there is no command for it and no lobby setting — so
 * a slot is a mode and not a map.
 */
public final class QuickQueue {
	private static final String CATEGORY = "key.category.hypixelscout";

	private final ScoutConfig config;
	private final HypixelScout scout;
	private final Random random = new Random();

	private final KeyBinding[] slots = new KeyBinding[ScoutConfig.QUEUE_SLOTS];
	private KeyBinding randomSlot;
	private KeyBinding lobbyScreen;

	public QuickQueue(HypixelScout scout, ScoutConfig config) {
		this.scout = scout;
		this.config = config;
	}

	public void register() {
		// The number row, so slot n sits under the key with the same digit on it.
		int[] keys = {
				Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3,
				Keyboard.KEY_NUMPAD4, Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6,
				Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9
		};

		for (int slot = 0; slot < slots.length; slot++) {
			slots[slot] = new KeyBinding("key.hypixelscout.queue" + (slot + 1), keys[slot],
					CATEGORY);
			ClientRegistry.registerKeyBinding(slots[slot]);
		}

		randomSlot = new KeyBinding("key.hypixelscout.queueRandom", Keyboard.KEY_NUMPAD0, CATEGORY);
		ClientRegistry.registerKeyBinding(randomSlot);

		lobbyScreen = new KeyBinding("key.hypixelscout.lobby", Keyboard.KEY_L, CATEGORY);
		ClientRegistry.registerKeyBinding(lobbyScreen);
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		// Not while a screen is open: the keys would fire while the player is typing in chat.
		if (mc.thePlayer == null || mc.currentScreen != null) {
			return;
		}

		for (int slot = 0; slot < slots.length; slot++) {
			if (slots[slot] != null && slots[slot].isPressed()) {
				queue(config.getQueueMode(slot));
			}
		}

		if (randomSlot != null && randomSlot.isPressed()) {
			queue(randomMode());
		}

		if (lobbyScreen != null && lobbyScreen.isPressed()) {
			mc.displayGuiScreen(new LobbyScreen(scout.getStats(), scout.getMojang(),
					scout.getRoster(), config));
		}
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
