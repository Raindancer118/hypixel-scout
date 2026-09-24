package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.Eliminations;
import de.raindancer118.hypixelscout.core.RequeueMode;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.ui.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Joins the next game of the same mode once this one is lost: when the player is out, or when the
 * player and every party member in the game are.
 *
 * <p>Off unless switched on. Who is out comes from the final kill lines; who is in the party from
 * the Mod API's party packet, asked for at the start of every game. Only the party leader can move
 * a party, so a member is told instead of sending a {@code /play} Hypixel would refuse. There is a
 * short delay with a clickable cancel before anything is sent.
 *
 * <p>Ported from 26.2's {@code game.AutoRequeue}. Unlike 26.2, this does not create its own {@code
 * ClientboundPartyInfoPacket} handler — {@link LocationBridge} already owns that subscription (a
 * second {@code HypixelModAPI.createHandler} call for the same packet type would either be refused
 * or silently replace the first one) — so party membership is read from {@link LocationBridge}
 * instead, and {@link LocationBridge#requestPartyInfo()} is what {@link #gameJoined()} calls.
 */
public final class AutoRequeue {
	private final Roster roster;
	private final QuickQueue queue;
	private final LocationBridge location;
	private final Supplier<ScoutSettings> settings;
	private final Eliminations eliminations = new Eliminations();

	/** Ticks until {@code /play}; -1 while nothing is scheduled. */
	private int countdown = -1;
	private String pendingMode;
	/** Already scheduled or sent in this game, so later kill lines do not queue twice. */
	private boolean done;

	public AutoRequeue(Roster roster, QuickQueue queue, LocationBridge location, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.queue = queue;
		this.location = location;
		this.settings = settings;
	}

	/**
	 * Nothing to subscribe here — {@link LocationBridge#register()} already did, this method exists
	 * only to mirror 26.2's public surface for whoever wires this class up.
	 */
	public void register() {
		// see class javadoc: the party-info handler lives on LocationBridge.
	}

	/** A new game: everybody is back in, and the party is asked for again. */
	public void gameJoined() {
		reset();
		if (settings.get().requeue.mode != RequeueMode.OFF) {
			location.requestPartyInfo();
		}
	}

	public void reset() {
		eliminations.reset();
		countdown = -1;
		pendingMode = null;
		done = false;
	}

	/** Every chat line, to catch the final kills. */
	public void onChat(String message) {
		if (!roster.isInGame() || message == null) {
			return;
		}

		boolean anyone = false;
		for (String line : message.split("\n")) {
			anyone |= eliminations.record(line) != null;
		}
		if (anyone) {
			check();
		}
	}

	private void check() {
		Minecraft client = Minecraft.getMinecraft();
		ScoutSettings.Requeue requeue = settings.get().requeue;
		if (done || client.thePlayer == null || roster.mode() == null) {
			return;
		}

		if (!Eliminations.requeueDue(requeue.mode, client.thePlayer.getName(), partyInGame(client),
				eliminations::isOut)) {
			return;
		}

		done = true;
		if (!location.party().isEmpty() && location.partyLeader() != null
				&& !location.partyLeader().equals(client.thePlayer.getGameProfile().getId())) {
			Chat.sayTranslated("message.hypixelscout.requeue.not_leader");
			return;
		}

		pendingMode = roster.mode().toLowerCase(Locale.ROOT);
		countdown = requeue.delaySeconds * 20;

		IChatComponent soon = new ChatComponentTranslation("message.hypixelscout.requeue.soon",
				BedwarsModes.shortName(pendingMode), requeue.delaySeconds);

		ChatStyle cancelStyle = new ChatStyle();
		cancelStyle.setColor(EnumChatFormatting.RED);
		cancelStyle.setUnderlined(true);
		cancelStyle.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/scout requeue cancel"));
		cancelStyle.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ChatComponentText(StatCollector.translateToLocal("message.hypixelscout.requeue.cancel.tooltip"))));

		IChatComponent cancel = new ChatComponentTranslation("message.hypixelscout.requeue.cancel");
		cancel.setChatStyle(cancelStyle);

		IChatComponent line = new ChatComponentText("");
		line.appendSibling(soon);
		line.appendText(" ");
		line.appendSibling(cancel);

		Chat.say(line);
	}

	/** The other party members who are playing in this game, by name. */
	private List<String> partyInGame(Minecraft client) {
		return location.partyInGame(roster, client.thePlayer.getGameProfile().getId());
	}

	public void tick() {
		if (countdown < 0) {
			return;
		}
		if (countdown-- > 0) {
			return;
		}

		String mode = pendingMode;
		countdown = -1;
		pendingMode = null;
		// Somebody who already left for another game by hand does not get pulled somewhere else.
		if (mode != null && roster.isInGame() && mode.equalsIgnoreCase(roster.mode())) {
			queue.queue(mode);
		}
	}

	/** Stops a requeue that is counting down; says whether there was one. */
	public boolean cancel() {
		if (countdown < 0) {
			return false;
		}
		countdown = -1;
		pendingMode = null;
		return true;
	}

	/** Whether a requeue is counting down. */
	public boolean isPending() {
		return countdown >= 0;
	}
}
