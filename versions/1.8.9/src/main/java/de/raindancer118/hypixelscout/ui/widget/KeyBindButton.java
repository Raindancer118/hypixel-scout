package de.raindancer118.hypixelscout.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;

/**
 * One key binding as a button: click it, press a key or a mouse button, done — the same idea as
 * 26.2's {@code ui.widget.KeyBindButton}, and stored in the very same {@link KeyBinding}, so this
 * button and vanilla's own controls screen always agree.
 *
 * <p>1.8.9 has no {@code InputConstants}: a mouse button is folded into the same {@code int} space
 * {@link KeyBinding#getKeyCode()} already uses for keyboard keys, the same convention vanilla's own
 * {@code GuiControls}/{@code GuiKeyBindingList} uses — {@code -100 - button}, so 0 (left) becomes
 * -100, 1 (right) becomes -101, and so on; {@link #encodeMouseButton}/{@link #keyName} are the two
 * ends of that encoding.
 */
public final class KeyBindButton extends ThemedButton implements Clickable {
	private final KeyBinding mapping;
	private final String label;
	private final Consumer<KeyBindButton> onListen;
	private boolean listening;

	/** @param label a short name for the binding; the controls screen's own is rarely as short */
	public KeyBindButton(int id, int x, int y, int width, int height, KeyBinding mapping, String label,
			Consumer<KeyBindButton> onListen) {
		super(id, x, y, width, height, "");
		this.mapping = mapping;
		this.label = label;
		this.onListen = onListen;
		refresh();
	}

	public static int encodeMouseButton(int button) {
		return -100 - button;
	}

	@Override
	public void onClick() {
		listening = true;
		onListen.accept(this);
		refresh();
	}

	public boolean isListening() {
		return listening;
	}

	public void stopListening() {
		listening = false;
		refresh();
	}

	/** Binds what was pressed; {@code Keyboard.KEY_ESCAPE} clears the binding instead, as in vanilla. */
	public void bind(int keyCode) {
		mapping.setKeyCode(keyCode == Keyboard.KEY_ESCAPE ? Keyboard.KEY_NONE : keyCode);
		KeyBinding.resetKeyBindingArrayAndHash();
		Minecraft.getMinecraft().gameSettings.saveOptions();
		listening = false;
		refresh();
	}

	/** Relabels after any binding changed, since a conflict can appear or disappear elsewhere. */
	public void refresh() {
		String key;
		String colour;

		if (listening) {
			key = "> " + keyName(mapping.getKeyCode()) + " <";
			colour = "§e";
		} else if (mapping.getKeyCode() == Keyboard.KEY_NONE) {
			key = StatCollector.translateToLocal("message.hypixelscout.settings.keys.none");
			colour = "§7";
		} else {
			key = keyName(mapping.getKeyCode());
			colour = clash() != null ? "§c" : "§f";
		}

		displayString = label + ": " + colour + key;
	}

	/** The first other binding sharing this one's key, or {@code null} if there is none. */
	private KeyBinding clash() {
		if (mapping.getKeyCode() == Keyboard.KEY_NONE || listening) {
			return null;
		}

		GameSettings settings = Minecraft.getMinecraft().gameSettings;
		for (KeyBinding other : settings.keyBindings) {
			if (other != mapping && other.getKeyCode() == mapping.getKeyCode()
					&& !other.getKeyDescription().startsWith("key.forge.")) {
				return other;
			}
		}
		return null;
	}

	private static String keyName(int keyCode) {
		if (keyCode == Keyboard.KEY_NONE) {
			return StatCollector.translateToLocal("message.hypixelscout.settings.keys.none");
		}
		if (keyCode < 0) {
			int button = -100 - keyCode;
			switch (button) {
				case 0:
					return "Left Mouse";
				case 1:
					return "Right Mouse";
				case 2:
					return "Middle Mouse";
				default:
					return "Mouse " + (button + 1);
			}
		}
		return Keyboard.getKeyName(keyCode);
	}
}
