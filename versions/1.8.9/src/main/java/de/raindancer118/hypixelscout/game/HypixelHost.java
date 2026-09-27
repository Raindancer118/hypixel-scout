package de.raindancer118.hypixelscout.game;

import de.raindancer118.cheatwatch.Enclosure;
import de.raindancer118.cheatwatch.math.Cell;
import de.raindancer118.hypixelscout.HypixelScout;
import de.raindancer118.hypixelscout.core.BedDefense;
import de.raindancer118.scout.api.ReportAction;
import de.raindancer118.scout.api.ScoutHost;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What Hypixel Scout tells Scout about a Bedwars game: when it begins (the Mod API's location packet,
 * not a new world — Hypixel moves the player through several worlds on the way in), who is in it, the
 * teams, what a bed's defence is, and the reports it can send to the party or the team.
 *
 * <p>Registered with {@code ScoutApi.register} before Scout starts (1.8.9 has no entrypoints). Every
 * answer is read live from the mod, so it is the same instance before and after {@link HypixelScout}
 * is up. Ported from 26.2's {@code game.HypixelHost}.
 */
public final class HypixelHost implements ScoutHost {
	/**
	 * NUKER's question — was the bed still wrapped in its defence — answered by the same {@link
	 * BedDefense} the bed ledger shows, from nothing but the terrain CheatWatch hands over, so a
	 * recording of that terrain still replays the answer.
	 */
	static final Enclosure DEFENCE = (bed, terrain) -> {
		List<BedDefense.Cell> cells = new ArrayList<BedDefense.Cell>(bed.size());
		for (Cell cell : bed) {
			cells.add(new BedDefense.Cell(cell.x(), cell.y(), cell.z()));
		}
		final BedDefense.Block solid = new BedDefense.Block("solid", 1);
		return !BedDefense.analyse(cells, at -> terrain.solid(at.x(), at.y(), at.z()) ? solid : null).open();
	};

	private static HypixelScout mod() {
		return HypixelScout.get();
	}

	@Override
	public String name() {
		return "Hypixel Scout";
	}

	@Override
	public boolean drivesRounds() {
		return true;
	}

	@Override
	public boolean watching() {
		HypixelScout mod = mod();
		return mod != null && mod.roster().isInGame();
	}

	@Override
	public boolean watches(String player, boolean scout) {
		HypixelScout mod = mod();
		if (mod == null) {
			return scout;
		}
		Minecraft client = Minecraft.getMinecraft();
		boolean self = client.thePlayer != null && client.thePlayer.getName().equals(player);
		return self || mod.roster().contains(player);
	}

	@Override
	public boolean teammates(String a, String b, boolean scout) {
		// Teammates cannot hurt each other in Bedwars: a swing beside a push on one is chance.
		Teams.Team team = Teams.of(a);
		return team != Teams.NONE && team.equals(Teams.of(b));
	}

	@Override
	public int colour(String player, int scout) {
		Teams.Team team = Teams.of(player);
		return team == Teams.NONE ? scout : team.rgb();
	}

	@Override
	public String team(String player) {
		Teams.Team team = Teams.of(player);
		return team == Teams.NONE ? null : team.name();
	}

	@Override
	public Enclosure enclosure() {
		return DEFENCE;
	}

	@Override
	public String mode() {
		HypixelScout mod = mod();
		return mod == null ? null : mod.roster().mode();
	}

	@Override
	public String map() {
		HypixelScout mod = mod();
		return mod == null ? null : mod.roster().map();
	}

	@Override
	public List<ReportAction> actions() {
		return Arrays.asList(
				new ReportAction("message.hypixelscout.cheat.to_party", "message.hypixelscout.cheat.to_party.hover",
						"/scout cheats party", false, () -> canSend(PartyReport.Channel.PARTY)),
				new ReportAction("message.hypixelscout.cheat.to_team", "message.hypixelscout.cheat.to_team.hover",
						"/scout cheats team", false, () -> canSend(PartyReport.Channel.TEAM)),
				new ReportAction("message.hypixelscout.suspects.profile", "message.hypixelscout.suspects.profile.hover",
						"/scout {player}", true, () -> true));
	}

	private static boolean canSend(PartyReport.Channel channel) {
		HypixelScout mod = mod();
		return mod != null && mod.partyReport().canSendPlayer(channel);
	}

	@Override
	public boolean ownsRootCommand() {
		return true;
	}
}
