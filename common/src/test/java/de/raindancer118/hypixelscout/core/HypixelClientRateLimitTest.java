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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hypixel sends its own count with every answer; the client has to hand it to the limiter. */
class HypixelClientRateLimitTest {
	private HttpServer server;
	private final AtomicInteger status = new AtomicInteger(200);

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v2/player", exchange -> {
			exchange.getResponseHeaders().add("RateLimit-Limit", "600");
			exchange.getResponseHeaders().add("RateLimit-Remaining", status.get() == 429 ? "0" : "587");
			exchange.getResponseHeaders().add("RateLimit-Reset", "73");
			byte[] body = (status.get() == 429
					? "{\"success\":false,\"cause\":\"Key throttle\"}"
					: "{\"success\":true,\"player\":{\"displayname\":\"Someone\"}}").getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status.get(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private final AtomicLongClock clock = new AtomicLongClock();

	private HypixelClient client() {
		HypixelClient client = new HypixelClient("http://127.0.0.1:" + server.getAddress().getPort(),
				new RateLimiter(clock, 300, 300_000L));
		client.setApiKey("key");
		return client;
	}

	@Test
	void aProductionKeysBudgetIsTakenFromTheAnswer() {
		HypixelClient client = client();
		client.fetch(UUID.randomUUID(), "Someone");

		assertThat(client.getLimiter().limit()).isEqualTo(600);
		assertThat(client.getLimiter().remaining()).isEqualTo(587);
		assertThat(client.getLimiter().millisUntilReset()).isEqualTo(73_000L);
	}

	@Test
	void aThrottleStopsFurtherRequestsUntilTheReset() {
		HypixelClient client = client();
		status.set(429);

		assertThatThrownBy(() -> client.fetch(UUID.randomUUID(), "Someone"))
				.isInstanceOf(HypixelApiException.class);
		assertThat(client.getLimiter().tryAcquire()).isFalse();

		clock.advance(73_001L);
		assertThat(client.getLimiter().tryAcquire()).isTrue();
	}

	/** A clock the test moves by hand. */
	static final class AtomicLongClock implements Clock {
		private final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();

		@Override
		public long millis() {
			return now.get();
		}

		void advance(long millis) {
			now.addAndGet(millis);
		}
	}
}
