package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.BedwarsModes;
import de.raindancer118.hypixelscout.core.Eliminations;
import de.raindancer118.hypixelscout.core.RequeueMode;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.ui.Chat;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Joins the next game of the same mode once this one is lost: when the player is out, or when the
 * player and every party member in the game are.
 *
 * <p>Off unless switched on. Who is out comes from the final kill lines; who is in the party from
 * the Mod API's party packet, asked for at the start of every game. Only the party leader can move
 * a party, so a member is told instead of sending a {@code /play} Hypixel would refuse. There is a
 * short delay with a clickable cancel before anything is sent.
 */
public final class AutoRequeue {
	private final Roster roster;
	private final QuickQueue queue;
	private final Supplier<ScoutSettings> settings;
	private final Eliminations eliminations = new Eliminations();

	private volatile Set<UUID> party = Set.of();
	private volatile UUID leader;
	/** Ticks until {@code /play}; -1 while nothing is scheduled. */
	private int countdown = -1;
	private String pendingMode;
	/** Already scheduled or sent in this game, so later kill lines do not queue twice. */
	private boolean done;

	public AutoRequeue(Roster roster, QuickQueue queue, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.queue = queue;
		this.settings = settings;
	}

	public void register() {
		HypixelModAPI.getInstance().createHandler(ClientboundPartyInfoPacket.class, packet -> {
			Set<UUID> members = packet.isInParty() ? Set.copyOf(packet.getMembers()) : Set.of();
			UUID partyLeader = packet.getLeader().orElse(null);
			Minecraft.getInstance().execute(() -> {
				party = members;
				leader = partyLeader;
			});
		});
	}

	/** A new game: everybody is back in, and the party is asked for again. */
	public void gameJoined() {
		reset();
		if (settings.get().requeue.mode != RequeueMode.OFF) {
			HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
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
		Minecraft client = Minecraft.getInstance();
		ScoutSettings.Requeue requeue = settings.get().requeue;
		if (done || client.player == null || roster.mode() == null) {
			return;
		}

		if (!Eliminations.requeueDue(requeue.mode, client.player.getScoreboardName(), partyInGame(client),
				eliminations::isOut)) {
			return;
		}

		done = true;
		if (!party.isEmpty() && leader != null && !leader.equals(client.player.getUUID())) {
			Chat.sayTranslated("message.hypixelscout.requeue.not_leader");
			return;
		}

		pendingMode = roster.mode().toLowerCase(Locale.ROOT);
		countdown = requeue.delaySeconds * 20;
		Chat.say(Component.translatable("message.hypixelscout.requeue.soon",
						BedwarsModes.shortName(pendingMode), requeue.delaySeconds)
				.append(" ")
				.append(Component.translatable("message.hypixelscout.requeue.cancel").withStyle(style -> style
						.withColor(ChatFormatting.RED).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("/scout requeue cancel"))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.translatable("message.hypixelscout.requeue.cancel.tooltip"))))));
	}

	/** The other party members who are playing in this game, by name. */
	private List<String> partyInGame(Minecraft client) {
		List<String> names = new ArrayList<>();
		for (Roster.Member member : roster.members()) {
			if (party.contains(member.uuid()) && !member.uuid().equals(client.player.getUUID())) {
				names.add(member.name());
			}
		}
		return names;
	}

	public void tick(Minecraft client) {
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

	/** Whether a requeue is counting down, for the client game test. */
	public boolean isPending() {
		return countdown >= 0;
	}

	/** Sets the party as the packet would, for the client game test. */
	public void setPartyForTest(Set<UUID> members, UUID partyLeader) {
		party = Set.copyOf(members);
		leader = partyLeader;
	}
}
