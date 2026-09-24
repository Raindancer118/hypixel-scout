package de.raindancer118.hypixelscout.ui;

import de.raindancer118.hypixelscout.config.Accent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.function.Supplier;

/**
 * The mod's lines in the player's own chat log.
 *
 * <p>Client side only: nothing here is ever sent to the server. Safe to call from any thread — the
 * line is handed to the render thread via {@link Minecraft#addScheduledTask}, because Minecraft's
 * chat is not safe to touch from anywhere else and the stats arrive on worker threads.
 *
 * <p>1.8.9 has no {@code Component}/{@code MutableComponent}: this builds {@link IChatComponent}s
 * out of {@link ChatComponentText} and {@link ChatStyle} instead, and prints locally through
 * {@code EntityPlayerSP.addChatMessage} — the client-side override that never round-trips to the
 * server, exactly like 26.2's {@code addClientSystemMessage}.
 */
public final class Chat {
	private static Supplier<Accent> accent = new Supplier<Accent>() {
		@Override
		public Accent get() {
			return Accent.GOLD;
		}
	};

	private Chat() {
	}

	public static void useAccent(Supplier<Accent> source) {
		accent = source;
	}

	/** {@code [Scout]} in the accent colour, then the message in gray. */
	public static IChatComponent prefixed(IChatComponent message) {
		IChatComponent prefix = new ChatComponentText("[Scout] ");
		prefix.setChatStyle(new ChatStyle().setColor(formattingOf(accent.get())));

		IChatComponent body = new ChatComponentText("");
		body.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY));
		body.appendSibling(message);

		IChatComponent line = new ChatComponentText("");
		line.appendSibling(prefix);
		line.appendSibling(body);
		return line;
	}

	public static void say(IChatComponent message) {
		Minecraft client = Minecraft.getMinecraft();
		client.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				if (client.thePlayer != null) {
					client.thePlayer.addChatMessage(prefixed(message));
				}
			}
		});
	}

	/** A line built from section-sign codes, as {@code StatFormat} produces them. */
	public static void sayLegacy(String message) {
		say(new ChatComponentText(message));
	}

	public static void sayTranslated(String key, Object... args) {
		say(new ChatComponentTranslation(key, args));
	}

	/** The formatting colour closest to an accent, since 1.8.9 chat styling has no raw ARGB. */
	private static EnumChatFormatting formattingOf(Accent value) {
		switch (value.legacyCode()) {
			case '6':
				return EnumChatFormatting.GOLD;
			case 'b':
				return EnumChatFormatting.AQUA;
			case 'a':
				return EnumChatFormatting.GREEN;
			case 'c':
				return EnumChatFormatting.RED;
			case 'd':
				return EnumChatFormatting.LIGHT_PURPLE;
			case '9':
				return EnumChatFormatting.BLUE;
			case 'f':
				return EnumChatFormatting.WHITE;
			default:
				return EnumChatFormatting.GOLD;
		}
	}
}
