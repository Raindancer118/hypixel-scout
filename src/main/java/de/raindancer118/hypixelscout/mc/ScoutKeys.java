package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.SortMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every function of the mod, on a key.
 *
 * <p>One place for all of them, so the controls screen shows the mod as one block and nothing can
 * be reachable by command but not by key. Each entry is an {@link Action}: a name, a default key
 * and what it does.
 *
 * <p>Almost everything defaults to unbound. Vanilla, Forge and Lunar have already taken the
 * comfortable keys, and a mod that claims a handful of them on install is a mod that breaks
 * somebody's muscle memory without asking. The two exceptions are the lobby list and the queue
 * slots, which are on keys nothing else uses.
 */
public final class ScoutKeys {
	private static final String CATEGORY = "key.category.hypixelscout";

	/** One bindable function. */
	private abstract static class Action {
		private final KeyBinding binding;

		Action(String translationKey, int defaultKey) {
			this.binding = new KeyBinding(translationKey, defaultKey, CATEGORY);
		}

		abstract void run();
	}

	private final HypixelScout scout;
	private final ScoutConfig config;
	private final QuickQueue queue;

	private final List<Action> actions = new ArrayList<Action>();

	public ScoutKeys(HypixelScout scout, ScoutConfig config, QuickQueue queue) {
		this.scout = scout;
		this.config = config;
		this.queue = queue;
	}

	public void register() {
		add("lobby", Keyboard.KEY_L, new Runnable() {
			@Override
			public void run() {
				open(new LobbyScreen(scout.getStats(), scout.getMojang(), scout.getRoster(),
						config));
			}
		});

		add("settings", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				open(new ScoutGuiFactory.ScoutConfigScreen(null));
			}
		});

		// The profile of whoever is under the crosshair, which is the one lookup you cannot type
		// quickly enough in a fight.
		add("profileTarget", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				profileOfTarget();
			}
		});

		add("toggleTable", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				config.setTableEnabled(!config.isTableEnabled());
				say("Table " + onOff(config.isTableEnabled()));
			}
		});

		add("toggleTab", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				config.setTabStatsEnabled(!config.isTabStatsEnabled());
				say("Tab list stats " + onOff(config.isTabStatsEnabled()));
			}
		});

		add("toggleNametags", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				config.setNametagStars(!config.isNametagStars());
				say("Nametag stars " + onOff(config.isNametagStars()));
			}
		});

		add("toggleTooltip", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				config.setLookTooltipEnabled(!config.isLookTooltipEnabled());
				say("Look tooltip " + onOff(config.isLookTooltipEnabled()));
			}
		});

		add("toggleChatHover", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				config.setChatHoverEnabled(!config.isChatHoverEnabled());
				say("Chat hover " + onOff(config.isChatHoverEnabled()));
			}
		});

		add("toggleNickAlert", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				config.setNickAlert(!config.isNickAlert());
				say("Nick alert " + onOff(config.isNickAlert()));
			}
		});

		add("cycleSort", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				SortMode[] modes = SortMode.values();
				SortMode next = modes[(config.getTableSort().ordinal() + 1) % modes.length];
				config.setTableSort(next);
				say("Sorting by " + next.name().toLowerCase(java.util.Locale.ROOT) + ".");
			}
		});

		add("partyReport", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				partyReport();
			}
		});

		add("refresh", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				scout.getStats().invalidate();
				scout.getRoster().scanTabList();
				say("Looking everybody up again.");
			}
		});

		for (int slot = 0; slot < ScoutConfig.QUEUE_SLOTS; slot++) {
			final int index = slot;
			add("queue" + (slot + 1), queueDefaultKey(slot), new Runnable() {
				@Override
				public void run() {
					queue.queueSlot(index);
				}
			});
		}

		add("queueRandom", Keyboard.KEY_NUMPAD0, new Runnable() {
			@Override
			public void run() {
				queue.queueRandom();
			}
		});
	}

	/** The number pad, so slot n sits under the key with the same digit printed on it. */
	private static int queueDefaultKey(int slot) {
		int[] keys = {
				Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3,
				Keyboard.KEY_NUMPAD4, Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6,
				Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9
		};

		return keys[slot];
	}

	private void add(String name, int defaultKey, final Runnable body) {
		Action action = new Action("key.hypixelscout." + name, defaultKey) {
			@Override
			void run() {
				body.run();
			}
		};

		ClientRegistry.registerKeyBinding(action.binding);
		actions.add(action);
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		Minecraft mc = Minecraft.getMinecraft();
		// Not while a screen is open, or these would fire while the player is typing in chat.
		if (mc.thePlayer == null || mc.currentScreen != null) {
			return;
		}

		for (Action action : actions) {
			if (action.binding.isPressed()) {
				action.run();
			}
		}
	}

	private void profileOfTarget() {
		EntityPlayer target = LookTargetTooltip.pickTarget(config.getLookCosine(),
				config.isLookThroughWalls());
		if (target == null) {
			say("§cNot looking at anybody.");
			return;
		}

		UUID uuid = scout.getRoster().uuidOf(target.getName());
		if (uuid == null) {
			uuid = target.getGameProfile().getId();
		}

		open(new ProfileScreen(scout.getStats(), scout.getMojang(), target.getName(), uuid));
	}

	private void partyReport() {
		if (!scout.getRoster().isInBedwars()) {
			say("§cNot in a Bedwars game.");
			return;
		}

		List<String> messages = scout.getPartyReport().send();
		say(messages.isEmpty() ? "§cNo enemy teams to report on yet."
				: "Sending " + messages.size() + " line" + (messages.size() == 1 ? "" : "s")
						+ " to party chat…");
	}

	/** Opened next tick: a screen put up from inside a tick fights with whatever is closing. */
	private void open(final net.minecraft.client.gui.GuiScreen screen) {
		final Minecraft mc = Minecraft.getMinecraft();

		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				mc.displayGuiScreen(screen);
			}
		});
	}

	private static String onOff(boolean value) {
		return value ? "§aon§7." : "§coff§7.";
	}

	private void say(String message) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null) {
			mc.thePlayer.addChatMessage(
					new ChatComponentText("§6[Scout] §r§7" + message));
		}
	}
}
