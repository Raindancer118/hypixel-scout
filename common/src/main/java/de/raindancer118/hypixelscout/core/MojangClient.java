package de.raindancer118.hypixelscout.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * Name to UUID, for looking up somebody who is not in your game.
 *
 * <p>Players in the lobby never come through here: their UUID is already in the tab list. This is
 * only for {@code /scout <name>} typed about a stranger, which is also why the result is cached —
 * Mojang rate limits this endpoint per IP and is unforgiving about it.
 */
public final class MojangClient {
	public static final String DEFAULT_BASE_URL = "https://api.mojang.com";

	private static final int TIMEOUT_MILLIS = 8000;

	private volatile String baseUrl;
	private final java.util.Map<String, UUID> resolved =
			new java.util.concurrent.ConcurrentHashMap<String, UUID>();

	public MojangClient(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/**
	 * Points every later request somewhere else. The client game test serves recorded answers from
	 * a local stub this way; the mod itself never calls it.
	 */
	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public static MojangClient live() {
		return new MojangClient(DEFAULT_BASE_URL);
	}

	/** Blocks. Returns null for a name no account has ever had. */
	public UUID uuidOf(String name) {
		String key = name.toLowerCase(java.util.Locale.ROOT);
		UUID known = resolved.get(key);
		if (known != null) {
			return known;
		}

		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(
					baseUrl + "/users/profiles/minecraft/" + name).openConnection();
			connection.setConnectTimeout(TIMEOUT_MILLIS);
			connection.setReadTimeout(TIMEOUT_MILLIS);
			connection.setRequestProperty("User-Agent", "HypixelScout");

			int status = connection.getResponseCode();
			// Mojang answers an unknown name with 204 and an empty body, and 404 in its newer
			// deployments. Neither is an error worth reporting: the name simply does not exist.
			if (status == 204 || status == 404) {
				return null;
			}
			if (status == 429) {
				throw new RateLimitedException("Mojang is rate limiting name lookups");
			}
			if (status >= 400) {
				throw new HypixelApiException("Mojang answered HTTP " + status, status);
			}

			JsonObject body = read(connection);
			if (body == null || !body.has("id")) {
				return null;
			}

			UUID uuid = fromUndashed(body.get("id").getAsString());
			resolved.put(key, uuid);

			return uuid;
		} catch (IOException e) {
			throw new HypixelApiException("Could not reach Mojang: " + e.getMessage(),
					HypixelApiException.NO_RESPONSE);
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/** Mojang returns the UUID without its dashes, and {@code UUID.fromString} insists on them. */
	public static UUID fromUndashed(String id) {
		if (id.length() != 32) {
			return UUID.fromString(id);
		}

		return UUID.fromString(id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
				+ id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20));
	}

	private static JsonObject read(HttpURLConnection connection) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(connection.getInputStream(), "UTF-8"));
		try {
			StringBuilder body = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				body.append(line);
			}

			if (body.length() == 0) {
				return null;
			}

			return JsonParser.parseString(body.toString()).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Mojang sent something that is not JSON", e);
		} finally {
			reader.close();
		}
	}
}
