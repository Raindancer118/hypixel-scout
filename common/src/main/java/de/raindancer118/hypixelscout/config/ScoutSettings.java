package de.raindancer118.hypixelscout.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatScale;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every setting the mod has, as {@code config/hypixelscout.json}.
 *
 * <p>Plain public fields in nested sections, so the file reads the way the settings screen is laid
 * out and stays editable by hand. Read at the point of use, never copied: a change in the screen
 * takes effect on the next frame.
 *
 * <p>A file that cannot be parsed is left exactly as it is and the defaults are used — overwriting
 * it would throw away whatever the player was halfway through editing. Every value is clamped on
 * load, so a typo can make a setting fall back but never make the mod misbehave.
 *
 * <p>The API key is stored in plain text. It only reads public statistics and can be revoked at
 * developer.hypixel.net at any time — but this file is still not one to show on stream.
 */
public final class ScoutSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Nine quick-queue slots, because nine is as many hotkeys as anybody remembers. */
	public static final int QUEUE_SLOTS = 9;

	public static final int MAX_ROWS = 32;
	public static final double MIN_SCALE = 0.5;
	public static final double MAX_SCALE = 2.0;
	public static final double MIN_ANGLE = 0.5;
	public static final double MAX_ANGLE = 20.0;
	public static final int MAX_CACHE_MINUTES = 120;
	public static final int MAX_LOOKUPS = 12;
	public static final int MIN_SENSITIVITY = 25;
	public static final int MAX_SENSITIVITY = 400;

	/** Personal key from developer.hypixel.net. Empty until the player sets one. */
	public String apiKey = "";

	/** The colour every surface of the mod is tinted with. */
	public Accent accent = Accent.GOLD;

	/**
	 * Whether players are looked up already in the waiting lobby before a match. Off, because the
	 * lobby fills and empties for minutes and every player who leaves before the start is a
	 * request spent on nothing.
	 */
	public boolean lookUpInLobby = false;

	/** What the threat levels are measured against: fixed bands, the player, or player and team. */
	public ThreatScale.Basis threatBasis = ThreatScale.Basis.TEAM;

	/**
	 * How dangerous every enemy is taken to be, in percent of their numbers: above 100 the levels
	 * come sooner, below 100 later.
	 */
	public int threatSensitivity = 100;

	/** The lowest level the team and party reports name; below it only the count is sent. */
	public Threat threatReportFrom = Threat.MEDIUM;

	/** How long a player's stats are reused before being fetched again. */
	public int cacheMinutes = 10;

	public Table table = new Table();
	public Tab tab = new Tab();
	public Nametag nametag = new Nametag();
	public Tooltip tooltip = new Tooltip();
	public Alerts alerts = new Alerts();
	public Queue queue = new Queue();

	/** Names typed into the lookup screen lately, newest first. */
	public List<String> recentLookups = new ArrayList<>();

	/** Where this was read from, and where {@link #save()} writes it back. */
	private transient Path file;

	/** Why the file could not be read, for the log; {@code null} when it was fine. */
	private transient String problem;

	/** The in-game table. */
	public static final class Table {
		public HudMode mode = HudMode.TOGGLE;
		public SortMode sort = SortMode.STARS;
		public TablePlacement placement = TablePlacement.DEFAULT;
		public double scale = 1.0;
		/** Backdrop opacity in percent. */
		public int opacity = 82;
		public int maxRows = 16;
		public boolean groupByTeam = true;
		public boolean hideOwnTeam = false;
		public boolean showWlr = true;
		public boolean showWinstreak = true;
		public boolean showBeds = false;
		/** Beds broken per game: whether this player rushes. */
		public boolean showBedsPerGame = true;
		/** Kills per game: whether this player fights. */
		public boolean showKillsPerGame = true;
		public boolean showAccountAge = false;
	}

	/** Stats in the tab list, in place of vanilla's. */
	public static final class Tab {
		public boolean enabled = false;
	}

	/** The star in front of each nametag. */
	public static final class Nametag {
		public boolean stars = false;
		public boolean fkdr = true;
	}

	/** The stats of whoever is under the crosshair. */
	public static final class Tooltip {
		public boolean enabled = true;
		/** How close to the centre of the screen a player has to be, in degrees. */
		public double angle = 4.0;
		/** Off on purpose: the tooltip is for players you can actually see. */
		public boolean throughWalls = false;
		/** How far below the crosshair the card is drawn. */
		public int offsetY = 14;

		/** Cosine of the half-angle, which is what the picker compares against. */
		public double cosine() {
			return Math.cos(Math.toRadians(angle));
		}
	}

	/** What arrives in chat by itself. */
	public static final class Alerts {
		public boolean chatHover = true;
		public boolean nickAlert = true;
		public boolean streakAlert = true;
		public int streakThreshold = 50;
	}

	/** The quick-queue hotkeys. */
	public static final class Queue {
		/** Hypixel's own mode ids, as {@code /play} takes them. Empty means unused. */
		public String[] slots = {
				"bedwars_eight_one", "bedwars_eight_two", "bedwars_four_three",
				"bedwars_four_four", "bedwars_two_four", "", "", "", ""
		};
		/** So a stray key press cannot fire {@code /play} at some other server. */
		public boolean onlyOnHypixel = true;
	}

	/**
	 * Reads the file, or writes the defaults out if there is none yet.
	 *
	 * <p>Never throws: the worst case is the defaults, with {@link #problem()} saying why.
	 */
	public static ScoutSettings load(Path file) {
		if (!Files.exists(file)) {
			ScoutSettings fresh = new ScoutSettings();
			fresh.file = file;
			fresh.save();
			return fresh;
		}

		ScoutSettings loaded;
		String problem = null;

		try {
			loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ScoutSettings.class);

			if (loaded == null) {
				loaded = new ScoutSettings();
				problem = "the file is empty";
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			loaded = new ScoutSettings();
			problem = e.getMessage();
		}

		loaded.file = file;
		loaded.problem = problem;
		loaded.sanitise();
		return loaded;
	}

	/** Writes to a temporary file and moves it over, so a crash cannot leave half a config. */
	public void save() {
		if (file == null) {
			return;
		}

		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			problem = "could not write " + file + ": " + e.getMessage();

			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
				// Nothing more to be done; the problem above is what gets reported.
			}
		}
	}

	public String problem() {
		return problem;
	}

	public Path file() {
		return file;
	}

	/** Fills in whatever a hand-edited file left out and pulls every number back into range. */
	void sanitise() {
		apiKey = apiKey == null ? "" : apiKey.trim();
		accent = accent == null ? Accent.GOLD : accent;
		threatBasis = threatBasis == null ? ThreatScale.Basis.TEAM : threatBasis;
		threatSensitivity = Math.clamp(threatSensitivity, MIN_SENSITIVITY, MAX_SENSITIVITY);
		threatReportFrom = threatReportFrom == null || !threatReportFrom.isRated() ? Threat.MEDIUM : threatReportFrom;
		cacheMinutes = Math.clamp(cacheMinutes, 1, MAX_CACHE_MINUTES);

		table = table == null ? new Table() : table;
		table.mode = table.mode == null ? HudMode.TOGGLE : table.mode;
		table.sort = table.sort == null ? SortMode.STARS : table.sort;
		table.placement = table.placement == null ? TablePlacement.DEFAULT : table.placement;
		table.scale = Double.isFinite(table.scale)
				? Math.clamp(table.scale, MIN_SCALE, MAX_SCALE) : 1.0;
		table.opacity = Math.clamp(table.opacity, 0, 100);
		table.maxRows = Math.clamp(table.maxRows, 1, MAX_ROWS);

		tab = tab == null ? new Tab() : tab;
		nametag = nametag == null ? new Nametag() : nametag;

		tooltip = tooltip == null ? new Tooltip() : tooltip;
		tooltip.angle = Double.isFinite(tooltip.angle)
				? Math.clamp(tooltip.angle, MIN_ANGLE, MAX_ANGLE) : 4.0;
		tooltip.offsetY = Math.clamp(tooltip.offsetY, -200, 200);

		alerts = alerts == null ? new Alerts() : alerts;
		alerts.streakThreshold = Math.clamp(alerts.streakThreshold, 1, 10_000);

		queue = queue == null ? new Queue() : queue;
		String[] slots = queue.slots == null ? new String[0] : queue.slots;
		queue.slots = Arrays.copyOf(slots, QUEUE_SLOTS);
		for (int i = 0; i < QUEUE_SLOTS; i++) {
			queue.slots[i] = queue.slots[i] == null ? "" : queue.slots[i].trim();
		}

		recentLookups = recentLookups == null ? new ArrayList<>() : new ArrayList<>(recentLookups);
		recentLookups.removeIf(name -> name == null || name.isBlank());
		while (recentLookups.size() > MAX_LOOKUPS) {
			recentLookups.removeLast();
		}
	}
}
