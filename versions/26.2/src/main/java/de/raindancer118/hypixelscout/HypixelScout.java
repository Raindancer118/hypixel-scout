package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.LookupHistory;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.NickAwareSource;
import de.raindancer118.hypixelscout.core.RateLimiter;
import de.raindancer118.hypixelscout.core.GameStart;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.ui.Threats;
import de.raindancer118.hypixelscout.core.StatsCache;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.game.Callouts;
import de.raindancer118.hypixelscout.game.ChatHover;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.ui.hud.IncomingElement;
import de.raindancer118.hypixelscout.ui.world.FlightLines;
import de.raindancer118.hypixelscout.game.LocationBridge;
import de.raindancer118.hypixelscout.game.Nametags;
import de.raindancer118.hypixelscout.game.AutoRequeue;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.game.ProximityAlerts;
import de.raindancer118.hypixelscout.game.QuickQueue;
import de.raindancer118.hypixelscout.game.ScoutAlerts;
import de.raindancer118.hypixelscout.game.TabListReader;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.hud.LookTooltipElement;
import de.raindancer118.hypixelscout.ui.hud.PeekElement;
import de.raindancer118.hypixelscout.ui.hud.ProximityElement;
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
	private PeekElement peek;
	private ProximityAlerts proximity;
	private AutoRequeue requeue;
	private ProximityElement proximityElement;
	private Flights flights;
	private Callouts callouts;
	private ScoutKeys keys;
	private ChatHover hover;
	private boolean modApiPresent;
	private int scanTicks;
	/** Whether the scoreboard shows real Bedwars teams, checked with the roster every second. */
	private volatile boolean teamsReady;
	private volatile ThreatScale threatScale = ThreatScale.ABSOLUTE;

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
		// Sensitivity and focus are applied on every read, so changing either shows at once.
		Threats.use(() -> threatScale.withSensitivity(settings.threatSensitivity / 100.0)
				.withFocus(settings.threatFocus));
		Chat.useAccent(() -> settings.accent);

		client = new HypixelClient(HypixelClient.DEFAULT_BASE_URL,
				new RateLimiter(Clock.SYSTEM, HypixelClient.REQUESTS_PER_WINDOW, HypixelClient.WINDOW_MILLIS));
		client.setApiKey(settings.apiKey);
		mojang = MojangClient.live();
		cache = new StatsCache(Clock.SYSTEM, settings.cacheMinutes * 60_000L);
		// Nicks are told apart through Mojang first, so they cost nothing from the key's budget.
		stats = new StatsService(new NickAwareSource(client, mojang::uuidOf), cache);
		roster = new Roster(stats);
		alerts = new ScoutAlerts(() -> settings, roster);
		stats.setListener(alerts);
		partyReport = new PartyReport(roster, stats, () -> settings);
		callouts = new Callouts(roster, stats, partyReport, () -> settings);
		queue = new QuickQueue(() -> settings);
		requeue = new AutoRequeue(roster, queue, () -> settings);
		lookups = new LookupHistory(ScoutSettings.MAX_LOOKUPS, settings.recentLookups);
		new Nametags(roster, stats, () -> settings);

		tableHud = new TableHud(roster, stats, () -> settings, client::hasApiKey);
		table = new TableHudElement(tableHud, () -> settings, roster);
		HudElementRegistry.addLast(id("table"), table);
		peek = new PeekElement(roster, stats, tableHud, () -> settings);
		table.hideWhile(peek::isHeld);
		// Last of all, so the peek sits above the table, the tooltip and vanilla's HUD.
		HudElementRegistry.addLast(id("peek"), peek);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("look_tooltip"),
				new LookTooltipElement(roster, stats, () -> settings).hideWhile(() -> peek.isHeld()));
		proximity = new ProximityAlerts(roster, stats, () -> settings, () -> teamsReady);
		proximityElement = new ProximityElement(proximity, stats, () -> settings).hideWhile(() -> peek.isHeld());
		HudElementRegistry.attachElementBefore(id("peek"), id("proximity"), proximityElement);
		flights = new Flights(roster, () -> settings);
		new FlightLines(flights, () -> settings).register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id("incoming"),
				new IncomingElement(flights, () -> settings));
		HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST,
				vanilla -> new TabStatsElement(vanilla, roster, stats, () -> settings));

		hover = new ChatHover(roster, stats, () -> settings);
		ClientReceiveMessageEvents.MODIFY_GAME.register(hover::modify);
		// The opening line of a game, as a second way to see the start besides the scoreboard.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay && roster.isInGame() && !roster.hasStarted() && GameStart.isStartLine(message.getString())) {
				Minecraft.getInstance().execute(this::matchStarted);
			}
			if (!overlay) {
				String text = message.getString();
				Minecraft.getInstance().execute(() -> requeue.onChat(text));
			}
		});

		keys = new ScoutKeys(this);
		keys.register();
		ScoutCommands.register(this);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) ->
				minecraft.execute(this::leftServer));

		new LocationBridge(roster, this::gameJoined).register();
		requeue.register();
		modApiPresent = FabricLoader.getInstance().isModLoaded("hypixel-mod-api");

		LOGGER.info("Hypixel Scout ready ({} API key)", client.hasApiKey() ? "with" : "without an");
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private void tick(Minecraft minecraft) {
		keys.tick(minecraft);
		partyReport.tick(minecraft);
		proximity.tick(minecraft);
		flights.tick(minecraft);
		requeue.tick(minecraft);

		if (roster.isInGame() && ++scanTicks >= SCAN_INTERVAL_TICKS) {
			scanTicks = 0;
			roster.refresh(TabListReader.current());

			teamsReady = matchIsOn(minecraft);
			if (!roster.hasStarted() && (settings.lookUpInLobby || teamsReady)) {
				matchStarted();
			}

			threatScale = currentThreatScale(minecraft);
		}
	}

	/** The waiting lobby is over once the scoreboard has put everybody into real teams. */
	private boolean matchIsOn(Minecraft minecraft) {
		if (minecraft.player == null) {
			return false;
		}

		java.util.Map<String, String> teams = new java.util.LinkedHashMap<>();
		for (Roster.Member member : roster.members()) {
			teams.put(member.name(), Teams.of(member.name()).name());
		}
		teams.put(minecraft.player.getScoreboardName(), Teams.own().name());

		return GameStart.hasStarted(roster.mode(), teams, minecraft.player.getScoreboardName());
	}

	private void matchStarted() {
		if (roster.isInGame() && !roster.hasStarted()) {
			roster.markStarted();
			LOGGER.debug("Match started in {} on {}", roster.mode(), roster.map());
		}
	}

	/** The player's own stats and their teammates', for threat levels measured against them. */
	private ThreatScale currentThreatScale(Minecraft minecraft) {
		if (minecraft.player == null) {
			return ThreatScale.ABSOLUTE;
		}

		java.util.List<de.raindancer118.hypixelscout.core.PlayerStats> mates = new java.util.ArrayList<>();
		String self = minecraft.player.getScoreboardName();
		for (Roster.Member member : roster.members()) {
			if (!member.name().equals(self) && Teams.isOwnTeam(member.name())) {
				mates.add(stats.peek(member.uuid()));
			}
		}

		return ThreatScale.of(settings.threatBasis, stats.peek(minecraft.player.getUUID()), mates);
	}

	/** What the threat levels are measured against right now. */
	public ThreatScale threatScale() {
		return threatScale;
	}

	/** A new game server: the waiting lobby, which is not yet the match. */
	private void gameJoined() {
		alerts.reset();
		table.close();
		// Anybody who failed last game — a hiccup, a throttle — deserves another try in this one.
		stats.clearFailures();
		proximity.reset();
		flights.reset();
		teamsReady = false;
		requeue.gameJoined();
		scanTicks = 0;
		roster.refresh(TabListReader.current());
	}

	/**
	 * What the location packet does when a game starts, for the client game test — a singleplayer
	 * world has no Hypixel to send one.
	 */
	public void startGameForTest(String mode, String map) {
		roster.onLocationChanged(true, mode, map);
		gameJoined();
	}

	/** Leaving the server ends the game as surely as the location packet would. */
	private void leftServer() {
		roster.onLocationChanged(false, null, null);
		table.close();
		alerts.reset();
		proximity.reset();
		flights.reset();
		teamsReady = false;
		requeue.reset();
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
		// Everybody again with the new key, but a key alone is no reason to start in the lobby.
		stats.invalidate();
		if (roster.hasStarted()) {
			roster.members().forEach(member -> stats.request(member.uuid(), member.name()));
		}
	}

	/**
	 * Forgets every answer and asks again. In the waiting lobby this is also the player saying
	 * "look them up now" — the one way lookups start before the match does.
	 */
	public void refresh() {
		stats.invalidate();
		if (roster.isInGame()) {
			roster.refresh(TabListReader.current());
			if (!roster.hasStarted()) {
				roster.markStarted();
			} else {
				roster.members().forEach(member -> stats.request(member.uuid(), member.name()));
			}
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

	public Callouts callouts() {
		return callouts;
	}

	public QuickQueue queue() {
		return queue;
	}

	public LookupHistory lookups() {
		return lookups;
	}

	public PeekElement peek() {
		return peek;
	}

	public AutoRequeue requeue() {
		return requeue;
	}

	/** The proximity popups as drawn, for the client game test. */
	public ProximityElement proximity() {
		return proximityElement;
	}

	/** The arrows and fireballs in the air, for the client game test. */
	public Flights flights() {
		return flights;
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
