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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MojangClientTest {
	private HttpServer server;
	private final AtomicInteger calls = new AtomicInteger();
	private volatile int status = 200;
	private volatile String body = "";

	@BeforeEach
	void startStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/users/profiles/minecraft", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {
				calls.incrementAndGet();

				byte[] payload = body.getBytes(Charset.forName("UTF-8"));
				// A 204 must carry no body at all, or the exchange refuses to send it.
				exchange.sendResponseHeaders(status, status == 204 ? -1 : payload.length);
				if (status != 204) {
					OutputStream out = exchange.getResponseBody();
					out.write(payload);
					out.close();
				} else {
					exchange.close();
				}
			}
		});
		server.start();
	}

	@AfterEach
	void stopStub() {
		server.stop(0);
	}

	private MojangClient client() {
		return new MojangClient("http://127.0.0.1:" + server.getAddress().getPort());
	}

	@Test
	void putsTheDashesBackIntoTheUuid() {
		assertEquals(UUID.fromString("b876ec32-e396-476b-a115-8438d83c67d4"),
				MojangClient.fromUndashed("b876ec32e396476ba1158438d83c67d4"));
	}

	@Test
	void resolvesAName() {
		body = "{\"id\":\"b876ec32e396476ba1158438d83c67d4\",\"name\":\"Technoblade\"}";

		assertEquals(UUID.fromString("b876ec32-e396-476b-a115-8438d83c67d4"),
				client().uuidOf("Technoblade"));
	}

	@Test
	void answersNullForANameNobodyHas() {
		status = 204;

		assertNull(client().uuidOf("ThisNameIsFree"));
	}

	@Test
	void asksMojangOnlyOncePerName() {
		body = "{\"id\":\"b876ec32e396476ba1158438d83c67d4\",\"name\":\"Technoblade\"}";

		MojangClient client = client();
		client.uuidOf("Technoblade");
		client.uuidOf("technoblade");

		assertEquals(1, calls.get(), "Mojang rate limits this endpoint hard");
	}

	@Test
	void reportsAMojangThrottleAsTemporary() {
		status = 429;
		body = "{\"error\":\"TooManyRequests\"}";

		assertThrows(RateLimitedException.class, () -> client().uuidOf("Technoblade"));
	}
}
