package de.raindancer118.hypixelscout.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * The Hypixel API, over {@code HttpURLConnection}.
 *
 * <p>No HTTP library: the JDK's own client is enough for one GET, and shading one into a mod jar is
 * a class-loader argument nobody needs. Gson comes with the game.
 *
 * <p>The base URL is a parameter so the tests can point it at a stub. The key travels as the
 * {@code API-Key} header — v2 removed the query parameter, and a key in a URL ends up in logs.
 */
public final class HypixelClient implements StatsSource {
	public static final String DEFAULT_BASE_URL = "https://api.hypixel.net";

	/** Hypixel's documented budget is 300 requests per five minutes for a personal key. */
	public static final int REQUESTS_PER_WINDOW = 300;
	public static final long WINDOW_MILLIS = 300_000L;

	private static final int TIMEOUT_MILLIS = 8000;

	private volatile String baseUrl;
	private final RateLimiter limiter;
	private volatile String apiKey;

	public HypixelClient(String baseUrl, RateLimiter limiter) {
		this.baseUrl = baseUrl;
		this.limiter = limiter;
	}

	/**
	 * Points every later request somewhere else. The client game test serves recorded answers from
	 * a local stub this way; the mod itself never calls it.
	 */
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public static HypixelClient live() {
		return new HypixelClient(DEFAULT_BASE_URL,
				new RateLimiter(Clock.SYSTEM, REQUESTS_PER_WINDOW, WINDOW_MILLIS));
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey == null || apiKey.trim().isEmpty() ? null : apiKey.trim();
	}

	public boolean hasApiKey() {
		return apiKey != null;
	}

	public RateLimiter getLimiter() {
		return limiter;
	}

	/**
	 * Fetches one player, blocking. Call it off the render thread — every caller in this mod goes
	 * through {@link StatsService}, which owns the pool that does.
	 */
	@Override
	public PlayerStats fetch(UUID uuid, String observedName) {
		String key = apiKey;
		if (key == null) {
			throw new HypixelApiException("No Hypixel API key set", 403);
		}

		if (!limiter.tryAcquire()) {
			throw new RateLimitedException("Local rate limit reached, waiting for the window");
		}

		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(
					baseUrl + "/v2/player?uuid=" + uuid.toString().replace("-", "")).openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("API-Key", key);
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("User-Agent", "HypixelScout");
			connection.setConnectTimeout(TIMEOUT_MILLIS);
			connection.setReadTimeout(TIMEOUT_MILLIS);

			int status = connection.getResponseCode();
			observeRateLimit(connection);
			// Past 399 the body is only on the error stream; asking for the normal one throws and
			// would replace Hypixel's explanation with an IOException.
			InputStream stream = status >= 400
					? connection.getErrorStream() : connection.getInputStream();
			JsonObject body = read(stream);

			if (status >= 400) {
				String cause = body != null && body.has("cause") && !body.get("cause").isJsonNull()
						? body.get("cause").getAsString() : "HTTP " + status;
				throw new HypixelApiException(cause, status);
			}

			return BedwarsStatsParser.parse(body, observedName, uuid);
		} catch (IOException e) {
			throw new HypixelApiException("Could not reach Hypixel: " + e.getMessage(),
					HypixelApiException.NO_RESPONSE);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/**
	 * Hands Hypixel's own count to the limiter. Sent with every answer, a 429 included, and the only
	 * source for a production key's larger budget or for requests another program made on the key.
	 */
	private void observeRateLimit(HttpURLConnection connection) {
		int reportedLimit = header(connection, "RateLimit-Limit");
		int reportedRemaining = header(connection, "RateLimit-Remaining");
		int reset = header(connection, "RateLimit-Reset");

		if (reportedLimit >= 0 || reportedRemaining >= 0) {
			limiter.observe(reportedLimit, reportedRemaining, reset);
		}
	}

	/** A numeric header, or {@code -1} when it is missing or not a number. */
	private static int header(HttpURLConnection connection, String name) {
		String value = connection.getHeaderField(name);
		if (value == null) {
			return -1;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static JsonObject read(InputStream stream) throws IOException {
		if (stream == null) {
			return null;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
		try {
			StringBuilder body = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				body.append(line);
			}

			return new JsonParser().parse(body.toString()).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Hypixel sent something that is not JSON", e);
		} finally {
			reader.close();
		}
	}
}
