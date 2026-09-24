package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.CardLines;
import de.raindancer118.hypixelscout.core.ChatNames;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.Threats;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stats on the player names in chat: hovering a name shows them, clicking opens the profile.
 *
 * <p>The line is rebuilt piece by piece, each piece keeping its own style, and only the names gain
 * a tooltip and a click. A line that already carries a click or hover of its own — a party invite,
 * a friend request — is left exactly as it came: breaking those for a tooltip would be a poor trade.
 *
 * <p>Ported from 26.2's {@code game.ChatHover}. 1.8.9 has no {@code Component#visit} walking API:
 * {@code getUnformattedTextForChat()} gives one component's own text (not its
 * children's), so the tree is rebuilt by hand — one new container per original component, its own
 * text split into name spans, then every sibling rebuilt the same way and re-attached. The Forge
 * hook is {@link ClientChatReceivedEvent}, whose mutable {@code message} field this replaces
 * in place — the 1.8.9 equivalent of 26.2's {@code ClientReceiveMessageEvents.MODIFY_GAME}.
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

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		// Type 2 is the action bar above the hotbar: no hover, no room for one.
		if (event.type == 2 || event.message == null || !settings.get().alerts.chatHover
				|| !roster.isInGame() || carriesItsOwnActions(event.message)) {
			return;
		}

		boolean[] changed = {false};
		IChatComponent rebuilt = rebuild(event.message, changed);
		if (changed[0]) {
			event.message = rebuilt;
		}
	}

	private IChatComponent rebuild(IChatComponent original, boolean[] changed) {
		ChatStyle style = original.getChatStyle();
		IChatComponent container = new ChatComponentText("");
		if (style != null) {
			container.setChatStyle(style.createShallowCopy());
		}

		String text = original.getUnformattedTextForChat();
		if (text != null && !text.isEmpty()) {
			appendWithNames(container, text, style, changed);
		}

		for (Object siblingObj : original.getSiblings()) {
			container.appendSibling(rebuild((IChatComponent) siblingObj, changed));
		}

		return container;
	}

	private void appendWithNames(IChatComponent container, String text, ChatStyle style, boolean[] changed) {
		List<ChatNames.Span> spans = ChatNames.find(text, roster::contains);
		int cursor = 0;

		for (ChatNames.Span span : spans) {
			if (span.start() > cursor) {
				container.appendText(text.substring(cursor, span.start()));
			}

			IChatComponent name = new ChatComponentText(span.name());
			name.setChatStyle(decorate(style, span.name()));
			container.appendSibling(name);

			cursor = span.end();
			changed[0] = true;
		}

		if (cursor < text.length()) {
			container.appendText(text.substring(cursor));
		}
	}

	private ChatStyle decorate(ChatStyle style, String name) {
		UUID uuid = roster.uuidOf(name);
		List<String> lines = CardLines.lines(name, uuid == null ? null : stats.peek(uuid),
				uuid != null && stats.isPending(uuid), uuid == null ? null : stats.failureFor(uuid),
				Threats.scale(), settings.get().cards.hover.layout(), Suspects.cardExtras(name));

		StringBuilder tooltip = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				tooltip.append('\n');
			}
			tooltip.append(lines.get(i));
		}
		// The lang file's click-hint key carries a literal "\n" escape (see LanguageKeysTest's
		// notes) rather than an embedded newline — a .lang value is one line, so it was encoded
		// that way and is decoded back here, the one place that ever renders it.
		tooltip.append('\n').append(StatCollector.translateToLocal("message.hypixelscout.chat.click_hint")
				.replace("\\n", "\n"));

		ChatStyle decorated = style == null ? new ChatStyle() : style.createShallowCopy();
		decorated.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ChatComponentText(tooltip.toString())));
		decorated.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/scout " + name));
		return decorated;
	}

	/** A component with a click or hover of its own belongs to the server; leave it be. */
	static boolean carriesItsOwnActions(IChatComponent component) {
		ChatStyle style = component.getChatStyle();
		if (style != null && (style.getChatClickEvent() != null || style.getChatHoverEvent() != null)) {
			return true;
		}

		for (Object siblingObj : component.getSiblings()) {
			if (carriesItsOwnActions((IChatComponent) siblingObj)) {
				return true;
			}
		}

		return false;
	}
}
