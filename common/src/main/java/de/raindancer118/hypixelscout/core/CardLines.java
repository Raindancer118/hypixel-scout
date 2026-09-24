package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A player card's lines as the player set it up: the name — with or without star and rank — then
 * the chosen {@link CardField}s in the chosen order, so many to a line. Somebody not looked up yet,
 * or nicked, gets the same two lines whatever the fields, since there is nothing else to say.
 */
public final class CardLines {
	/**
	 * @param fields  what to show, in order
	 * @param perLine how many fields share a line
	 */
	public record Layout(List<CardField> fields, int perLine, boolean stars, boolean rank) {
		public Layout {
			fields = List.copyOf(fields);
			perLine = Math.max(1, perLine);
		}

		/** The look tooltip and the chat hover: everything worth knowing about somebody in front of you. */
		public static final Layout TOOLTIP = new Layout(List.of(CardField.FKDR, CardField.WLR, CardField.RATINGS,
				CardField.FINALS, CardField.WINS, CardField.WINSTREAK, CardField.BEDS, CardField.BBLR,
				CardField.ACCOUNT_AGE, CardField.BEDS_PER_GAME, CardField.KILLS_PER_GAME, CardField.SOCIALS,
				CardField.CHEATS), 3, true, true);

		/** The proximity popup: read in a second. */
		public static final Layout POPUP = new Layout(List.of(CardField.THREAT, CardField.HEADLINE_RATIO,
				CardField.WINSTREAK, CardField.CHEATS), 4, true, true);
	}

	private CardLines() {
	}

	/**
	 * @param extra what the caller fills in for fields only it knows ({@link CardField#CHEATS}); a
	 *              field missing here and with nothing of its own is left out
	 */
	public static List<String> lines(String name, PlayerStats stats, boolean pending, String failure, ThreatScale scale,
			Layout layout, Map<CardField, String> extra) {
		if (stats == null) {
			return List.of("§f" + name, pending || failure == null ? "§7Looking them up…" : "§c" + failure);
		}
		if (stats.isNicked()) {
			return List.of("§f" + name, "§dNICK");
		}

		List<String> lines = new ArrayList<>();
		lines.add(nameLine(name, stats, layout));

		long now = System.currentTimeMillis();
		StringBuilder line = new StringBuilder();
		int onLine = 0;
		for (CardField field : layout.fields()) {
			String text = extra.containsKey(field) ? extra.get(field) : field.render(stats, scale, now);
			if (text == null || text.isBlank()) {
				continue;
			}
			if (onLine == layout.perLine()) {
				lines.add(line.toString());
				line.setLength(0);
				onLine = 0;
			}
			line.append(onLine == 0 ? "" : "  ").append(text);
			onLine++;
		}
		if (onLine > 0) {
			lines.add(line.toString());
		}
		return lines;
	}

	private static String nameLine(String name, PlayerStats stats, Layout layout) {
		String tag = Ranks.tag(stats.getRank());
		return (layout.stars() ? StatFormat.star(stats.getStars()) + " " : "")
				+ (layout.rank() && !tag.isEmpty() ? tag + " " : "")
				+ Ranks.colour(stats.getRank()) + name;
	}
}
