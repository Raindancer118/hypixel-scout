package de.raindancer118.hypixelscout.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import de.raindancer118.hypixelscout.core.ChatPacing;
import de.raindancer118.hypixelscout.core.HudMode;
import de.raindancer118.hypixelscout.core.RequeueMode;
import de.raindancer118.hypixelscout.core.SortMode;
import de.raindancer118.hypixelscout.core.Threat;
import de.raindancer118.hypixelscout.core.ThreatFocus;
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

	/** Callout hotkeys: as many as fit on one settings tab. */
	public static final int CALLOUTS = 6;

	public static final int MAX_ROWS = 32;
	public static final double MIN_SCALE = 0.5;
	public static final double MAX_SCALE = 2.0;
	public static final double MIN_ANGLE = 0.5;
	public static final double MAX_ANGLE = 20.0;
	public static final int MAX_CACHE_MINUTES = 120;
	public static final int MAX_LOOKUPS = 12;
	public static final int MAX_RADIUS = 48;
	/** The edge markers look no further than this, in blocks. */
	public static final int MAX_OFFSCREEN_RANGE = 64;
	public static final int MAX_POPUP_SECONDS = 15;
	public static final int MAX_REQUEUE_DELAY = 15;
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

	/** Which danger the levels are about: killing you, taking your bed, or both. */
	public ThreatFocus threatFocus = ThreatFocus.BOTH;

	/**
	 * How dangerous every enemy is taken to be, in percent of their numbers: above 100 the levels
	 * come sooner, below 100 later.
	 */
	public int threatSensitivity = 100;

	/** The lowest level the team and party reports name; below it only the count is sent. */
	public Threat threatReportFrom = Threat.MEDIUM;

	/**
	 * Ticks between two lines of a report sent to chat. Without a rank Hypixel's own three seconds
	 * win anyway ({@link ChatPacing}).
	 */
	public int reportIntervalTicks = 10;

	/** How long a player's stats are reused before being fetched again. */
	public int cacheMinutes = 10;

	public Table table = new Table();
	public Tab tab = new Tab();
	public Nametag nametag = new Nametag();
	public Tooltip tooltip = new Tooltip();
	public Alerts alerts = new Alerts();
	public Proximity proximity = new Proximity();
	public Requeue requeue = new Requeue();
	public Projectiles projectiles = new Projectiles();
	public Awareness awareness = new Awareness();
	public Cards cards = new Cards();
	public Callouts callouts = new Callouts();
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

	/** A short popup with the stats of an enemy who comes close. */
	public static final class Proximity {
		public boolean enabled = true;
		/** How close counts, in blocks. */
		public int radius = 12;
		/** How long a popup stays up. */
		public int seconds = 4;
		/** The lowest threat level worth a popup; nicks and players not looked up yet always get one. */
		public Threat from = Threat.NONE;
	}

	/** Joining the next game once this one is lost. */
	public static final class Requeue {
		public RequeueMode mode = RequeueMode.OFF;
		/** Time to read the last kill line — and to cancel — before {@code /play} goes out. */
		public int delaySeconds = 3;
	}

	/** Arrows and fireballs: where they fly, and a warning when one flies at you. */
	public static final class Projectiles {
		/** The predicted flight path of every arrow and fireball in the air, drawn in the world. */
		public boolean paths = true;
		/** A warning in the HUD while one of them is headed at you. */
		public boolean alarm = true;
		/** A short sound when the warning comes up. */
		public boolean sound = true;
		/**
		 * Target lock: a warning, with a tone of its own, while somebody aims a fire charge at you —
		 * before they throw it. Part of the alarm; off with it.
		 */
		public boolean lock = true;
		/** While holding a fire charge: the line the fireball would fly if thrown now. */
		public boolean aim = true;
		public boolean arrows = true;
		public boolean fireballs = true;
		/** Only in a Bedwars game, so a lobby or another server is left alone. */
		public boolean onlyInGame = true;
		/** Other players' ender pearls: their flight and where they land — where the thrower appears. */
		public boolean pearls = true;
		/** While holding an ender pearl: the arc it would fly if thrown now, and where it lands. */
		public boolean pearlAim = true;
		/** While drawing a bow: the arc the arrow would fly at the current draw. */
		public boolean bowAim = true;
		/** At the end of the fireball aim line: the blast's reach, and who stands in it. */
		public boolean blastPreview = true;
	}

	/** What is about to happen around the player: TNT, the void under them, beds, enemies out of view. */
	public static final class Awareness {
		/** Primed TNT: where it goes off, its reach, and the push the player would get. */
		public boolean tnt = true;
		/** While falling: where the player comes down, or that nothing is under them. */
		public boolean voidWarning = true;
		/** Looking at a bed: the materials on the outside of its defence, the softest first. */
		public boolean bedDefense = true;
		/** Enemies behind or beside the player in plain line of sight, as markers on the screen's edge. */
		public boolean offscreen = true;
		public int offscreenRange = 32;
	}

	/** What each player card shows, and how big it is drawn. */
	public static final class Cards {
		public Card tooltip = Card.of(de.raindancer118.hypixelscout.core.CardLines.Layout.TOOLTIP);
		public Card popup = Card.of(de.raindancer118.hypixelscout.core.CardLines.Layout.POPUP);
		/** The chat hover is vanilla's own tooltip: its contents can be chosen, its size cannot. */
		public Card hover = Card.of(de.raindancer118.hypixelscout.core.CardLines.Layout.TOOLTIP);
		public Profile profile = new Profile();
		/** The peek overlay's size; the profile screen fills the window as it is. */
		public double peekScale = 1.0;
	}

	/** One card: its fields in order, how many to a line, the name's star and rank, the face, the size. */
	public static final class Card {
		public List<de.raindancer118.hypixelscout.core.CardField> fields;
		public int perLine = 3;
		public boolean stars = true;
		public boolean rank = true;
		public boolean head = true;
		public double scale = 1.0;

		public static Card of(de.raindancer118.hypixelscout.core.CardLines.Layout layout) {
			Card card = new Card();
			card.fields = new ArrayList<>(layout.fields());
			card.perLine = layout.perLine();
			card.stars = layout.stars();
			card.rank = layout.rank();
			return card;
		}

		public de.raindancer118.hypixelscout.core.CardLines.Layout layout() {
			return new de.raindancer118.hypixelscout.core.CardLines.Layout(fields, perLine, stars, rank);
		}

		/** Pulls the numbers into range and puts back the defaults where the file had none. */
		void sanitise(de.raindancer118.hypixelscout.core.CardLines.Layout defaults) {
			if (fields == null) {
				Card fresh = of(defaults);
				fields = fresh.fields;
				perLine = fresh.perLine;
			}
			fields = new ArrayList<>(new java.util.LinkedHashSet<>(fields.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList())));
			perLine = Math.max(1, Math.min(6, perLine));
			scale = finiteClamp(scale, MIN_SCALE, MAX_SCALE, 1.0);
		}
	}

	/** Which parts of the profile — on its screen and under the peek key — are shown. */
	public static final class Profile {
		public boolean threat = true;
		public boolean facts = true;
		public boolean lastLogin = true;
		public boolean combat = true;
		public boolean games = true;
		public boolean beds = true;
		public boolean pace = true;
		public boolean socials = true;
	}

	/** Messages on hotkeys, filled in with whoever is aimed at ({@link de.raindancer118.hypixelscout.core.Callout}). */
	public static final class Callouts {
		public String[] messages = {
				"{team} inc", "{team} {name} inc - {stars}* {threat}", "{team} is rushing us",
				"Going {team} bed", "{team} bed is open", "Need help, {team} on me"
		};
		/** Into party chat instead of team chat. */
		public boolean toParty = false;
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
			byte[] bytes = Files.readAllBytes(file);
			loaded = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), ScoutSettings.class);

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
			Files.write(tmp, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
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

	private static double finiteClamp(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}

	/** Fills in whatever a hand-edited file left out and pulls every number back into range. */
	void sanitise() {
		apiKey = apiKey == null ? "" : apiKey.trim();
		accent = accent == null ? Accent.GOLD : accent;
		threatBasis = threatBasis == null ? ThreatScale.Basis.TEAM : threatBasis;
		threatFocus = threatFocus == null ? ThreatFocus.BOTH : threatFocus;
		threatSensitivity = Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, threatSensitivity));
		reportIntervalTicks = Math.max(ChatPacing.MIN_TICKS, Math.min(ChatPacing.MAX_TICKS, reportIntervalTicks));
		threatReportFrom = threatReportFrom == null || !threatReportFrom.isRated() ? Threat.MEDIUM : threatReportFrom;
		cacheMinutes = Math.max(1, Math.min(MAX_CACHE_MINUTES, cacheMinutes));

		table = table == null ? new Table() : table;
		table.mode = table.mode == null ? HudMode.TOGGLE : table.mode;
		table.sort = table.sort == null ? SortMode.STARS : table.sort;
		table.placement = table.placement == null ? TablePlacement.DEFAULT : table.placement;
		table.scale = Double.isFinite(table.scale)
				? Math.max(MIN_SCALE, Math.min(MAX_SCALE, table.scale)) : 1.0;
		table.opacity = Math.max(0, Math.min(100, table.opacity));
		table.maxRows = Math.max(1, Math.min(MAX_ROWS, table.maxRows));

		tab = tab == null ? new Tab() : tab;
		nametag = nametag == null ? new Nametag() : nametag;

		tooltip = tooltip == null ? new Tooltip() : tooltip;
		tooltip.angle = Double.isFinite(tooltip.angle)
				? Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, tooltip.angle)) : 4.0;
		tooltip.offsetY = Math.max(-200, Math.min(200, tooltip.offsetY));

		alerts = alerts == null ? new Alerts() : alerts;
		alerts.streakThreshold = Math.max(1, Math.min(10_000, alerts.streakThreshold));

		proximity = proximity == null ? new Proximity() : proximity;
		proximity.radius = Math.max(2, Math.min(MAX_RADIUS, proximity.radius));
		proximity.seconds = Math.max(1, Math.min(MAX_POPUP_SECONDS, proximity.seconds));
		proximity.from = proximity.from == null || !proximity.from.isRated() ? Threat.NONE : proximity.from;

		requeue = requeue == null ? new Requeue() : requeue;
		requeue.mode = requeue.mode == null ? RequeueMode.OFF : requeue.mode;
		requeue.delaySeconds = Math.max(0, Math.min(MAX_REQUEUE_DELAY, requeue.delaySeconds));

		projectiles = projectiles == null ? new Projectiles() : projectiles;
		awareness = awareness == null ? new Awareness() : awareness;
		awareness.offscreenRange = Math.max(8, Math.min(MAX_OFFSCREEN_RANGE, awareness.offscreenRange));

		cards = cards == null ? new Cards() : cards;
		cards.tooltip = cards.tooltip == null ? Card.of(de.raindancer118.hypixelscout.core.CardLines.Layout.TOOLTIP) : cards.tooltip;
		cards.tooltip.sanitise(de.raindancer118.hypixelscout.core.CardLines.Layout.TOOLTIP);
		cards.popup = cards.popup == null ? Card.of(de.raindancer118.hypixelscout.core.CardLines.Layout.POPUP) : cards.popup;
		cards.popup.sanitise(de.raindancer118.hypixelscout.core.CardLines.Layout.POPUP);
		cards.hover = cards.hover == null ? Card.of(de.raindancer118.hypixelscout.core.CardLines.Layout.TOOLTIP) : cards.hover;
		cards.hover.sanitise(de.raindancer118.hypixelscout.core.CardLines.Layout.TOOLTIP);
		cards.profile = cards.profile == null ? new Profile() : cards.profile;
		cards.peekScale = finiteClamp(cards.peekScale, MIN_SCALE, MAX_SCALE, 1.0);

		callouts = callouts == null ? new Callouts() : callouts;
		String[] messages = callouts.messages == null ? new String[0] : callouts.messages;
		callouts.messages = Arrays.copyOf(messages, CALLOUTS);
		for (int i = 0; i < CALLOUTS; i++) {
			String message = callouts.messages[i];
			callouts.messages[i] = message == null ? "" : message.trim();
		}

		queue = queue == null ? new Queue() : queue;
		String[] slots = queue.slots == null ? new String[0] : queue.slots;
		queue.slots = Arrays.copyOf(slots, QUEUE_SLOTS);
		for (int i = 0; i < QUEUE_SLOTS; i++) {
			queue.slots[i] = queue.slots[i] == null ? "" : queue.slots[i].trim();
		}

		recentLookups = recentLookups == null ? new ArrayList<>() : new ArrayList<>(recentLookups);
		recentLookups.removeIf(name -> name == null || name.trim().isEmpty());
		while (recentLookups.size() > MAX_LOOKUPS) {
			recentLookups.remove(recentLookups.size() - 1);
		}
	}
}
