package de.raindancer118.hypixelscout.gametest;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local stand-in for the Hypixel and Mojang APIs, answering with made-up players.
 *
 * <p>The mod's real HTTP path runs against it — request, headers, status codes, parsing — without
 * a key, a network, or a single real player's data.
 */
final class HypixelStub implements AutoCloseable {
	record Player(String name, int stars, int finalKills, int finalDeaths, int wins, int losses,
			Integer winstreak, String rank, boolean nicked) {
	}

	static final String KEY = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";

	private final HttpServer server;
	private final Map<String, Player> byUuid = new ConcurrentHashMap<>();
	private final Map<String, UUID> byName = new ConcurrentHashMap<>();
	final AtomicInteger playerRequests = new AtomicInteger();

	HypixelStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

		server.createContext("/v2/player", exchange -> {
			int made = playerRequests.incrementAndGet();
			// Answering like a production key: its budget, and what is left of it.
			exchange.getResponseHeaders().add("RateLimit-Limit", "600");
			exchange.getResponseHeaders().add("RateLimit-Remaining", String.valueOf(600 - made));
			exchange.getResponseHeaders().add("RateLimit-Reset", "240");
			String query = exchange.getRequestURI().getQuery();
			String uuid = query.substring(query.indexOf("uuid=") + 5).toLowerCase(Locale.ROOT);

			if (!KEY.equals(exchange.getRequestHeaders().getFirst("API-Key"))) {
				send(exchange, 403, "{\"success\":false,\"cause\":\"Invalid API key\"}");
				return;
			}

			Player player = byUuid.get(uuid);
			send(exchange, 200, player == null || player.nicked()
					? "{\"success\":true,\"player\":null}"
					: json(player));
		});

		server.createContext("/users/profiles/minecraft/", exchange -> {
			String name = exchange.getRequestURI().getPath().substring("/users/profiles/minecraft/".length());
			UUID uuid = byName.get(name.toLowerCase(Locale.ROOT));
			if (uuid == null) {
				send(exchange, 404, "");
				return;
			}
			send(exchange, 200, "{\"id\":\"" + uuid.toString().replace("-", "") + "\",\"name\":\"" + name + "\"}");
		});

		server.start();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	void add(UUID uuid, Player player) {
		byUuid.put(uuid.toString().replace("-", ""), player);
		byName.put(player.name().toLowerCase(Locale.ROOT), uuid);
	}

	private static String json(Player p) {
		long day = 86_400_000L;
		long now = System.currentTimeMillis();
		String rank = p.rank() == null ? "" : ",\"newPackageRank\":\"" + p.rank() + "\"";
		String streak = p.winstreak() == null ? "" : ",\"winstreak\":" + p.winstreak();

		return "{\"success\":true,\"player\":{\"displayname\":\"" + p.name() + "\"" + rank
				+ ",\"networkExp\":" + (p.stars() * 9000L) + ",\"karma\":" + (p.stars() * 1300L)
				+ ",\"firstLogin\":" + (now - (100 + p.stars()) * day) + ",\"lastLogin\":" + (now - day)
				+ ",\"achievements\":{\"bedwars_level\":" + p.stars() + "}"
				+ ",\"socialMedia\":{\"links\":{\"DISCORD\":\"" + p.name().toLowerCase(Locale.ROOT) + "\"}}"
				+ ",\"stats\":{\"Bedwars\":{\"final_kills_bedwars\":" + p.finalKills()
				+ ",\"final_deaths_bedwars\":" + p.finalDeaths() + ",\"wins_bedwars\":" + p.wins()
				+ ",\"losses_bedwars\":" + p.losses() + ",\"beds_broken_bedwars\":" + (p.wins() * 2)
				+ ",\"beds_lost_bedwars\":" + p.losses() + ",\"kills_bedwars\":" + (p.finalKills() * 2)
				+ ",\"deaths_bedwars\":" + (p.finalDeaths() * 3) + streak + "}}}}";
	}

	private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
			throws IOException {
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
