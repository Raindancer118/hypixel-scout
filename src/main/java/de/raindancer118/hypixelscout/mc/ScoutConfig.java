package de.raindancer118.hypixelscout.mc;

import de.raindancer118.hypixelscout.core.SortMode;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Every setting the mod has, in {@code config/hypixelscout.cfg} and in the settings screen under
 * Mods.
 *
 * <p>Nothing here is hard-coded elsewhere: what is shown, where it is shown, how sharply the look
 * tooltip picks, what counts as a winstreak worth warning about, and how long an answer stays
 * cached are all read from here at the point of use, so changing one takes effect on the next
 * frame without a restart.
 *
 * <p>The API key lives in this file in plain text, the way every 1.8.9 mod of this kind stores it.
 * It only reads public statistics and can be reset at developer.hypixel.net at any time — but it is
 * still worth not putting this file on stream.
 */
public final class ScoutConfig {
	public static final String CATEGORY_GENERAL = "general";
	public static final String CATEGORY_TABLE = "table";
	public static final String CATEGORY_TOOLTIP = "tooltip";
	public static final String CATEGORY_TAB = "tab";
	public static final String CATEGORY_NAMETAG = "nametag";
	public static final String CATEGORY_ALERTS = "alerts";
	public static final String CATEGORY_QUEUE = "quickqueue";
	public static final String CATEGORY_ADVANCED = "advanced";

	/** Nine slots, because nine is as many hotkeys as anybody will remember. */
	public static final int QUEUE_SLOTS = 9;

	private final Configuration configuration;

	private String apiKey;

	private boolean tableEnabled;
	private int tableX;
	private int tableY;
	private SortMode tableSort;
	private boolean showAccountAge;
	private boolean showWinstreak;
	private boolean showWlr;
	private boolean hideOwnTeam;
	private int tableMaxRows;
	private boolean tableBackground;

	private boolean tabStatsEnabled;

	private boolean nametagStars;
	private boolean nametagFkdr;

	private boolean lookTooltipEnabled;
	private double lookAngleDegrees;
	private boolean lookThroughWalls;
	private int lookOffsetY;

	private boolean chatHoverEnabled;
	private boolean nickAlert;
	private boolean streakAlert;
	private int streakThreshold;

	private final String[] queueModes = new String[QUEUE_SLOTS];
	private boolean queueOnlyOnHypixel;

	private int cacheMinutes;
	private boolean debugLogging;

	public ScoutConfig(File file) {
		this.configuration = new Configuration(file);
		load();
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	/** Reads every value. Called at start-up and again whenever the settings screen is closed. */
	public void load() {
		configuration.load();

		apiKey = configuration.getString("apiKey", CATEGORY_GENERAL, "",
				"Your personal Hypixel API key from developer.hypixel.net. Easier to set in game "
						+ "with /scout key <key> than to paste in here.");

		tableEnabled = configuration.getBoolean("enabled", CATEGORY_TABLE, true,
				"Show the table of everybody in the game.");
		tableX = configuration.getInt("x", CATEGORY_TABLE, 4, 0, 4000,
				"Distance of the table from the left edge, in scaled pixels.");
		tableY = configuration.getInt("y", CATEGORY_TABLE, 4, 0, 4000,
				"Distance of the table from the top edge, in scaled pixels.");
		tableSort = SortMode.parse(configuration.getString("sort", CATEGORY_TABLE,
				SortMode.STARS.name(),
				"What the table is sorted by, highest first.",
				new String[] {"STARS", "FKDR", "WLR", "NAME"}));
		showAccountAge = configuration.getBoolean("accountAge", CATEGORY_TABLE, true,
				"Show how old each account is. A young account with high stats is usually an alt.");
		showWinstreak = configuration.getBoolean("winstreak", CATEGORY_TABLE, true,
				"Show each player's winstreak. Many players hide it, and then it reads as ?.");
		showWlr = configuration.getBoolean("wlr", CATEGORY_TABLE, true,
				"Show the win/loss ratio next to the FKDR.");
		hideOwnTeam = configuration.getBoolean("hideOwnTeam", CATEGORY_TABLE, false,
				"Leave your own team out of the table and only list the enemies.");
		tableMaxRows = configuration.getInt("maxRows", CATEGORY_TABLE, 16, 1, 100,
				"At most this many players are listed. A Bedwars game holds sixteen; a larger "
						+ "number only matters if you point this at something else.");
		tableBackground = configuration.getBoolean("background", CATEGORY_TABLE, true,
				"Draw a dark box behind the table so it stays readable on a bright map.");

		// Off by default, and so is the nametag below it: both replace something vanilla draws
		// rather than adding to it, and a mod should not change how the game looks uninvited.
		tabStatsEnabled = configuration.getBoolean("enabled", CATEGORY_TAB, false,
				"Replace the tab list with one that carries stats, heads and team colours.");

		nametagStars = configuration.getBoolean("stars", CATEGORY_NAMETAG, false,
				"Put each player's Bedwars star in front of their nametag, in place of the "
						+ "vanilla one.");
		nametagFkdr = configuration.getBoolean("fkdr", CATEGORY_NAMETAG, true,
				"Put their FKDR after it as well. Only does anything with the star switched on.");

		lookTooltipEnabled = configuration.getBoolean("enabled", CATEGORY_TOOLTIP, true,
				"Show a player's stats while you are looking at them.");
		lookAngleDegrees = configuration.getFloat("angle", CATEGORY_TOOLTIP, 4.0f, 0.5f, 20.0f,
				"How close to the centre of the screen a player has to be, in degrees. Smaller "
						+ "means you have to aim more precisely.");
		lookThroughWalls = configuration.getBoolean("throughWalls", CATEGORY_TOOLTIP, false,
				"Off by default on purpose: the tooltip only appears for players you can actually "
						+ "see.");
		lookOffsetY = configuration.getInt("offsetY", CATEGORY_TOOLTIP, 14, -200, 200,
				"How far below the crosshair the tooltip is drawn.");

		chatHoverEnabled = configuration.getBoolean("chatHover", CATEGORY_ALERTS, true,
				"Attach stats to player names in chat, shown when you hover over them.");
		nickAlert = configuration.getBoolean("nickAlert", CATEGORY_ALERTS, true,
				"Say in chat when somebody in the game turns out to be nicked.");
		streakAlert = configuration.getBoolean("streakAlert", CATEGORY_ALERTS, true,
				"Say in chat when somebody in the game is on a high winstreak.");
		streakThreshold = configuration.getInt("streakThreshold", CATEGORY_ALERTS, 50, 1, 10000,
				"What counts as a high winstreak, for both the chat warning and /scout party.");

		// Hypixel's own mode ids, the ones /play takes. An empty slot is simply unused, and its
		// hotkey then does nothing rather than sending a broken command.
		String[] defaults = {
				"bedwars_four_four", "bedwars_eight_two", "bedwars_eight_one",
				"bedwars_four_three", "bedwars_two_four", "", "", "", ""
		};
		for (int slot = 0; slot < QUEUE_SLOTS; slot++) {
			queueModes[slot] = configuration.getString("slot" + (slot + 1), CATEGORY_QUEUE,
					defaults[slot],
					"The mode hotkey " + (slot + 1) + " queues for, as /play takes it — for "
							+ "example bedwars_four_four. Leave empty to disable the key.");
		}
		queueOnlyOnHypixel = configuration.getBoolean("onlyOnHypixel", CATEGORY_QUEUE, true,
				"Ignore the queue hotkeys while you are not connected to Hypixel, so they cannot "
						+ "fire a stray command on another server.");

		cacheMinutes = configuration.getInt("cacheMinutes", CATEGORY_ADVANCED, 10, 1, 120,
				"How long a player's stats are reused before being fetched again.");
		debugLogging = configuration.getBoolean("debugLogging", CATEGORY_ADVANCED, false,
				"Write what the mod is doing to the game log.");

		if (configuration.hasChanged()) {
			configuration.save();
		}
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String value) {
		this.apiKey = value == null ? "" : value.trim();
		configuration.get(CATEGORY_GENERAL, "apiKey", "").set(this.apiKey);
		configuration.save();
	}

	public boolean isTableEnabled() {
		return tableEnabled;
	}

	public void setTableEnabled(boolean value) {
		this.tableEnabled = value;
		configuration.get(CATEGORY_TABLE, "enabled", true).set(value);
		configuration.save();
	}

	public int getTableX() {
		return tableX;
	}

	public int getTableY() {
		return tableY;
	}

	public SortMode getTableSort() {
		return tableSort;
	}

	public void setTableSort(SortMode value) {
		this.tableSort = value;
		configuration.get(CATEGORY_TABLE, "sort", SortMode.STARS.name()).set(value.name());
		configuration.save();
	}

	public boolean isShowAccountAge() {
		return showAccountAge;
	}

	public boolean isShowWinstreak() {
		return showWinstreak;
	}

	public boolean isShowWlr() {
		return showWlr;
	}

	public boolean isHideOwnTeam() {
		return hideOwnTeam;
	}

	public int getTableMaxRows() {
		return tableMaxRows;
	}

	public boolean isTableBackground() {
		return tableBackground;
	}

	public boolean isTabStatsEnabled() {
		return tabStatsEnabled;
	}

	public void setTabStatsEnabled(boolean value) {
		this.tabStatsEnabled = value;
		configuration.get(CATEGORY_TAB, "enabled", false).set(value);
		configuration.save();
	}

	public boolean isNametagStars() {
		return nametagStars;
	}

	public void setNametagStars(boolean value) {
		this.nametagStars = value;
		configuration.get(CATEGORY_NAMETAG, "stars", false).set(value);
		configuration.save();
	}

	public boolean isNametagFkdr() {
		return nametagFkdr;
	}

	public boolean isLookTooltipEnabled() {
		return lookTooltipEnabled;
	}

	public void setLookTooltipEnabled(boolean value) {
		this.lookTooltipEnabled = value;
		configuration.get(CATEGORY_TOOLTIP, "enabled", true).set(value);
		configuration.save();
	}

	/** Cosine of the half-angle, which is what the picker actually compares against. */
	public double getLookCosine() {
		return Math.cos(Math.toRadians(lookAngleDegrees));
	}

	public boolean isLookThroughWalls() {
		return lookThroughWalls;
	}

	public int getLookOffsetY() {
		return lookOffsetY;
	}

	public boolean isChatHoverEnabled() {
		return chatHoverEnabled;
	}

	public void setChatHoverEnabled(boolean value) {
		this.chatHoverEnabled = value;
		configuration.get(CATEGORY_ALERTS, "chatHover", true).set(value);
		configuration.save();
	}

	public boolean isNickAlert() {
		return nickAlert;
	}

	public void setNickAlert(boolean value) {
		this.nickAlert = value;
		configuration.get(CATEGORY_ALERTS, "nickAlert", true).set(value);
		configuration.save();
	}

	public boolean isStreakAlert() {
		return streakAlert;
	}

	public int getStreakThreshold() {
		return streakThreshold;
	}

	/** The mode for a slot, or an empty string when that hotkey is unassigned. */
	public String getQueueMode(int slot) {
		return slot < 0 || slot >= QUEUE_SLOTS ? "" : queueModes[slot];
	}

	public boolean isQueueOnlyOnHypixel() {
		return queueOnlyOnHypixel;
	}

	public long getCacheMillis() {
		return cacheMinutes * 60_000L;
	}

	public boolean isDebugLogging() {
		return debugLogging;
	}
}
