package de.raindancer118.hypixelscout.ui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * One key binding as a button: click it, press a key or a mouse button, done — the same as
 * vanilla's controls screen, and stored in the same place, so both screens always agree.
 *
 * <p>The screen holding it routes the next key or click here while it is listening. Escape
 * unbinds, as in vanilla. A key another binding already uses is shown in red, with the other one
 * named in the tooltip.
 */
public final class KeyBindButton extends Button.Plain {
	private final KeyMapping mapping;
	private final Component label;
	private final Consumer<KeyBindButton> onListen;
	private boolean listening;

	/** @param label a short name for the binding; the controls screen's full one rarely fits a button */
	public KeyBindButton(int width, KeyMapping mapping, Component label, Consumer<KeyBindButton> onListen) {
		super(0, 0, width, 20, Component.empty(), button -> ((KeyBindButton) button).listen(), DEFAULT_NARRATION);
		this.mapping = mapping;
		this.label = label;
		this.onListen = onListen;
		refresh();
	}

	private void listen() {
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

	/** Binds what was pressed; Escape clears the binding instead. */
	public void bind(InputConstants.Key key) {
		Minecraft minecraft = Minecraft.getInstance();
		mapping.setKey(key.getValue() == InputConstants.KEY_ESCAPE && key.getType() == InputConstants.Type.KEYSYM
				? InputConstants.UNKNOWN : key);
		KeyMapping.resetMapping();
		minecraft.options.save();
		listening = false;
		refresh();
	}

	/** Relabels after any binding changed, since a conflict can appear or disappear elsewhere. */
	public void refresh() {
		Component key = mapping.getTranslatedKeyMessage();
		MutableComponent value;

		if (listening) {
			value = Component.literal("> ").append(key.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
					.append(" <").withStyle(ChatFormatting.YELLOW);
		} else if (mapping.isUnbound()) {
			value = Component.translatable("message.hypixelscout.settings.keys.none").withStyle(ChatFormatting.GRAY);
		} else {
			value = key.copy();
		}

		List<KeyMapping> clashes = clashes();
		if (!listening && !clashes.isEmpty()) {
			value = value.withStyle(ChatFormatting.RED);
			setTooltip(Tooltip.create(Component.translatable("message.hypixelscout.settings.keys.clash",
					Component.translatable(clashes.getFirst().getName()))));
		} else {
			setTooltip(Tooltip.create(Component.translatable(mapping.getName()).append("\n")
					.append(Component.translatable("message.hypixelscout.settings.keys.hint")
							.withStyle(ChatFormatting.GRAY))));
		}

		setMessage(label.copy().append(": ").append(value));
	}

	private List<KeyMapping> clashes() {
		if (mapping.isUnbound()) {
			return List.of();
		}

		return Arrays.stream(Minecraft.getInstance().options.keyMappings)
				// The debug keys only act together with F3, so sharing a letter with one is no clash.
				.filter(other -> other != mapping && !other.getName().startsWith("key.debug.") && other.same(mapping))
				.toList();
	}
}
