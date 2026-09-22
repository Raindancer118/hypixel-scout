package de.raindancer118.hypixelscout.game;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;

/**
 * Which Bedwars team a player is on.
 *
 * <p>Bedwars puts every player into a scoreboard team whose colour is the team's colour — the same
 * thing the client tints their nametag with. Reading it back is exactly as reliable as the colours
 * on screen, and needs no chat parsing and no guessing from spawn points.
 */
public final class Teams {
	/** What the scoreboard knows about one player's team. */
	public record Team(String name, int rgb) {
		/** The team as a colour with full alpha, for bars and badges. */
		public int argb() {
			return 0xFF000000 | rgb;
		}
	}

	public static final Team NONE = new Team("", 0x808080);

	private Teams() {
	}

	public static Team of(String playerName) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || playerName == null) {
			return NONE;
		}

		Scoreboard scoreboard = client.level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(playerName);
		return team == null ? NONE : describe(team);
	}

	/** The client's own team, so the enemies can be told from the friends. */
	public static Team own() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? NONE : of(client.player.getScoreboardName());
	}

	public static boolean isOwnTeam(String playerName) {
		Team own = own();
		return own != NONE && own.equals(of(playerName));
	}

	/**
	 * The team's colour, from the team itself or — where a server set only a coloured prefix, as
	 * Hypixel does — from the first coloured character of that prefix.
	 */
	private static Team describe(PlayerTeam team) {
		Optional<TeamColor> colour = team.getColor();
		if (colour.isPresent() && colour.get() != TeamColor.WHITE) {
			return new Team(nameOf(colour.get()), colour.get().rgb());
		}

		TextColor fromPrefix = firstColour(team.getPlayerPrefix());
		if (fromPrefix != null) {
			TeamColor named = nearest(fromPrefix.getValue());
			return new Team(nameOf(named), fromPrefix.getValue());
		}

		return colour.map(c -> new Team(nameOf(c), c.rgb())).orElse(new Team(team.getName(), NONE.rgb()));
	}

	private static TextColor firstColour(Component prefix) {
		if (prefix == null) {
			return null;
		}

		return prefix.visit((style, text) -> text.isBlank() || style.getColor() == null
				? Optional.<TextColor>empty()
				: Optional.of(style.getColor()), net.minecraft.network.chat.Style.EMPTY).orElse(null);
	}

	private static TeamColor nearest(int rgb) {
		for (TeamColor colour : TeamColor.values()) {
			if (colour.rgb() == rgb) {
				return colour;
			}
		}

		return TeamColor.WHITE;
	}

	/** The name Bedwars gives the team, which is not always the colour's own name. */
	private static String nameOf(TeamColor colour) {
		return switch (colour) {
			case RED, DARK_RED -> "Red";
			case BLUE, DARK_BLUE -> "Blue";
			case GREEN, DARK_GREEN -> "Green";
			case YELLOW, GOLD -> "Yellow";
			case AQUA, DARK_AQUA -> "Aqua";
			case LIGHT_PURPLE, DARK_PURPLE -> "Pink";
			case GRAY, DARK_GRAY -> "Gray";
			case WHITE -> "White";
			case BLACK -> "Black";
		};
	}
}
