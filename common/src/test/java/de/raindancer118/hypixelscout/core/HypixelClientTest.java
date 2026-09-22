package de.raindancer118.hypixelscout.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The client is pointed at a local stub rather than at Hypixel: the error paths that matter most —
 * a rejected key and a throttle — cannot be provoked against the real API without breaking a key.
 */
class HypixelClientTest {
	private HttpServer server;
	private final AtomicReference<String> sentKey = new AtomicReference<String>();
	private final AtomicReference<String> response = new AtomicReference<String>();
	private final AtomicReference<Integer> status = new AtomicReference<Integer>(200);

	@BeforeEach
	void startStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v2/player", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				sentKey.set(exchange.getRequestHeaders().getFirst("API-Key"));

				byte[] body = response.get().getBytes(Charset.forName("UTF-8"));
				exchange.sendResponseHeaders(status.get().intValue(), body.length);

				OutputStream out = exchange.getResponseBody();
				out.write(body);
				out.close();
			}
		});
		server.start();
	}

	@AfterEach
	void stopStub() {
		server.stop(0);
	}

	private HypixelClient client() {
		return new HypixelClient("http://127.0.0.1:" + server.getAddress().getPort(),
				new RateLimiter(Clock.SYSTEM, 300, 300_000L));
	}

	@Test
	void sendsTheKeyAsAHeaderAndParsesTheProfile() {
		response.set("{\"success\":true,\"player\":{\"displayname\":\"Technoblade\","
				+ "\"achievements\":{\"bedwars_level\":412}}}");

		HypixelClient client = client();
		client.setApiKey("secret-key");
		PlayerStats stats = client.fetch(UUID.randomUUID(), "Technoblade");

		assertEquals("secret-key", sentKey.get(), "the key never belongs in the query string");
		assertEquals(412, stats.getStars());
	}

	@Test
	void refusesToCallAtAllWithoutAKey() {
		HypixelClient client = client();

		HypixelApiException thrown = assertThrows(HypixelApiException.class,
				() -> client.fetch(UUID.randomUUID(), "Anyone"));

		assertTrue(thrown.isKeyProblem());
		assertNull(sentKey.get(), "no request should have left at all");
	}

	@Test
	void reportsARejectedKeyAsSuch() {
		status.set(Integer.valueOf(403));
		response.set("{\"success\":false,\"cause\":\"Invalid API key\"}");

		HypixelClient client = client();
		client.setApiKey("wrong");

		HypixelApiException thrown = assertThrows(HypixelApiException.class,
				() -> client.fetch(UUID.randomUUID(), "Anyone"));

		assertTrue(thrown.isKeyProblem());
		assertEquals(403, thrown.getStatusCode());
	}

	@Test
	void readsTheErrorBodyOfAThrottle() {
		// A 429 body still carries the cause, and HttpURLConnection only offers it on the error
		// stream — reading the normal one there throws and would hide what happened.
		status.set(Integer.valueOf(429));
		response.set("{\"success\":false,\"cause\":\"Key throttle\"}");

		HypixelClient client = client();
		client.setApiKey("fine");

		HypixelApiException thrown = assertThrows(HypixelApiException.class,
				() -> client.fetch(UUID.randomUUID(), "Anyone"));

		assertEquals(429, thrown.getStatusCode());
		assertTrue(thrown.getMessage().contains("throttle"));
	}

	@Test
	void stopsBeforeSpendingARequestItDoesNotHave() {
		response.set("{\"success\":true,\"player\":null}");

		RateLimiter spent = new RateLimiter(Clock.SYSTEM, 1, 300_000L);
		HypixelClient client = new HypixelClient(
				"http://127.0.0.1:" + server.getAddress().getPort(), spent);
		client.setApiKey("fine");

		client.fetch(UUID.randomUUID(), "First");

		assertThrows(RateLimitedException.class,
				() -> client.fetch(UUID.randomUUID(), "Second"));
	}

	@Test
	void canBePointedSomewhereElseAfterItWasBuilt() {
		response.set("{\"success\":true,\"player\":{\"displayname\":\"Technoblade\"}}");

		HypixelClient client = HypixelClient.live();
		client.setApiKey("secret-key");
		client.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

		assertEquals("Technoblade", client.fetch(UUID.randomUUID(), "Technoblade").getName());
	}
}
