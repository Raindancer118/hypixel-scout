package de.raindancer118.hypixelscout.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;

/**
 * Which Bedwars team a player is on.
 *
 * <p>Bedwars puts every player into a scoreboard team whose colour is the team's colour, which is
 * also what the client uses to tint their nametag. Reading it back out is therefore exactly as
 * reliable as the colours on screen, and needs no chat parsing and no guessing from spawn points.
 */
public final class Teams {
	private Teams() {
	}

	/** The colour name of a player's team — RED, BLUE, … — or {@code null} if they have none. */
	public static String teamOf(String playerName) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			return null;
		}

		Scoreboard scoreboard = mc.theWorld.getScoreboard();
		if (scoreboard == null) {
			return null;
		}

		ScorePlayerTeam team = scoreboard.getPlayersTeam(playerName);
		return team == null ? null : label(team);
	}

	/** The team the client's own player is on, so the enemies can be told from the friends. */
	public static String ownTeam() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null) {
			return null;
		}

		Team team = mc.thePlayer.getTeam();
		return team instanceof ScorePlayerTeam ? label((ScorePlayerTeam) team) : null;
	}

	public static boolean isOwnTeam(String playerName) {
		String own = ownTeam();
		return own != null && own.equals(teamOf(playerName));
	}

	/** The team's colour as an ARGB value, for the marker down the side of a row. */
	public static int colourOf(String team) {
		if (team == null) {
			return 0xFF808080;
		}

		if ("RED".equals(team)) {
			return 0xFFFF5555;
		}
		if ("BLUE".equals(team)) {
			return 0xFF5555FF;
		}
		if ("GREEN".equals(team)) {
			return 0xFF55FF55;
		}
		if ("YELLOW".equals(team)) {
			return 0xFFFFFF55;
		}
		if ("AQUA".equals(team)) {
			return 0xFF55FFFF;
		}
		if ("PINK".equals(team)) {
			return 0xFFFF55FF;
		}
		if ("GRAY".equals(team)) {
			return 0xFFAAAAAA;
		}
		if ("WHITE".equals(team)) {
			return 0xFFFFFFFF;
		}

		return 0xFF808080;
	}

	/**
	 * The colour code in the team's prefix names the team. The registered name is a Bedwars
	 * internal such as {@code §c§lR}, which is no use in a chat message.
	 */
	private static String label(ScorePlayerTeam team) {
		String prefix = team.getColorPrefix();
		if (prefix == null) {
			return team.getRegisteredName();
		}

		int marker = prefix.indexOf('§');
		if (marker < 0 || marker + 1 >= prefix.length()) {
			return team.getRegisteredName();
		}

		switch (prefix.charAt(marker + 1)) {
			case 'c':
				return "RED";
			case '9':
				return "BLUE";
			case 'a':
				return "GREEN";
			case 'e':
				return "YELLOW";
			case 'b':
				return "AQUA";
			case 'd':
				return "PINK";
			case '7':
				return "GRAY";
			case 'f':
				return "WHITE";
			default:
				return team.getRegisteredName();
		}
	}
}
