package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.StatsService;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stats on the player names in chat, shown on hover and opening the profile on click.
 *
 * <p>The line is rebuilt from its formatted text, with the names split out into their own
 * components. That loses whatever the server hung on the original components, so a line that
 * already carries a click action — a party invite, a friend request — is left exactly as it came.
 * Breaking those to add a tooltip would be a poor trade.
 */
public final class ChatHover {
	private static final Pattern WORD = Pattern.compile("\\w{3,16}");

	private final RosterTracker roster;
	private final StatsService stats;
	private final ScoutConfig config;

	public ChatHover(RosterTracker roster, StatsService stats, ScoutConfig config) {
		this.roster = roster;
		this.stats = stats;
		this.config = config;
	}

	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		// Type 2 is the action bar above the hotbar, which has no hover and no room for one.
		if (event.type == 2 || !config.isChatHoverEnabled() || !roster.isInBedwars()) {
			return;
		}

		if (event.message == null || carriesItsOwnActions(event.message)) {
			return;
		}

		String formatted = event.message.getFormattedText();
		IChatComponent rebuilt = rebuild(formatted);
		if (rebuilt != null) {
			event.message = rebuilt;
		}
	}

	/**
	 * Splits the line around the names of players in this game and gives each of them a tooltip.
	 * Returns null when there was nothing to do, so the original object survives untouched.
	 */
	private IChatComponent rebuild(String formatted) {
		Matcher matcher = WORD.matcher(formatted);
		ChatComponentText rebuilt = null;
		int cursor = 0;

		while (matcher.find()) {
			UUID uuid = roster.uuidOf(matcher.group());
			if (uuid == null) {
				continue;
			}

			if (rebuilt == null) {
				rebuilt = new ChatComponentText("");
			}

			rebuilt.appendSibling(new ChatComponentText(formatted.substring(cursor,
					matcher.start())));
			rebuilt.appendSibling(nameComponent(matcher.group(), uuid));
			cursor = matcher.end();
		}

		if (rebuilt == null) {
			return null;
		}

		rebuilt.appendSibling(new ChatComponentText(formatted.substring(cursor)));
		return rebuilt;
	}

	private IChatComponent nameComponent(String name, UUID uuid) {
		PlayerStats playerStats = stats.peek(uuid);
		List<String> detail = StatsLines.detail(name, playerStats, stats.isPending(uuid),
				stats.failureFor(uuid));

		StringBuilder tooltip = new StringBuilder();
		for (int i = 0; i < detail.size(); i++) {
			if (i > 0) {
				tooltip.append('\n');
			}
			tooltip.append(detail.get(i));
		}

		ChatStyle style = new ChatStyle()
				.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						new ChatComponentText(tooltip.toString())))
				.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
						"/scout " + name));

		ChatComponentText component = new ChatComponentText(name);
		component.setChatStyle(style);

		return component;
	}

	/** A component with a click or hover of its own belongs to the server; leave it be. */
	private boolean carriesItsOwnActions(IChatComponent component) {
		ChatStyle style = component.getChatStyle();
		if (style != null && (style.getChatClickEvent() != null
				|| style.getChatHoverEvent() != null)) {
			return true;
		}

		for (Object sibling : component.getSiblings()) {
			if (carriesItsOwnActions((IChatComponent) sibling)) {
				return true;
			}
		}

		return false;
	}
}
