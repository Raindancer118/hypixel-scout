package de.raindancer118.hypixelscout.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KeyCheckTest {
	private static final UUID SELF = UUID.fromString("f7c77d99-9f15-4a66-a87d-c4a51ef30d19");

	private HttpServer server;
	private final AtomicInteger status = new AtomicInteger(200);
	private final AtomicReference<String> body = new AtomicReference<>("");

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v2/player", exchange -> {
			byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private HypixelClient client(String key) {
		HypixelClient client = new HypixelClient("http://127.0.0.1:" + server.getAddress().getPort(),
				new RateLimiter(Clock.SYSTEM, 300, 300_000L));
		client.setApiKey(key);
		return client;
	}

	@Test
	void noKeyIsSaidWithoutAskingAnybody() {
		assertThat(KeyCheck.run(client(""), SELF).outcome()).isEqualTo(KeyCheck.Outcome.NO_KEY);
	}

	@Test
	void anAcceptedKeyNamesTheAccountItLookedUp() {
		body.set("{\"success\":true,\"player\":{\"displayname\":\"Raindancer118\","
				+ "\"stats\":{\"Bedwars\":{}}}}");

		KeyCheck.Result result = KeyCheck.run(client("key"), SELF);

		assertThat(result.outcome()).isEqualTo(KeyCheck.Outcome.OK);
		assertThat(result.detail()).isEqualTo("Raindancer118");
	}

	@Test
	void aRejectedKeyIsTheKeysFault() {
		status.set(403);
		body.set("{\"success\":false,\"cause\":\"Invalid API key\"}");

		KeyCheck.Result result = KeyCheck.run(client("key"), SELF);

		assertThat(result.outcome()).isEqualTo(KeyCheck.Outcome.INVALID_KEY);
		assertThat(result.detail()).isEqualTo("Invalid API key");
	}

	@Test
	void aThrottleIsNotABadKey() {
		status.set(429);
		body.set("{\"success\":false,\"cause\":\"Key throttle\"}");

		assertThat(KeyCheck.run(client("key"), SELF).outcome())
				.isEqualTo(KeyCheck.Outcome.RATE_LIMITED);
	}

	@Test
	void anUnreachableServerIsSaidAsSuch() {
		server.stop(0);

		assertThat(KeyCheck.run(client("key"), SELF).outcome())
				.isEqualTo(KeyCheck.Outcome.UNREACHABLE);
	}

	@Test
	void anyOtherRefusalKeepsHypixelsReason() {
		status.set(503);
		body.set("{\"success\":false,\"cause\":\"Maintenance\"}");

		KeyCheck.Result result = KeyCheck.run(client("key"), SELF);

		assertThat(result.outcome()).isEqualTo(KeyCheck.Outcome.FAILED);
		assertThat(result.detail()).isEqualTo("Maintenance");
	}
}
