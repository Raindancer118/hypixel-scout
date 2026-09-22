package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.Accent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Supplier;

/**
 * The mod's lines in the player's own chat log.
 *
 * <p>Client side only: nothing here is ever sent to the server. Safe to call from any thread — the
 * line is handed to the render thread, because Minecraft's chat is not safe to touch from anywhere
 * else and the stats arrive on worker threads.
 */
public final class Chat {
	private static Supplier<Accent> accent = () -> Accent.GOLD;

	private Chat() {
	}

	public static void useAccent(Supplier<Accent> source) {
		accent = source;
	}

	/** {@code [Scout]} in the accent colour, then the message. */
	public static MutableComponent prefixed(Component message) {
		return Component.empty()
				.append(Component.literal("[Scout] ").withColor(accent.get().argb() & 0xFFFFFF))
				.append(Component.empty().withStyle(ChatFormatting.GRAY).append(message));
	}

	public static void say(Component message) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.player != null) {
				client.gui.hud.getChat().addClientSystemMessage(prefixed(message));
			}
		});
	}

	/** A line built from section-sign codes, as {@code StatFormat} produces them. */
	public static void sayLegacy(String message) {
		say(Component.literal(message));
	}

	public static void sayTranslated(String key, Object... args) {
		say(Component.translatable(key, args));
	}
}
