package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.config.ScoutSettings;
import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.GameStart;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.KeyCheck;
import de.raindancer118.hypixelscout.core.LookupHistory;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.NickAwareSource;
import de.raindancer118.hypixelscout.core.PlayerStats;
import de.raindancer118.hypixelscout.core.RateLimiter;
import de.raindancer118.hypixelscout.core.Roster;
import de.raindancer118.hypixelscout.core.StatFormat;
import de.raindancer118.hypixelscout.core.StatsCache;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.core.ThreatScale;
import de.raindancer118.hypixelscout.game.AutoRequeue;
import de.raindancer118.hypixelscout.game.Callouts;
import de.raindancer118.hypixelscout.game.ChatHover;
import de.raindancer118.hypixelscout.game.CheatSensor;
import de.raindancer118.hypixelscout.game.Flights;
import de.raindancer118.hypixelscout.game.Hazards;
import de.raindancer118.hypixelscout.game.LocationBridge;
import de.raindancer118.hypixelscout.game.Nametags;
import de.raindancer118.hypixelscout.game.PartyReport;
import de.raindancer118.hypixelscout.game.ProximityAlerts;
import de.raindancer118.hypixelscout.game.QuickQueue;
import de.raindancer118.hypixelscout.game.ScoutAlerts;
import de.raindancer118.hypixelscout.game.TabListReader;
import de.raindancer118.hypixelscout.game.Teams;
import de.raindancer118.hypixelscout.startup.StartupTestListener;
import de.raindancer118.hypixelscout.ui.Chat;
import de.raindancer118.hypixelscout.ui.ProfileView;
import de.raindancer118.hypixelscout.ui.ScoutTheme;
import de.raindancer118.hypixelscout.ui.Suspects;
import de.raindancer118.hypixelscout.ui.Threats;
import de.raindancer118.hypixelscout.ui.hud.HazardElement;
import de.raindancer118.hypixelscout.ui.hud.IncomingElement;
import de.raindancer118.hypixelscout.ui.hud.LookTooltipElement;
import de.raindancer118.hypixelscout.ui.hud.PeekElement;
import de.raindancer118.hypixelscout.ui.hud.ProximityElement;
import de.raindancer118.hypixelscout.ui.hud.SuspectsElement;
import de.raindancer118.hypixelscout.ui.hud.TabStatsElement;
import de.raindancer118.hypixelscout.ui.hud.TableHud;
import de.raindancer118.hypixelscout.ui.hud.TableHudElement;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bedwars opponent stats, inside the game.
 *
 * <p>Reads nothing but the public Hypixel API with the player's own key: the numbers any stats site
 * shows for the same player. It sends no chat by itself, changes nothing about how the game plays,
 * and only ever sends the server the commands the player pressed a key for.
 *
 * <p>The Hypixel Mod API is a hard requirement rather than a nicety: it is what says a Bedwars game
 * has started. The alternative is matching English chat lines, which breaks the day Hypixel rewords
 * one.
 *
 * <p>This is Phase 1 of the Forge 1.8.9 port on branch {@code forge-1.8.9} (see {@code Project.md}):
 * the foundation — settings, the API clients, the roster, alerts, party/team reports, callouts, the
 * queue, auto-requeue, lookups, the tick loop, game-start detection, nametags and chat hover — with
 * no HUD elements, screens, world-drawn lines, projectile awareness or cheat detection yet. Those
 * are later phases on this branch, built on top of what this class already exposes: a later phase's
 * {@code onInitializeClient}-equivalent adds its own {@code HudElementRegistry}-style registration
 * (1.8.9's is {@code RenderGameOverlayEvent}) right where 26.2's does — after {@link #stats}/{@link
 * #roster} exist below — and reaches this class's state through the same accessors 26.2's HUD/screen
 * code already calls ({@link #settings()}, {@link #roster()}, {@link #stats()}, …).
 */
@Mod(modid = HypixelScout.MOD_ID, name = "Hypixel Scout", version = HypixelScout.VERSION,
		clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]",
		dependencies = "required-after:hypixel_mod_api",
		guiFactory = "de.raindancer118.hypixelscout.mc.ScoutGuiFactory")
public final class HypixelScout {

	public static final String MOD_ID = "hypixelscout";
	/**
	 * Forge wants this as a compile-time constant, so it cannot be read from a resource at
	 * runtime. {@link HypixelScoutVersion#VERSION} is itself a {@code static final String}
	 * initialised from {@code project.version} by the {@code generateVersion} Gradle task, which
	 * Java's constant-folding treats as a constant expression too.
	 */
	public static final String VERSION = HypixelScoutVersion.VERSION;

	/** A second, so a lobby filling over half a minute is picked up as it fills. */
	private static final int SCAN_INTERVAL_TICKS = 20;

	private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private static HypixelScout instance;

	/** Off the render thread: name lookups and the key check. Stats have their own pool. */
	private final ExecutorService worker = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "HypixelScout-worker");
			thread.setDaemon(true);
			return thread;
		}
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
	private ProximityAlerts proximity;
	private LocationBridge location;
	private AutoRequeue requeue;
	private Callouts callouts;
	private ScoutKeys keys;
	private ChatHover hover;
	private TableHud tableHud;
	private TableHudElement table;
	private PeekElement peek;
	private ProximityElement proximityElement;
	private Flights flights;
	private Hazards hazards;
	private CheatSensor cheats;
	private boolean modApiPresent;
	private int scanTicks;
	/** Whether the scoreboard shows real Bedwars teams, checked with the roster every second. */
	private volatile boolean teamsReady;
	private volatile ThreatScale threatScale = ThreatScale.ABSOLUTE;

	public static HypixelScout get() {
		return instance;
	}

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		instance = this;

		File configFile = new File(event.getModConfigurationDirectory(), "hypixelscout.json");
		settings = ScoutSettings.load(configFile.toPath());
		if (settings.problem() != null) {
			LOGGER.warn("Settings file: {} — using defaults until it is fixed", settings.problem());
		}
	}

	@Mod.EventHandler
	public void onInit(FMLInitializationEvent event) {
		LOGGER.info("Hypixel Scout {} loaded on Forge 1.8.9 (common: {} star={})", VERSION,
				StatFormat.class.getName(), StatFormat.star(1));

		ScoutTheme.useAccent(new java.util.function.Supplier<de.raindancer118.hypixelscout.config.Accent>() {
			@Override
			public de.raindancer118.hypixelscout.config.Accent get() {
				return settings.accent;
			}
		});
		ProfileView.use(new java.util.function.Supplier<ScoutSettings.Profile>() {
			@Override
			public ScoutSettings.Profile get() {
				return settings.cards.profile;
			}
		});
		// Sensitivity and focus are applied on every read, so changing either shows at once.
		Threats.use(new java.util.function.Supplier<ThreatScale>() {
			@Override
			public ThreatScale get() {
				return threatScale.withSensitivity(settings.threatSensitivity / 100.0).withFocus(settings.threatFocus);
			}
		});
		Chat.useAccent(new java.util.function.Supplier<de.raindancer118.hypixelscout.config.Accent>() {
			@Override
			public de.raindancer118.hypixelscout.config.Accent get() {
				return settings.accent;
			}
		});

		client = new HypixelClient(HypixelClient.DEFAULT_BASE_URL,
				new RateLimiter(Clock.SYSTEM, HypixelClient.REQUESTS_PER_WINDOW, HypixelClient.WINDOW_MILLIS));
		client.setApiKey(settings.apiKey);
		mojang = MojangClient.live();
		cache = new StatsCache(Clock.SYSTEM, settings.cacheMinutes * 60_000L);
		// Nicks are told apart through Mojang first, so they cost nothing from the key's budget.
		stats = new StatsService(new NickAwareSource(client, new NickAwareSource.NameLookup() {
			@Override
			public UUID uuidOf(String name) {
				return mojang.uuidOf(name);
			}
		}), cache);
		roster = new Roster(stats);
		alerts = new ScoutAlerts(settingsSupplier(), roster);
		stats.setListener(alerts);
		partyReport = new PartyReport(roster, stats, settingsSupplier());
		callouts = new Callouts(roster, stats, partyReport, settingsSupplier());
		queue = new QuickQueue(settingsSupplier());
		location = new LocationBridge(roster, new Runnable() {
			@Override
			public void run() {
				gameJoined();
			}
		});
		requeue = new AutoRequeue(roster, queue, location, settingsSupplier());
		lookups = new LookupHistory(ScoutSettings.MAX_LOOKUPS, settings.recentLookups);
		new Nametags(roster, stats, settingsSupplier());

		proximity = new ProximityAlerts(roster, stats, settingsSupplier(), new java.util.function.BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return teamsReady;
			}
		});

		hover = new ChatHover(roster, stats, settingsSupplier());
		MinecraftForge.EVENT_BUS.register(hover);

		registerHudElements();

		keys = new ScoutKeys(this);
		keys.register();
		ScoutCommands.register(this);

		MinecraftForge.EVENT_BUS.register(this);
		FMLCommonHandler.instance().bus().register(this);

		location.register();
		modApiPresent = Loader.isModLoaded("hypixel_mod_api");

		if (Boolean.getBoolean("hypixelscout.startupTest")) {
			LOGGER.info("hypixelscout.startupTest=true — registering the startup test listener");
			MinecraftForge.EVENT_BUS.register(new StartupTestListener());
		}

		LOGGER.info("Hypixel Scout ready ({} API key)", client.hasApiKey() ? "with" : "without an");
	}

	/**
	 * Phase 2a of this branch's port (see {@code Project.md}): everything drawn on the HUD — the
	 * in-game table, the peek overlay, the look tooltip, the tab list replacement, proximity
	 * popups, the incoming-projectile warning, hazards and the suspects card. Kept in one small
	 * method, called once from {@link #onInit}, since {@code game.Flights}/{@code game.Hazards}/
	 * {@code game.CheatSensor} are being ported on this same branch at the same time as this method
	 * — {@link #tableEditor}/{@link #suspectsEditor} (Phase 3) wire the screens that move these two
	 * elements around, not this method.
	 *
	 * <p>Every element fires on {@link net.minecraftforge.client.event.RenderGameOverlayEvent.Post}
	 * with {@code ElementType.ALL} except the tab list ({@code Pre}, {@code PLAYER_LIST}, cancelling
	 * vanilla's own) and the look tooltip ({@code Post}, {@code CROSSHAIRS}) — the same three event
	 * shapes the old {@code 1.8.9-support} branch's overlays already used.
	 */
	private void registerHudElements() {
		tableHud = new TableHud(roster, stats, settingsSupplier(), new BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return client.hasApiKey();
			}
		});
		table = new TableHudElement(tableHud, settingsSupplier(), roster);
		MinecraftForge.EVENT_BUS.register(table);

		peek = new PeekElement(roster, stats, tableHud, settingsSupplier());
		BooleanSupplier peekHeld = new BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return peek.isHeld();
			}
		};
		table.hideWhile(peekHeld);
		// Last of all, so the peek sits above the table, the tooltip and vanilla's HUD.
		MinecraftForge.EVENT_BUS.register(peek);

		MinecraftForge.EVENT_BUS.register(new LookTooltipElement(roster, stats, settingsSupplier()).hideWhile(peekHeld));

		proximityElement = new ProximityElement(proximity, stats, settingsSupplier()).hideWhile(peekHeld);
		MinecraftForge.EVENT_BUS.register(proximityElement);

		flights = new Flights(roster, settingsSupplier());
		MinecraftForge.EVENT_BUS.register(new IncomingElement(flights, settingsSupplier()));
		new de.raindancer118.hypixelscout.ui.world.FlightLines(flights, settingsSupplier()).register();

		hazards = new Hazards(roster, settingsSupplier());
		MinecraftForge.EVENT_BUS.register(new HazardElement(hazards, settingsSupplier()));
		new de.raindancer118.hypixelscout.ui.world.HazardLines(hazards).register();

		cheats = new CheatSensor(roster, settingsSupplier());
		Suspects.use(new java.util.function.Function<String, java.util.List<de.raindancer118.hypixelscout.cheat.Suspicion.Flag>>() {
			@Override
			public java.util.List<de.raindancer118.hypixelscout.cheat.Suspicion.Flag> apply(String name) {
				return settings.cheats.mark ? cheats.flags(name) : java.util.Collections.<de.raindancer118.hypixelscout.cheat.Suspicion.Flag>emptyList();
			}
		}, new java.util.function.ToDoubleFunction<String>() {
			@Override
			public double applyAsDouble(String name) {
				return cheats.confidence(name);
			}
		});
		MinecraftForge.EVENT_BUS.register(new SuspectsElement(cheats, settingsSupplier(), roster).hideWhile(peekHeld));

		MinecraftForge.EVENT_BUS.register(new TabStatsElement(roster, stats, settingsSupplier()));
	}

	private java.util.function.Supplier<ScoutSettings> settingsSupplier() {
		return new java.util.function.Supplier<ScoutSettings>() {
			@Override
			public ScoutSettings get() {
				return settings;
			}
		};
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		Minecraft minecraft = Minecraft.getMinecraft();
		keys.tick(minecraft);
		table.setKeyHeld(keys.isTableHeld());
		peek.setHeld(keys.isPeekHeld());
		partyReport.tick();
		proximity.tick();
		flights.tick(minecraft);
		hazards.tick(minecraft);
		cheats.tick(minecraft);
		requeue.tick();

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

	/**
	 * The opening line of a game, as a second way to see the start besides the scoreboard, plus
	 * every chat line handed to {@link AutoRequeue} for the final-kill lines it watches for.
	 * {@link EventPriority#HIGHEST} so this reads the server's own wording before {@link ChatHover}
	 * (registered separately, default priority) has a chance to rebuild the component.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onChat(ClientChatReceivedEvent event) {
		if (event.type == 2 || event.message == null) {
			// The action bar, not game chat — GameStart's line and kill feed lines never appear there.
			return;
		}

		// getUnformattedText() (not ...ForChat()) is the whole tree flattened; ...ForChat() is only
		// this one component's own text, ignoring every sibling — exactly backwards from what the
		// names suggest, and the one 1.8.9 chat-API trap worth a comment here.
		String text = event.message.getUnformattedText();
		if (roster.isInGame() && !roster.hasStarted() && GameStart.isStartLine(text)) {
			matchStarted();
		}
		requeue.onChat(text);
	}

	@SubscribeEvent
	public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		leftServer();
	}

	/** The waiting lobby is over once the scoreboard has put everybody into real teams. */
	private boolean matchIsOn(Minecraft minecraft) {
		if (minecraft.thePlayer == null) {
			return false;
		}

		Map<String, String> teams = new LinkedHashMap<String, String>();
		for (Roster.Member member : roster.members()) {
			teams.put(member.name(), Teams.of(member.name()).name());
		}
		teams.put(minecraft.thePlayer.getName(), Teams.own().name());

		return GameStart.hasStarted(roster.mode(), teams, minecraft.thePlayer.getName());
	}

	private void matchStarted() {
		if (roster.isInGame() && !roster.hasStarted()) {
			roster.markStarted();
			LOGGER.debug("Match started in {} on {}", roster.mode(), roster.map());
		}
	}

	/** The player's own stats and their teammates', for threat levels measured against them. */
	private ThreatScale currentThreatScale(Minecraft minecraft) {
		if (minecraft.thePlayer == null) {
			return ThreatScale.ABSOLUTE;
		}

		List<PlayerStats> mates = new ArrayList<PlayerStats>();
		String self = minecraft.thePlayer.getName();
		for (Roster.Member member : roster.members()) {
			if (!member.name().equals(self) && Teams.isOwnTeam(member.name())) {
				mates.add(stats.peek(member.uuid()));
			}
		}

		return ThreatScale.of(settings.threatBasis, stats.peek(minecraft.thePlayer.getGameProfile().getId()), mates);
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
		hazards.newRound();
		cheats.newRound();
		teamsReady = false;
		requeue.gameJoined();
		scanTicks = 0;
		roster.refresh(TabListReader.current());
	}

	/**
	 * What the location packet does when a game starts, for a future client game test — a
	 * singleplayer world has no Hypixel to send one.
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
		hazards.reset();
		cheats.endRound();
		teamsReady = false;
		requeue.reset();
		partyReport.cancel();
	}

	// --- actions shared by the keys and the commands -----------------------------------------------

	/** Saves the settings and applies the ones something else holds a copy of. */
	public void saveSettings() {
		client.setApiKey(settings.apiKey);
		cache.setTtlMillis(settings.cacheMinutes * 60_000L);
		settings.recentLookups = new ArrayList<String>(lookups.names());
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
			for (Roster.Member member : roster.members()) {
				stats.request(member.uuid(), member.name());
			}
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
				for (Roster.Member member : roster.members()) {
					stats.request(member.uuid(), member.name());
				}
			}
		}
	}

	/** Checks the key against Hypixel off the render thread and hands the answer back on it. */
	public void checkKey(final Consumer<KeyCheck.Result> onResult) {
		final Minecraft minecraft = Minecraft.getMinecraft();
		final UUID self = minecraft.thePlayer == null ? null : minecraft.thePlayer.getGameProfile().getId();

		worker.execute(new Runnable() {
			@Override
			public void run() {
				final KeyCheck.Result result = KeyCheck.run(client, self);
				minecraft.addScheduledTask(new Runnable() {
					@Override
					public void run() {
						onResult.accept(result);
					}
				});
			}
		});
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

	public AutoRequeue requeue() {
		return requeue;
	}

	/** The enemies around the player, drawn by {@link #proximityElement()}. */
	public ProximityAlerts proximity() {
		return proximity;
	}

	/** The in-game table's layout and drawing, shared with a later phase's table editor screen. */
	public TableHud tableHud() {
		return tableHud;
	}

	/** The in-game table's HUD element: open/closed state and the key that toggles it. */
	public TableHudElement table() {
		return table;
	}

	/** The peek overlay: held state, and what it is currently showing (for the client startup test). */
	public PeekElement peek() {
		return peek;
	}

	public ProximityElement proximityElement() {
		return proximityElement;
	}

	/** Arrows and fireballs in the air, for the incoming-projectile warning and the world's gizmo lines. */
	public Flights flights() {
		return flights;
	}

	/** TNT, falls, beds and off-screen enemies, for the hazard HUD and the world's hazard lines. */
	public Hazards hazards() {
		return hazards;
	}

	/** Cheat detection: everybody flagged this round, and how sure the mod is. */
	public CheatSensor cheats() {
		return cheats;
	}

	public ChatHover chatHover() {
		return hover;
	}

	public ScoutKeys keys() {
		return keys;
	}

	public LocationBridge location() {
		return location;
	}

	public ExecutorService worker() {
		return worker;
	}

	public boolean isModApiPresent() {
		return modApiPresent;
	}

	// --- screens (Phase 3 of this branch's port, see Project.md) --------------------------------

	public net.minecraft.client.gui.GuiScreen settingsScreen(net.minecraft.client.gui.GuiScreen parent) {
		return new de.raindancer118.hypixelscout.ui.screen.SettingsScreen(this, parent);
	}

	public net.minecraft.client.gui.GuiScreen profileScreen(String name, UUID uuid, net.minecraft.client.gui.GuiScreen parent) {
		return new de.raindancer118.hypixelscout.ui.screen.ProfileScreen(this, name, uuid, parent);
	}

	public net.minecraft.client.gui.GuiScreen tableEditor(net.minecraft.client.gui.GuiScreen parent) {
		return new de.raindancer118.hypixelscout.ui.hud.TableEditorScreen(this, parent);
	}

	public net.minecraft.client.gui.GuiScreen suspectsEditor(net.minecraft.client.gui.GuiScreen parent) {
		return new de.raindancer118.hypixelscout.ui.hud.SuspectsEditorScreen(this, parent);
	}
}
