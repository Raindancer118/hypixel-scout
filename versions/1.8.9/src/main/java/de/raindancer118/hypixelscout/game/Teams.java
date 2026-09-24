package de.raindancer118.hypixelscout.game;

import de.raindancer118.hypixelscout.core.Allegiance;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/**
 * Which Bedwars team a player is on.
 *
 * <p>Bedwars puts every player into a scoreboard team whose colour is the team's colour — the same
 * thing the client tints their nametag with. Reading it back is exactly as reliable as the colours
 * on screen, and needs no chat parsing and no guessing from spawn points.
 */
public final class Teams {
	/** What the scoreboard knows about one player's team. */
	public static final class Team {
		private final String name;
		private final int rgb;

		public Team(String name, int rgb) {
			this.name = name;
			this.rgb = rgb;
		}

		public String name() {
			return name;
		}

		public int rgb() {
			return rgb;
		}

		/** The team as a colour with full alpha, for bars and badges. */
		public int argb() {
			return 0xFF000000 | rgb;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Team)) {
				return false;
			}
			Team other = (Team) obj;
			return rgb == other.rgb && name.equals(other.name);
		}

		@Override
		public int hashCode() {
			return name.hashCode() * 31 + rgb;
		}

		@Override
		public String toString() {
			return "Team[name=" + name + ", rgb=" + Integer.toHexString(rgb) + "]";
		}
	}

	public static final Team NONE = new Team("", 0x808080);

	private Teams() {
	}

	public static Team of(String playerName) {
		Minecraft client = Minecraft.getMinecraft();
		if (client.theWorld == null || playerName == null) {
			return NONE;
		}

		Scoreboard scoreboard = client.theWorld.getScoreboard();
		ScorePlayerTeam team = scoreboard.getPlayersTeam(playerName);
		return team == null ? NONE : describe(team);
	}

	/** The client's own team, so the enemies can be told from the friends. */
	public static Team own() {
		Minecraft client = Minecraft.getMinecraft();
		return client.thePlayer == null ? NONE : of(client.thePlayer.getName());
	}

	public static boolean isOwnTeam(String playerName) {
		Team own = own();
		return own != NONE && own.equals(of(playerName));
	}

	/**
	 * Whether this player is on another team than the client's, by team name. Only a definite yes:
	 * with either team unknown the answer is no.
	 */
	public static boolean isEnemy(String playerName) {
		return Allegiance.of(own().name(), of(playerName).name()) == Allegiance.ENEMY;
	}

	/**
	 * The team's colour, from the team's own chat colour or — where a server set only a coloured
	 * prefix, as Hypixel does — from the first coloured character of that prefix.
	 */
	private static Team describe(ScorePlayerTeam team) {
		Team byFormat = fromFormatting(team.getChatFormat());
		if (byFormat != null) {
			return byFormat;
		}

		Team byPrefix = fromPrefix(team.getColorPrefix());
		if (byPrefix != null) {
			return byPrefix;
		}

		return new Team(team.getRegisteredName(), NONE.rgb);
	}

	/** The first legacy colour code ({@code §c}, …) found in a prefix such as Hypixel's {@code §c§lR}. */
	private static Team fromPrefix(String prefix) {
		if (prefix == null) {
			return null;
		}

		for (int i = 0; i < prefix.length() - 1; i++) {
			if (prefix.charAt(i) == '§') {
				Team byChar = fromChar(Character.toLowerCase(prefix.charAt(i + 1)));
				if (byChar != null) {
					return byChar;
				}
			}
		}

		return null;
	}

	/** The name Bedwars gives the team, which is not always the colour's own name — from the enum. */
	private static Team fromFormatting(EnumChatFormatting colour) {
		if (colour == null || !colour.isColor()) {
			return null;
		}

		switch (colour) {
			case RED:
			case DARK_RED:
				return new Team("Red", rgbOf(colour));
			case BLUE:
			case DARK_BLUE:
				return new Team("Blue", rgbOf(colour));
			case GREEN:
			case DARK_GREEN:
				return new Team("Green", rgbOf(colour));
			case YELLOW:
			case GOLD:
				return new Team("Yellow", rgbOf(colour));
			case AQUA:
			case DARK_AQUA:
				return new Team("Aqua", rgbOf(colour));
			case LIGHT_PURPLE:
			case DARK_PURPLE:
				return new Team("Pink", rgbOf(colour));
			case GRAY:
			case DARK_GRAY:
				return new Team("Gray", rgbOf(colour));
			case WHITE:
				return new Team("White", rgbOf(colour));
			case BLACK:
				return new Team("Black", rgbOf(colour));
			default:
				return null;
		}
	}

	/** The same mapping, keyed by the legacy formatting character rather than the enum constant. */
	private static Team fromChar(char code) {
		switch (code) {
			case 'c':
				return new Team("Red", 0xFF5555);
			case '4':
				return new Team("Red", 0xAA0000);
			case '9':
				return new Team("Blue", 0x5555FF);
			case '1':
				return new Team("Blue", 0x0000AA);
			case 'a':
				return new Team("Green", 0x55FF55);
			case '2':
				return new Team("Green", 0x00AA00);
			case 'e':
				return new Team("Yellow", 0xFFFF55);
			case '6':
				return new Team("Yellow", 0xFFAA00);
			case 'b':
				return new Team("Aqua", 0x55FFFF);
			case '3':
				return new Team("Aqua", 0x00AAAA);
			case 'd':
				return new Team("Pink", 0xFF55FF);
			case '5':
				return new Team("Pink", 0xAA00AA);
			case '7':
				return new Team("Gray", 0xAAAAAA);
			case '8':
				return new Team("Gray", 0x555555);
			case 'f':
				return new Team("White", 0xFFFFFF);
			case '0':
				return new Team("Black", 0x000000);
			default:
				return null;
		}
	}

	/** Standard Minecraft chat colours, the same values every legacy-formatted line renders with. */
	private static int rgbOf(EnumChatFormatting colour) {
		switch (colour) {
			case BLACK:
				return 0x000000;
			case DARK_BLUE:
				return 0x0000AA;
			case DARK_GREEN:
				return 0x00AA00;
			case DARK_AQUA:
				return 0x00AAAA;
			case DARK_RED:
				return 0xAA0000;
			case DARK_PURPLE:
				return 0xAA00AA;
			case GOLD:
				return 0xFFAA00;
			case GRAY:
				return 0xAAAAAA;
			case DARK_GRAY:
				return 0x555555;
			case BLUE:
				return 0x5555FF;
			case GREEN:
				return 0x55FF55;
			case AQUA:
				return 0x55FFFF;
			case RED:
				return 0xFF5555;
			case LIGHT_PURPLE:
				return 0xFF55FF;
			case YELLOW:
				return 0xFFFF55;
			case WHITE:
			default:
				return 0xFFFFFF;
		}
	}
}
