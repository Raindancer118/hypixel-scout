package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.LookupHistory;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.RateLimiter;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatsCache;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.ChatHover;
import de.raindancer118.hypixelscout.game.LocationBridge;
import de.raindancer118.hypixelscout.game.Nametags;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.game.QuickQueue;
import de.raindancer118.hypixelscout.game.ScoutAlerts;
import de.raindancer118.hypixelscout.game.TabListReader;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.hud.LookTooltipElement;
import de.raindancer118.hypixelscout.ui.hud.TabStatsElement;
import de.raindancer118.hypixelscout.ui.hud.TableEditorScreen;
import de.raindancer118.hypixelscout.ui.hud.TableHud;
import de.raindancer118.hypixelscout.ui.hud.TableHudElement;
import de.raindancer118.hypixelscout.ui.screen.ProfileScreen;
import de.raindancer118.hypixelscout.ui.screen.ScoutScreen;
import de.raindancer118.hypixelscout.ui.screen.SettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Bedwars opponent stats, inside the game.
 *
 * <p>Reads nothing but the public Hypixel API with the player's own key: the numbers any stats site
 * shows for the same player. It sends no chat by itself, changes nothing about how the game plays,
 * and only ever sends the server the commands the player pressed a key for.
 *
 * <p>The Hypixel Mod API is a hard requirement rather than a nicety: it is what says a Bedwars game
 * has started. The alternative is matching English chat lines and breaking the day Hypixel rewords
 * one.
 */
public final class HypixelScout implements ClientModInitializer {
	public static final String MOD_ID = "hypixelscout";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** A second, so a lobby filling over half a minute is picked up as it fills. */
	private static final int SCAN_INTERVAL_TICKS = 20;

	private static HypixelScout instance;

	/** Off the render thread: name lookups and the key check. Stats have their own pool. */
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "HypixelScout-worker");
		thread.setDaemon(true);
		return thread;
	});

	private ScoutSettings settings;
	private HypixelClient client;
	private MojangClient mojang;
	private StatsCache cache;
	private StatsService stats;
	private Roster roster;
	private ScoutAlerts alerts;
	private PartyReport partyReport;
	private QuickQueue queue;
	private LookupHistory lookups;
	private TableHud tableHud;
	private TableHudElement table;
	private ScoutKeys keys;
	private ChatHover hover;
	private boolean modApiPresent;
	private int scanTicks;

	public static HypixelScout get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;

		settings = ScoutSettings.load(FabricLoader.getInstance().getConfigDir().resolve("hypixelscout.json"));
		if (settings.problem() != null) {
			LOGGER.warn("Settings file: {} — using defaults until it is fixed", settings.problem());
		}

		ScoutTheme.useAccent(() -> settings.accent);
		Chat.useAccent(() -> settings.accent);

		client = new HypixelClient(HypixelClient.DEFAULT_BASE_URL,
				new RateLimiter(Clock.SYSTEM, HypixelClient.REQUESTS_PER_WINDOW, HypixelClient.WINDOW_MILLIS));
		client.setApiKey(settings.apiKey);
		mojang = MojangClient.live();
		cache = new StatsCache(Clock.SYSTEM, settings.cacheMinutes * 60_000L);
		stats = new StatsService(client, cache);
		roster = new Roster(stats);
		alerts = new ScoutAlerts(() -> settings, roster);
		stats.setListener(alerts);
		partyReport = new PartyReport(roster, stats, () -> settings);
		queue = new QuickQueue(() -> settings);
		lookups = new LookupHistory(ScoutSettings.MAX_LOOKUPS, settings.recentLookups);
		new Nametags(roster, stats, () -> settings);

		tableHud = new TableHud(roster, stats, () -> settings, client::hasApiKey);
		table = new TableHudElement(tableHud, () -> settings, roster);
		HudElementRegistry.addLast(id("table"), table);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("look_tooltip"),
				new LookTooltipElement(roster, stats, () -> settings));
		HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST,
				vanilla -> new TabStatsElement(vanilla, roster, stats, () -> settings));

		hover = new ChatHover(roster, stats, () -> settings);
		ClientReceiveMessageEvents.MODIFY_GAME.register(hover::modify);

		keys = new ScoutKeys(this);
		keys.register();
		ScoutCommands.register(this);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) ->
				minecraft.execute(this::leftServer));

		new LocationBridge(roster, this::gameStarted).register();
		modApiPresent = FabricLoader.getInstance().isModLoaded("hypixel-mod-api");

		LOGGER.info("Hypixel Scout ready ({} API key)", client.hasApiKey() ? "with" : "without an");
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private void tick(Minecraft minecraft) {
		keys.tick(minecraft);
		partyReport.tick(minecraft);

		if (roster.isInGame() && ++scanTicks >= SCAN_INTERVAL_TICKS) {
			scanTicks = 0;
			roster.refresh(TabListReader.current());
		}
	}

	private void gameStarted() {
		alerts.reset();
		table.close();
		// Anybody who failed last game — a hiccup, a throttle — deserves another try in this one.
		stats.clearFailures();
		scanTicks = 0;
		roster.refresh(TabListReader.current());
	}

	/**
	 * What the location packet does when a game starts, for the client game test — a singleplayer
	 * world has no Hypixel to send one.
	 */
	public void startGameForTest(String mode, String map) {
		roster.onLocationChanged(true, mode, map);
		gameStarted();
	}

	/** Leaving the server ends the game as surely as the location packet would. */
	private void leftServer() {
		roster.onLocationChanged(false, null, null);
		table.close();
		alerts.reset();
		partyReport.cancel();
	}

	// --- actions shared by the keys, the commands and the screens ---------------------------------

	/** Saves the settings and applies the ones something else holds a copy of. */
	public void saveSettings() {
		client.setApiKey(settings.apiKey);
		cache.setTtlMillis(settings.cacheMinutes * 60_000L);
		settings.recentLookups = new java.util.ArrayList<>(lookups.names());
		settings.save();

		if (settings.problem() != null) {
			LOGGER.warn("Settings file: {}", settings.problem());
		}
	}

	/** Stores a new key and looks everybody up again with it. */
	public void setApiKey(String key) {
		settings.apiKey = key == null ? "" : key.trim();
		saveSettings();
		refresh();
	}

	/** Forgets every answer and asks again. */
	public void refresh() {
		stats.invalidate();
		if (roster.isInGame()) {
			roster.refresh(TabListReader.current());
		}
	}

	/** Checks the key against Hypixel off the render thread and hands the answer back on it. */
	public void checkKey(Consumer<KeyCheck.Result> onResult) {
		Minecraft minecraft = Minecraft.getInstance();
		UUID self = minecraft.getUser().getProfileId();

		worker.execute(() -> {
			KeyCheck.Result result = KeyCheck.run(client, self);
			minecraft.execute(() -> onResult.accept(result));
		});
	}

	/** Opens a screen once the current one — usually the chat that ran a command — has closed. */
	public static void open(Screen screen) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> minecraft.gui.setScreen(screen));
	}

	public ScoutScreen scoutScreen(Screen parent) {
		return new ScoutScreen(this, parent);
	}

	public SettingsScreen settingsScreen(Screen parent) {
		return new SettingsScreen(this, parent);
	}

	public TableEditorScreen tableEditor(Screen parent) {
		return new TableEditorScreen(() -> settings, tableHud, roster, this::saveSettings, parent);
	}

	public ProfileScreen profileScreen(String name, UUID uuid, Screen parent) {
		return new ProfileScreen(this, name, uuid, parent);
	}

	public ScoutSettings settings() {
		return settings;
	}

	public HypixelClient client() {
		return client;
	}

	public MojangClient mojang() {
		return mojang;
	}

	public StatsService stats() {
		return stats;
	}

	public Roster roster() {
		return roster;
	}

	public PartyReport partyReport() {
		return partyReport;
	}

	public QuickQueue queue() {
		return queue;
	}

	public LookupHistory lookups() {
		return lookups;
	}

	public TableHudElement table() {
		return table;
	}

	public ChatHover chatHover() {
		return hover;
	}

	public ScoutKeys keys() {
		return keys;
	}

	public ExecutorService worker() {
		return worker;
	}

	public boolean isModApiPresent() {
		return modApiPresent;
	}
}
