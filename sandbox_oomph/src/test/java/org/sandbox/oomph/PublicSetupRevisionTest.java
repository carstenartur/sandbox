package org.sandbox.oomph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

class PublicSetupRevisionTest {
    private static final byte[] CURRENT = "<setupTask id=\"sdk\"/>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] STALE = "<setupTask id=\"target\"/>".getBytes(StandardCharsets.UTF_8);

    private final AtomicInteger mainRequests = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pinned", exchange -> reply(exchange, 200, CURRENT));
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void waitsForCanonicalSetupToCatchUpToThePinnedRevision() throws Exception {
        server.createContext("/main", exchange -> reply(exchange, 200,
                mainRequests.incrementAndGet() == 1 ? STALE : CURRENT));

        assertArrayEquals(CURRENT, awaitPublishedSetup());
        assertEquals(2, mainRequests.get());
    }

    @Test
    void rejectsAStreamThatStaysStale() {
        server.createContext("/main", exchange -> {
            mainRequests.incrementAndGet();
            reply(exchange, 200, STALE);
        });

        assertThrows(AssertionFailedError.class, this::awaitPublishedSetup);
        assertEquals(2, mainRequests.get());
    }

    @Test
    void rejectsAnErrorResponseEvenIfItsBodyMatches() {
        server.createContext("/main", exchange -> {
            mainRequests.incrementAndGet();
            reply(exchange, 503, CURRENT);
        });

        assertThrows(AssertionFailedError.class, this::awaitPublishedSetup);
        assertEquals(1, mainRequests.get());
    }

    private byte[] awaitPublishedSetup() throws Exception {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try (var http = HttpClient.newHttpClient()) {
            return PublicEppInstallerTest.awaitPublishedSetup(
                    http, endpoint.resolve("/pinned"), endpoint.resolve("/main"), 2, Duration.ZERO);
        }
    }

    private static void reply(HttpExchange exchange, int status, byte[] body) throws IOException {
        try (exchange) {
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
