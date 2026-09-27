package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.screen.ProfileScreen;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
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
 * <p>Every key is fully live as of Phase 3 of this branch's port (see {@code Project.md}): the ones
 * that open a screen ({@code open}, {@code settings}, {@code suspects}, {@code move_table}, {@code
 * profile_target}) now do, on top of the table/peek keys Phase 2a wired and the settings toggles,
 * sort cycling, party/team reports, callouts, queue slots and key check Phase 1 already had live.
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
	/** One press toggles {@code ui.hud.TableHudElement}; also read held each tick for HudMode.HOLD. */
	private KeyBinding tableKey;
	/** Held, not pressed: the peek stats are up exactly as long as this is down. */
	private KeyBinding peekKey;

	public ScoutKeys(HypixelScout mod) {
		this.mod = mod;
	}

	public void register() {
		// Opens the mod's own screen, on the game tab.
		add("open", Keyboard.KEY_K, new Runnable() {
			@Override
			public void run() {
				Minecraft.getMinecraft().displayGuiScreen(new ScoutScreen(mod, null, ScoutScreen.Page.GAME));
			}
		});
		// One press toggles it open/closed (HudMode.TOGGLE); tick() also feeds the raw key state to
		// the table for HudMode.HOLD, exactly as 26.2's own table key does.
		tableKey = add("table", Keyboard.KEY_Y, new Runnable() {
			@Override
			public void run() {
				mod.table().toggle();
			}
		});
		// Held, not pressed: see isPeekHeld() — read every tick in HypixelScout.onTick().
		peekKey = add("peek", Keyboard.KEY_R, NOOP);
		// Opens the mod's own settings screen.
		add("settings", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				Minecraft.getMinecraft().displayGuiScreen(mod.settingsScreen(null));
			}
		});
		// Opens the mod's own screen, on the CHEATS page.
		add("suspects", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				Minecraft.getMinecraft().displayGuiScreen(new ScoutScreen(mod, null, ScoutScreen.Page.CHEATS));
			}
		});
		// Opens the table editor screen.
		add("move_table", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				Minecraft.getMinecraft().displayGuiScreen(mod.tableEditor(null));
			}
		});
		// Opens the profile screen for whoever is under the crosshair.
		add("profile_target", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				ScoutSettings.Tooltip tooltip = mod.settings().tooltip;
				EntityPlayer target = LookTarget.pick(tooltip.cosine(), tooltip.throughWalls);
				if (target == null) {
					say(EnumChatFormatting.RED + "Not looking at anybody.");
					return;
				}
				String name = target.getName();
				Minecraft.getMinecraft().displayGuiScreen(new ProfileScreen(mod, name, mod.roster().uuidOf(name), null));
			}
		});

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
		// Everybody flagged for cheating, with how sure the mod is.
		add("cheats_party", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.partyReport().sendCheats(PartyReport.Channel.PARTY,
						de.raindancer118.scout.api.ScoutApi.get().suspicion());
			}
		});
		add("cheats_team", Keyboard.KEY_NONE, new Runnable() {
			@Override
			public void run() {
				mod.partyReport().sendCheats(PartyReport.Channel.TEAM,
						de.raindancer118.scout.api.ScoutApi.get().suspicion());
			}
		});
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

	/** Only {@code peek} uses this: it is read as a held state ({@link #isPeekHeld()}), not fired as a press. */
	private static final Runnable NOOP = new Runnable() {
		@Override
		public void run() {
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

	/** The peek key's raw state, read by {@code HypixelScout.onTick} for {@code ui.hud.PeekElement}. */
	public boolean isPeekHeld() {
		return peekKey.isKeyDown() && Minecraft.getMinecraft().currentScreen == null;
	}

	/** The table key's raw state, read by {@code HypixelScout.onTick} for HudMode.HOLD. */
	public boolean isTableHeld() {
		return tableKey.isKeyDown();
	}

	/** The peek binding itself, for the client startup test to hold down. */
	public KeyBinding peekBinding() {
		return peekKey;
	}

	/** The table binding itself, for a later phase's settings screen to show next to its toggle. */
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

	public boolean queueKeyBound(int slot) {
		return queueKeys[slot].getKeyCode() != Keyboard.KEY_NONE;
	}

	/** The bound key's own display name, for the queue tab's badge. */
	public String queueKeyName(int slot) {
		int code = queueKeys[slot].getKeyCode();
		if (code < 0) {
			return "Mouse";
		}
		// "NUMPAD1".."NUMPAD9" (LWJGL's own names) do not fit the queue tab's narrow badge; every
		// default binding here is a numpad digit, so this alone covers what a player sees without a
		// rebind — a genuinely rebound key still falls back to Keyboard.getKeyName's full name.
		String name = Keyboard.getKeyName(code);
		return name.startsWith("NUMPAD") ? "Num " + name.substring("NUMPAD".length()) : name;
	}

	/** The bindings the mod's own settings offer to change, in the order they are shown — mirrors 26.2's own curated list exactly. */
	public List<KeyBinding> settingsMappings() {
		String[] shown = {"open", "peek", "table", "profile_target", "send_target_team", "send_target_party",
				"team_report", "party_report", "team_list", "party_list", "move_table", "refresh"};
		List<KeyBinding> mappings = new ArrayList<KeyBinding>();
		for (String name : shown) {
			String id = "key.hypixelscout." + name;
			for (Action action : actions) {
				if (action.binding().getKeyDescription().equals(id)) {
					mappings.add(action.binding());
					break;
				}
			}
		}
		return mappings;
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
