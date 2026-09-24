package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Callout;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.Threats;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The callout hotkeys: one press, one message, about whoever is under the crosshair.
 *
 * <p>The team comes from the scoreboard, the numbers from what has been looked up already — nothing
 * is fetched on the spot, since the message has to go out now. One message per press, and never a
 * second copy of one still waiting to be sent.
 *
 * <p>Ported from 26.2's {@code game.Callouts}; the Java 17 switch expression over {@link
 * Callout.Problem} becomes a classic switch statement (Java 8 has no switch expressions), and the
 * target picker is this module's own {@link LookTarget} rather than 26.2's Fabric one.
 */
public final class Callouts {
	private final Roster roster;
	private final StatsService stats;
	private final PartyReport report;
	private final Supplier<ScoutSettings> settings;

	public Callouts(Roster roster, StatsService stats, PartyReport report, Supplier<ScoutSettings> settings) {
		this.roster = roster;
		this.stats = stats;
		this.report = report;
		this.settings = settings;
	}

	/** Sends callout {@code slot} (from 0), or says in the player's own chat why not. */
	public void fire(int slot) {
		ScoutSettings.Callouts callouts = settings.get().callouts;
		String template = callouts.messages[slot];

		Callout.Result result = Callout.render(template, target(template), Threats.scale());
		switch (result.problem()) {
			case EMPTY:
				Chat.sayTranslated("message.hypixelscout.callout.empty", slot + 1);
				break;
			case NO_TARGET:
				Chat.sayTranslated("message.hypixelscout.no_target");
				break;
			case NO_TEAM:
				Chat.sayTranslated("message.hypixelscout.callout.no_team");
				break;
			case NONE:
			default:
				report.sendCallout(callouts.toParty ? PartyReport.Channel.PARTY : PartyReport.Channel.TEAM,
						result.text());
				break;
		}
	}

	private Callout.Target target(String template) {
		Minecraft client = Minecraft.getMinecraft();
		if (!Callout.needsTarget(template) || client.thePlayer == null) {
			return null;
		}

		ScoutSettings.Tooltip tooltip = settings.get().tooltip;
		EntityPlayer target = LookTarget.pick(tooltip.cosine(), tooltip.throughWalls);
		if (target == null) {
			return null;
		}

		String name = target.getName();
		UUID known = roster.uuidOf(name);
		UUID uuid = known == null ? target.getGameProfile().getId() : known;
		// Not known yet: looked up now, so the next callout about them has the numbers.
		stats.request(uuid, name);
		return new Callout.Target(name, Teams.of(name).name(), stats.peek(uuid),
				target.getDistanceToEntity(client.thePlayer));
	}
}
