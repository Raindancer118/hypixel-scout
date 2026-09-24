package de.raindancer118.hypixelscout.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
	public static final class Layout {
		/** The look tooltip and the chat hover: everything worth knowing about somebody in front of you. */
		public static final Layout TOOLTIP = new Layout(Arrays.asList(CardField.FKDR, CardField.WLR, CardField.RATINGS,
				CardField.FINALS, CardField.WINS, CardField.WINSTREAK, CardField.BEDS, CardField.BBLR,
				CardField.ACCOUNT_AGE, CardField.BEDS_PER_GAME, CardField.KILLS_PER_GAME, CardField.SOCIALS,
				CardField.CHEATS), 3, true, true);

		/** The proximity popup: read in a second. */
		public static final Layout POPUP = new Layout(Arrays.asList(CardField.THREAT, CardField.HEADLINE_RATIO,
				CardField.WINSTREAK, CardField.CHEATS), 4, true, true);

		private final List<CardField> fields;
		private final int perLine;
		private final boolean stars;
		private final boolean rank;

		public Layout(List<CardField> fields, int perLine, boolean stars, boolean rank) {
			List<CardField> copy = new ArrayList<>(fields.size());
			for (CardField field : fields) {
				copy.add(Objects.requireNonNull(field));
			}
			this.fields = Collections.unmodifiableList(copy);
			this.perLine = Math.max(1, perLine);
			this.stars = stars;
			this.rank = rank;
		}

		public List<CardField> fields() {
			return fields;
		}

		public int perLine() {
			return perLine;
		}

		public boolean stars() {
			return stars;
		}

		public boolean rank() {
			return rank;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof Layout)) return false;
			Layout other = (Layout) obj;
			return perLine == other.perLine && stars == other.stars && rank == other.rank
					&& Objects.equals(fields, other.fields);
		}

		@Override
		public int hashCode() {
			return Objects.hash(fields, perLine, stars, rank);
		}

		@Override
		public String toString() {
			return "Layout[fields=" + fields + ", perLine=" + perLine + ", stars=" + stars + ", rank=" + rank + "]";
		}
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
			return Collections.unmodifiableList(Arrays.asList("§f" + name,
					pending || failure == null ? "§7Looking them up…" : "§c" + failure));
		}
		if (stats.isNicked()) {
			return Collections.unmodifiableList(Arrays.asList("§f" + name, "§dNICK"));
		}

		List<String> lines = new ArrayList<>();
		lines.add(nameLine(name, stats, layout));

		long now = System.currentTimeMillis();
		StringBuilder line = new StringBuilder();
		int onLine = 0;
		for (CardField field : layout.fields()) {
			String text = extra.containsKey(field) ? extra.get(field) : field.render(stats, scale, now);
			if (text == null || text.trim().isEmpty()) {
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
