package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.game.PartyReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Every function of the mod, on a key.
 *
 * <p>One place for all of them, so the controls screen shows the mod as one block and nothing is
 * reachable by command but not by key — the same reasoning as 26.2's {@code ScoutKeys}, ported to
 * legacy Forge: {@link KeyBinding} instead of {@code KeyMapping}, {@link ClientRegistry} instead of
 * {@code KeyMappingHelper}, LWJGL2 {@link Keyboard} codes instead of GLFW ones. Almost everything
 * defaults to unbound: vanilla and the launchers have taken the comfortable keys already, and a mod
 * that quietly claims a dozen more breaks somebody's muscle memory. The exceptions — the mod's
 * screen, the table and the queue slots on the number pad — sit on keys vanilla leaves free.
 *
 * <p>This phase has no screens or HUD elements yet (see {@code Project.md}), so every key whose
 * 26.2 action opens a screen, or reads/drives a HUD element's held state, is registered here (so the
 * controls screen already lists it and later phases do not have to touch this class to bind it) but
 * left unwired — see the {@code // not wired yet} comments below. Everything else — settings
 * toggles, sort cycling, party/team reports, callouts, queue slots, the key check — is fully live.
 */
public final class ScoutKeys {
	/** Standard Forge convention: {@code key.categories.<name>} is what {@code GuiControls} looks up. */
	private static final String CATEGORY = "key.categories.hypixelscout";

	/** One bindable, one-shot function: a name, a default key and what pressing it does. */
	private static final class Action {
		private final KeyBinding binding;
		private final Runnable run;

		Action(KeyBinding binding, Runnable run) {
			this.binding = binding;
			this.run = run;
		}

		KeyBinding binding() {
			return binding;
		}

		void run() {
			run.run();
		}
	}

	private final HypixelScout mod;
	private final List<Action> actions = new ArrayList<Action>();
	private final KeyBinding[] queueKeys = new KeyBinding[ScoutSettings.QUEUE_SLOTS];
	private final KeyBinding[] calloutKeys = new KeyBinding[ScoutSettings.CALLOUTS];
	/** Held, not pressed: the table stays up exactly as long as this is down (later phase). */
	private KeyBinding tableKey;
	/** Held, not pressed: the peek stats are up exactly as long as this is down (later phase). */
	private KeyBinding peekKey;

	public ScoutKeys(HypixelScout mod) {
		this.mod = mod;
	}

	public void register() {
		// opens ScoutScreen once ui/screen is ported (later phase)
		add("open", Keyboard.KEY_K, NOOP);
		// Held mode, read in tick(): the HUD table isn't ported yet, so this only tracks the key
		// itself for now (see isTableHeld()) — no action fires on press either.
		tableKey = add("table", Keyboard.KEY_Y, NOOP);
		// Held, not pressed: see isPeekHeld() — the peek HUD element isn't ported yet.
		peekKey = add("peek", Keyboard.KEY_NONE, NOOP);
		// opens ScoutScreen (settings) once ui/screen is ported (later phase)
		add("settings", Keyboard.KEY_NONE, NOOP);
		// opens ScoutScreen on the CHEATS page once ui/screen is ported (later phase)
		add("suspects", Keyboard.KEY_NONE, NOOP);
		// opens the table editor screen once ui/screen is ported (later phase)
		add("move_table", Keyboard.KEY_NONE, NOOP);
		// opens ProfileScreen once ui/screen is ported (later phase)
		add("profile_target", Keyboard.KEY_NONE, NOOP);

		add("toggle_tab", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				toggle("Tab list stats", mod.settings().tab.enabled = !mod.settings().tab.enabled);
			}
		});
		add("toggle_nametags", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				toggle("Nametag stars", mod.settings().nametag.stars = !mod.settings().nametag.stars);
			}
		});
		add("toggle_tooltip", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				toggle("Look tooltip", mod.settings().tooltip.enabled = !mod.settings().tooltip.enabled);
			}
		});
		add("toggle_chat_hover", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				toggle("Chat hover", mod.settings().alerts.chatHover = !mod.settings().alerts.chatHover);
			}
		});
		add("toggle_nick_alert", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				toggle("Nick alert", mod.settings().alerts.nickAlert = !mod.settings().alerts.nickAlert);
			}
		});
		add("toggle_proximity", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				toggle("Proximity alerts", mod.settings().proximity.enabled = !mod.settings().proximity.enabled);
			}
		});

		add("cycle_sort", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				SortMode[] modes = SortMode.values();
				SortMode next = modes[(mod.settings().table.sort.ordinal() + 1) % modes.length];
				mod.settings().table.sort = next;
				mod.saveSettings();
				say("Sorting by " + next.name().toLowerCase(Locale.ROOT) + ".");
			}
		});

		add("party_report", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.partyReport().send(PartyReport.Channel.PARTY);
			}
		});
		add("team_report", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.partyReport().send(PartyReport.Channel.TEAM);
			}
		});
		add("party_list", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.partyReport().sendAll(PartyReport.Channel.PARTY);
			}
		});
		add("team_list", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.partyReport().sendAll(PartyReport.Channel.TEAM);
			}
		});
		// Everybody flagged for cheating, with how sure the mod is — not wired yet: game.CheatSensor
		// (the source of a Suspicion) is a later phase on this branch, not part of this one.
		add("cheats_party", Keyboard.KEY_NONE, NOOP);
		add("cheats_team", Keyboard.KEY_NONE, NOOP);
		// Whoever is under the crosshair, into chat in one press: the call-out mid-fight.
		add("send_target_team", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				sendTarget(PartyReport.Channel.TEAM);
			}
		});
		add("send_target_party", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				sendTarget(PartyReport.Channel.PARTY);
			}
		});
		add("refresh", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.refresh();
				say("Looking everybody up again.");
			}
		});

		// Unbound like everything else mid-game: the player picks the keys on the Callouts tab.
		for (int slot = 0; slot < ScoutSettings.CALLOUTS; slot++) {
			final int index = slot;
			calloutKeys[slot] = add("callout_" + (slot + 1), Keyboard.KEY_NONE, new Runnable() {
				@Override
				public void run() {
					mod.callouts().fire(index);
				}
			});
		}

		int[] numpad = {Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3,
				Keyboard.KEY_NUMPAD4, Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6,
				Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9};
		for (int slot = 0; slot < ScoutSettings.QUEUE_SLOTS; slot++) {
			final int index = slot;
			queueKeys[slot] = add("queue_" + (slot + 1), numpad[slot], new Runnable() {
				@Override
				public void run() {
					mod.queue().queueSlot(index);
				}
			});
		}

		add("queue_random", Keyboard.KEY_NUMPAD0, new Runnable() {
			@Override
			public void run() {
				mod.queue().queueRandom();
			}
		});
	}

	private static final Runnable NOOP = new Runnable() {
		@Override
		public void run() {
			// Nothing to do yet: the screen this key opens is a later phase.
		}
	};

	private KeyBinding add(String name, int defaultKey, Runnable run) {
		KeyBinding binding = new KeyBinding("key.hypixelscout." + name, defaultKey, CATEGORY);
		ClientRegistry.registerKeyBinding(binding);
		actions.add(new Action(binding, run));
		return binding;
	}

	/**
	 * Called every client tick by {@link HypixelScout}. One-shot keys fire through
	 * {@link KeyBinding#isPressed()} (Forge's own since-last-poll queue, the 1.8.9 equivalent of
	 * 26.2's {@code consumeClick()}), skipped entirely while a screen is open or before a world is
	 * loaded so nothing fires while the player is typing in chat.
	 */
	public void tick(Minecraft minecraft) {
		if (minecraft.thePlayer == null || minecraft.currentScreen != null) {
			// Still drain the queues, or every press made while a screen was open would all fire
			// at once the instant it closes.
			for (Action action : actions) {
				while (action.binding().isPressed()) {
					// discarded
				}
			}
			return;
		}

		for (Action action : actions) {
			while (action.binding().isPressed()) {
				action.run();
			}
		}
	}

	/** The peek key's raw state, for the peek HUD element a later phase adds. */
	public boolean isPeekHeld() {
		return peekKey.isKeyDown() && Minecraft.getMinecraft().currentScreen == null;
	}

	/** The table key's raw state, for the table HUD element a later phase adds. */
	public boolean isTableHeld() {
		return tableKey.isKeyDown();
	}

	/** The peek binding itself, for a later phase (and the client game test) to hold down. */
	public KeyBinding peekBinding() {
		return peekKey;
	}

	/** The table binding itself, for a later phase to read or show next to its toggle. */
	public KeyBinding tableBinding() {
		return tableKey;
	}

	/** The binding of callout {@code slot} (from 0), for the Callouts tab a later phase adds. */
	public KeyBinding calloutBinding(int slot) {
		return calloutKeys[slot];
	}

	/** The binding of queue slot {@code slot} (from 0), for the Queue tab a later phase adds. */
	public KeyBinding queueBinding(int slot) {
		return queueKeys[slot];
	}

	private void toggle(String label, boolean on) {
		mod.saveSettings();
		say(label + " " + (on ? EnumChatFormatting.GREEN + "on" : EnumChatFormatting.RED + "off")
				+ EnumChatFormatting.GRAY + ".");
	}

	private void sendTarget(PartyReport.Channel channel) {
		ScoutSettings.Tooltip tooltip = mod.settings().tooltip;
		EntityPlayer target = LookTarget.pick(tooltip.cosine(), tooltip.throughWalls);
		if (target == null) {
			say(EnumChatFormatting.RED + "Not looking at anybody.");
			return;
		}

		String name = target.getName();
		UUID uuid = mod.roster().uuidOf(name);
		mod.partyReport().sendPlayer(channel, name,
				mod.stats().peek(uuid == null ? target.getGameProfile().getId() : uuid));
	}

	private static void say(String message) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null) {
			mc.thePlayer.addChatMessage(new ChatComponentText(
					EnumChatFormatting.GOLD + "[Scout] " + EnumChatFormatting.RESET + EnumChatFormatting.GRAY
							+ message));
		}
	}
}
