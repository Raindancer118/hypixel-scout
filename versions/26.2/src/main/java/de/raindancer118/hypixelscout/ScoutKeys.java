package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.game.LookTarget;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.ui.Chat;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Every function of the mod, on a key.
 *
 * <p>One place for all of them, so the controls screen shows the mod as one block and nothing is
 * reachable by command but not by key. Almost everything defaults to unbound: vanilla and the
 * launchers have taken the comfortable keys already, and a mod that quietly claims a dozen more
 * breaks somebody's muscle memory. The exceptions — the mod's screen, the table and the queue slots
 * on the number pad — sit on keys vanilla leaves free.
 */
public final class ScoutKeys {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HypixelScout.MOD_ID, "main"));

	private record Action(KeyMapping mapping, Runnable run) {
	}

	private final HypixelScout mod;
	private final List<Action> actions = new ArrayList<>();
	private final KeyMapping[] queueKeys = new KeyMapping[ScoutSettings.QUEUE_SLOTS];
	private KeyMapping tableKey;
	private KeyMapping peekKey;

	public ScoutKeys(HypixelScout mod) {
		this.mod = mod;
	}

	public void register() {
		add("open", GLFW.GLFW_KEY_K, () -> HypixelScout.open(mod.scoutScreen(null)));
		tableKey = add("table", GLFW.GLFW_KEY_Y, () -> mod.table().toggle());
		// Held, not pressed: the stats are up exactly as long as the key is down.
		peekKey = add("peek", GLFW.GLFW_KEY_R, () -> { });
		add("settings", GLFW.GLFW_KEY_UNKNOWN, () -> HypixelScout.open(mod.settingsScreen(null)));
		add("move_table", GLFW.GLFW_KEY_UNKNOWN, () -> HypixelScout.open(mod.tableEditor(null)));
		// The profile of whoever is under the crosshair: the one lookup nobody can type fast enough.
		add("profile_target", GLFW.GLFW_KEY_UNKNOWN, this::profileOfTarget);

		add("toggle_tab", GLFW.GLFW_KEY_UNKNOWN, () -> toggle("tab",
				mod.settings().tab.enabled = !mod.settings().tab.enabled));
		add("toggle_nametags", GLFW.GLFW_KEY_UNKNOWN, () -> toggle("nametags",
				mod.settings().nametag.stars = !mod.settings().nametag.stars));
		add("toggle_tooltip", GLFW.GLFW_KEY_UNKNOWN, () -> toggle("tooltip",
				mod.settings().tooltip.enabled = !mod.settings().tooltip.enabled));
		add("toggle_chat_hover", GLFW.GLFW_KEY_UNKNOWN, () -> toggle("chat_hover",
				mod.settings().alerts.chatHover = !mod.settings().alerts.chatHover));
		add("toggle_nick_alert", GLFW.GLFW_KEY_UNKNOWN, () -> toggle("nick_alert",
				mod.settings().alerts.nickAlert = !mod.settings().alerts.nickAlert));

		add("cycle_sort", GLFW.GLFW_KEY_UNKNOWN, () -> {
			SortMode[] modes = SortMode.values();
			SortMode next = modes[(mod.settings().table.sort.ordinal() + 1) % modes.length];
			mod.settings().table.sort = next;
			mod.saveSettings();
			Chat.say(Component.translatable("message.hypixelscout.sort.now",
					Component.translatable("message.hypixelscout.sort." + next.name().toLowerCase(Locale.ROOT))));
		});

		add("party_report", GLFW.GLFW_KEY_UNKNOWN, () -> mod.partyReport().send(PartyReport.Channel.PARTY));
		add("team_report", GLFW.GLFW_KEY_UNKNOWN, () -> mod.partyReport().send(PartyReport.Channel.TEAM));
		add("refresh", GLFW.GLFW_KEY_UNKNOWN, () -> {
			mod.refresh();
			Chat.sayTranslated("message.hypixelscout.refreshed");
		});

		int[] numpad = {GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3, GLFW.GLFW_KEY_KP_4,
				GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_KP_6, GLFW.GLFW_KEY_KP_7, GLFW.GLFW_KEY_KP_8,
				GLFW.GLFW_KEY_KP_9};
		for (int slot = 0; slot < ScoutSettings.QUEUE_SLOTS; slot++) {
			int index = slot;
			queueKeys[slot] = add("queue_" + (slot + 1), numpad[slot], () -> mod.queue().queueSlot(index));
		}

		add("queue_random", GLFW.GLFW_KEY_KP_0, () -> mod.queue().queueRandom());
	}

	private KeyMapping add(String name, int defaultKey, Runnable run) {
		KeyMapping mapping = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.hypixelscout." + name, defaultKey, CATEGORY));
		actions.add(new Action(mapping, run));
		return mapping;
	}

	void tick(Minecraft minecraft) {
		for (Action action : actions) {
			// consumeClick only counts presses made with no screen open, so typing in chat never
			// fires any of these.
			while (action.mapping().consumeClick()) {
				if (minecraft.player != null) {
					action.run().run();
				}
			}
		}

		// Hold mode needs the key's state, not its presses.
		mod.table().setKeyHeld(tableKey.isDown());
		mod.peek().setHeld(peekKey.isDown() && minecraft.gui.screen() == null);
	}

	/** The bindings the mod's own settings offer to change, in the order they are shown. */
	public List<KeyMapping> settingsMappings() {
		List<String> shown = List.of("open", "peek", "table", "profile_target", "team_report", "party_report",
				"move_table", "refresh");
		return shown.stream()
				.map(name -> "key.hypixelscout." + name)
				.map(id -> actions.stream().map(Action::mapping).filter(m -> m.getName().equals(id)).findFirst()
						.orElseThrow())
				.toList();
	}

	/** The peek key itself, for the client game test to hold down. */
	public KeyMapping peekMapping() {
		return peekKey;
	}

	/** The key bound to a queue slot, for the queue tab to show next to it. */
	public Component queueKey(int slot) {
		return queueKeys[slot].getTranslatedKeyMessage();
	}

	public boolean queueKeyBound(int slot) {
		return !queueKeys[slot].isUnbound();
	}

	private void toggle(String feature, boolean on) {
		mod.saveSettings();
		Chat.say(Component.translatable("message.hypixelscout.toggle." + feature,
				Component.translatable(on ? "options.on" : "options.off")
						.withColor(on ? 0x55FF55 : 0xFF5555)));
	}

	private void profileOfTarget() {
		ScoutSettings.Tooltip tooltip = mod.settings().tooltip;
		AbstractClientPlayer target = LookTarget.pick(tooltip.cosine(), tooltip.throughWalls, 1.0f);
		if (target == null) {
			Chat.sayTranslated("message.hypixelscout.no_target");
			return;
		}

		String name = target.getScoreboardName();
		UUID uuid = mod.roster().uuidOf(name);
		HypixelScout.open(mod.profileScreen(name, uuid == null ? target.getUUID() : uuid, null));
	}
}
