package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.ChatNames;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatLines;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stats on the player names in chat: hovering a name shows them, clicking opens the profile.
 *
 * <p>The line is rebuilt piece by piece with every piece keeping its own style, and only the names
 * gain a tooltip and a click. A line that already carries a click or hover of its own — a party
 * invite, a friend request — is left exactly as it came: breaking those for a tooltip would be a
 * poor trade.
 */
public final class ChatHover {
	private final Roster roster;
	private final StatsService stats;
	private final Supplier<ScoutSettings> settings;

	public ChatHover(Roster roster, StatsService stats, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.settings = settings;
	}

	public Component modify(Component message, boolean overlay) {
		// The action bar above the hotbar has no hover and no room for one.
		if (overlay || message == null || !settings.get().alerts.chatHover || !roster.isInGame()
				|| carriesItsOwnActions(message)) {
			return message;
		}

		MutableComponent rebuilt = Component.empty();
		boolean[] changed = {false};

		message.visit((style, text) -> {
			List<ChatNames.Span> spans = ChatNames.find(text, roster::contains);
			int cursor = 0;

			for (ChatNames.Span span : spans) {
				if (span.start() > cursor) {
					rebuilt.append(Component.literal(text.substring(cursor, span.start())).withStyle(style));
				}

				rebuilt.append(Component.literal(span.name()).withStyle(decorate(style, span.name())));
				cursor = span.end();
				changed[0] = true;
			}

			if (cursor < text.length()) {
				rebuilt.append(Component.literal(text.substring(cursor)).withStyle(style));
			}

			return Optional.empty();
		}, Style.EMPTY);

		return changed[0] ? rebuilt : message;
	}

	private Style decorate(Style style, String name) {
		UUID uuid = roster.uuidOf(name);
		List<String> lines = StatLines.detail(name, uuid == null ? null : stats.peek(uuid),
				uuid != null && stats.isPending(uuid), uuid == null ? null : stats.failureFor(uuid));

		MutableComponent tooltip = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			tooltip.append(Component.literal((i > 0 ? "\n" : "") + lines.get(i)));
		}
		tooltip.append(Component.translatable("message.hypixelscout.chat.click_hint")
				.withColor(0x707070));

		return style.withHoverEvent(new HoverEvent.ShowText(tooltip))
				.withClickEvent(new ClickEvent.RunCommand("/scout " + name));
	}

	/** A component with a click or hover of its own belongs to the server; leave it be. */
	static boolean carriesItsOwnActions(Component component) {
		Style style = component.getStyle();
		if (style.getClickEvent() != null || style.getHoverEvent() != null) {
			return true;
		}

		for (Component sibling : component.getSiblings()) {
			if (carriesItsOwnActions(sibling)) {
				return true;
			}
		}

		return false;
	}
}
