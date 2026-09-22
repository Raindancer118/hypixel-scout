package de.raindancer118.hypixelscout;

import de.raindancer118.hypixelscout.core.Clock;
import de.raindancer118.hypixelscout.core.HypixelClient;
import de.raindancer118.hypixelscout.core.MojangClient;
import de.raindancer118.hypixelscout.core.RateLimiter;
import de.raindancer118.hypixelscout.core.StatsCache;
import de.raindancer118.hypixelscout.core.StatsService;
import de.raindancer118.hypixelscout.mc.ChatHover;
import de.raindancer118.hypixelscout.mc.HypixelLocationBridge;
import de.raindancer118.hypixelscout.mc.LookTargetTooltip;
import de.raindancer118.hypixelscout.mc.NametagStars;
import de.raindancer118.hypixelscout.mc.PartyReport;
import de.raindancer118.hypixelscout.mc.QuickQueue;
import de.raindancer118.hypixelscout.mc.RosterTracker;
import de.raindancer118.hypixelscout.mc.ScoutAlerts;
import de.raindancer118.hypixelscout.mc.ScoutCommand;
import de.raindancer118.hypixelscout.mc.ScoutConfig;
import de.raindancer118.hypixelscout.mc.StatsOverlay;
import de.raindancer118.hypixelscout.mc.TabStatsOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Bedwars opponent stats for Minecraft 1.8.9.
 *
 * <p>Reads nothing but the public Hypixel API: the same numbers any website shows for the same
 * player, fetched with the player's own key. It sends no chat by itself, changes nothing about how
 * the game plays, and never touches the server beyond the commands the player presses a key for.
 *
 * <p>The Hypixel Mod API is a hard requirement rather than a nicety. It is what says a Bedwars game
 * has started; the alternative is matching English chat lines and breaking the day Hypixel rewords
 * one.
 */
@Mod(modid = HypixelScout.MOD_ID, name = "Hypixel Scout", version = HypixelScout.VERSION,
		clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]",
		dependencies = "required-after:hypixel_mod_api",
		guiFactory = "de.raindancer118.hypixelscout.mc.ScoutGuiFactory")
public final class HypixelScout {
	public static final String MOD_ID = "hypixelscout";
	/** Forge wants this as a compile-time constant, so it cannot be read from the jar. */
	public static final String VERSION = "0.1.0";

	@Mod.Instance(MOD_ID)
	public static HypixelScout instance;

	private ScoutConfig config;
	private HypixelClient client;
	private MojangClient mojang;
	private StatsService stats;
	private RosterTracker roster;
	private ScoutAlerts alerts;
	private PartyReport partyReport;
	private boolean modApiPresent;

	@Mod.EventHandler
	public void onPreInit(FMLPreInitializationEvent event) {
		config = new ScoutConfig(event.getSuggestedConfigurationFile());
	}

	@Mod.EventHandler
	public void onInit(FMLInitializationEvent event) {
		client = new HypixelClient(HypixelClient.DEFAULT_BASE_URL,
				new RateLimiter(Clock.SYSTEM, HypixelClient.REQUESTS_PER_WINDOW,
						HypixelClient.WINDOW_MILLIS));
		client.setApiKey(config.getApiKey());

		mojang = MojangClient.live();
		stats = new StatsService(client, new StatsCache(Clock.SYSTEM, config.getCacheMillis()));
		roster = new RosterTracker(stats);
		alerts = new ScoutAlerts(config, roster);
		partyReport = new PartyReport(roster, stats, config);

		stats.setListener(alerts);

		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(new StatsOverlay(roster, stats, config));
		MinecraftForge.EVENT_BUS.register(new TabStatsOverlay(roster, stats, config));
		MinecraftForge.EVENT_BUS.register(new LookTargetTooltip(stats, config, roster));
		MinecraftForge.EVENT_BUS.register(new NametagStars(stats, config, roster));
		MinecraftForge.EVENT_BUS.register(new ChatHover(roster, stats, config));
		MinecraftForge.EVENT_BUS.register(partyReport);

		QuickQueue queue = new QuickQueue(config);
		queue.register();
		MinecraftForge.EVENT_BUS.register(queue);

		ClientCommandHandler.instance.registerCommand(new ScoutCommand(this));

		// A missing Mod API is fatal to what this mod does, but Forge has already refused to load
		// without it; the flag exists so /scout status can say what is going on.
		new HypixelLocationBridge(roster).register();
		modApiPresent = true;
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			roster.onTick();
		}
	}

	/** Leaving the server ends the game as surely as the location packet would. */
	@SubscribeEvent
	public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		roster.onLocationChanged(false);
		alerts.reset();
		partyReport.cancel();
		stats.invalidate();
	}

	@SubscribeEvent
	public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
		if (MOD_ID.equals(event.modID)) {
			config.load();
		}
	}

	/** Whether the client is connected to Hypixel, for the features that only make sense there. */
	public static boolean onHypixel() {
		Minecraft mc = Minecraft.getMinecraft();
		ServerData server = mc.getCurrentServerData();
		if (server == null || server.serverIP == null) {
			return false;
		}

		String address = server.serverIP.toLowerCase(java.util.Locale.ROOT);
		return address.endsWith("hypixel.net") || address.contains("hypixel.net:");
	}

	public ScoutConfig getConfig() {
		return config;
	}

	public HypixelClient getClient() {
		return client;
	}

	public MojangClient getMojang() {
		return mojang;
	}

	public StatsService getStats() {
		return stats;
	}

	public RosterTracker getRoster() {
		return roster;
	}

	public PartyReport getPartyReport() {
		return partyReport;
	}

	public boolean isModApiPresent() {
		return modApiPresent;
	}
}
