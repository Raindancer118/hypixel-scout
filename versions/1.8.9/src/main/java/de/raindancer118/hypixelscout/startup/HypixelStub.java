package de.raindancer118.hypixelscout.startup;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local stand-in for the Hypixel and Mojang APIs, answering with made-up players — the same
 * technique 26.2's {@code gametest.HypixelStub} uses for its client game test, ported here since
 * Forge 1.8.9 has no client-gametest API of its own (see {@code startup.StartupTestRunner}).
 *
 * <p>The mod's real HTTP path runs against it — request, headers, status codes, parsing — without a
 * key, a network, or a single real player's data. {@code record Player} becomes a plain final class
 * since Java 8 has none.
 */
final class HypixelStub implements AutoCloseable {
	static final class Player {
		private final String name;
		private final int stars;
		private final int finalKills;
		private final int finalDeaths;
		private final int wins;
		private final int losses;
		private final Integer winstreak;
		private final String rank;
		private final boolean nicked;

		Player(String name, int stars, int finalKills, int finalDeaths, int wins, int losses,
				Integer winstreak, String rank, boolean nicked) {
			this.name = name;
			this.stars = stars;
			this.finalKills = finalKills;
			this.finalDeaths = finalDeaths;
			this.wins = wins;
			this.losses = losses;
			this.winstreak = winstreak;
			this.rank = rank;
			this.nicked = nicked;
		}
	}

	static final String KEY = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";

	private final HttpServer server;
	private final Map<String, Player> byUuid = new ConcurrentHashMap<String, Player>();
	private final Map<String, UUID> byName = new ConcurrentHashMap<String, UUID>();
	final AtomicInteger playerRequests = new AtomicInteger();
	/** Every UUID Hypixel was asked about, undashed — for the "no duplicate requests" assertion. */
	final Set<String> asked = ConcurrentHashMap.newKeySet();

	HypixelStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

		server.createContext("/v2/player", new com.sun.net.httpserver.HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				playerRequests.incrementAndGet();
				// Answering like a production key: its budget, and what is left of it.
				exchange.getResponseHeaders().add("RateLimit-Limit", "600");
				exchange.getResponseHeaders().add("RateLimit-Remaining",
						String.valueOf(600 - playerRequests.get()));
				exchange.getResponseHeaders().add("RateLimit-Reset", "240");
				String query = exchange.getRequestURI().getQuery();
				String uuid = query.substring(query.indexOf("uuid=") + 5).toLowerCase(Locale.ROOT);
				asked.add(uuid);

				if (!KEY.equals(exchange.getRequestHeaders().getFirst("API-Key"))) {
					send(exchange, 403, "{\"success\":false,\"cause\":\"Invalid API key\"}");
					return;
				}

				Player player = byUuid.get(uuid);
				send(exchange, 200, player == null || player.nicked
						? "{\"success\":true,\"player\":null}"
						: json(player));
			}
		});

		server.createContext("/users/profiles/minecraft/", new com.sun.net.httpserver.HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				String name = exchange.getRequestURI().getPath().substring("/users/profiles/minecraft/".length());
				UUID uuid = byName.get(name.toLowerCase(Locale.ROOT));
				if (uuid == null) {
					send(exchange, 404, "");
					return;
				}
				send(exchange, 200, "{\"id\":\"" + uuid.toString().replace("-", "") + "\",\"name\":\"" + name + "\"}");
			}
		});

		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void add(UUID uuid, Player player) {
		byUuid.put(uuid.toString().replace("-", ""), player);
		// A nick is a name no Mojang account has, as it is for real.
		if (!player.nicked) {
			byName.put(player.name.toLowerCase(Locale.ROOT), uuid);
		}
	}

	private static String json(Player p) {
		long day = 86_400_000L;
		long now = System.currentTimeMillis();
		String rank = p.rank == null ? "" : ",\"newPackageRank\":\"" + p.rank + "\"";
		String streak = p.winstreak == null ? "" : ",\"winstreak\":" + p.winstreak;

		return "{\"success\":true,\"player\":{\"displayname\":\"" + p.name + "\"" + rank
				+ ",\"networkExp\":" + (p.stars * 9000L) + ",\"karma\":" + (p.stars * 1300L)
				+ ",\"firstLogin\":" + (now - (100 + p.stars) * day) + ",\"lastLogin\":" + (now - day)
				+ ",\"achievements\":{\"bedwars_level\":" + p.stars + "}"
				+ ",\"socialMedia\":{\"links\":{\"DISCORD\":\"" + p.name.toLowerCase(Locale.ROOT) + "\"}}"
				+ ",\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":" + p.finalKills
				+ ",\"final_deaths_bedwars\":" + p.finalDeaths + ",\"wins_bedwars\":" + p.wins
				+ ",\"losses_bedwars\":" + p.losses + ",\"beds_broken_bedwars\":" + (p.wins * 2)
				+ ",\"beds_lost_bedwars\":" + p.losses + ",\"kills_bedwars\":" + (p.finalKills * 2)
				+ ",\"deaths_bedwars\":" + (p.finalDeaths * 3) + streak + "}}}}";
	}

	private static void send(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
