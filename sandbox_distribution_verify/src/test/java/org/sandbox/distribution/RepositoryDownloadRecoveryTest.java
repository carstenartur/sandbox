/* SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpServer;

class RepositoryDownloadRecoveryTest {
    @Test
    void recoversMetadataAfterTemporaryServiceFailure() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = metadataServer(requests, 503, false);
        try {
            assertEquals(Map.of("sandbox_feature.feature.group", "1.3.5"),
                    AggregateInstallationVerifier.featureVersions(repository(server)));
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 503})
    void metadataFailureRemainsFatalWithBoundedRequests(int status) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = metadataServer(requests, status, true);
        try {
            assertThrows(IOException.class, () -> AggregateInstallationVerifier.featureVersions(repository(server)));
            assertEquals(status == 404 ? 1 : 3, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesConnectionTimeoutAndKeepsAttemptNumbersForEvidence() throws Exception {
        List<Integer> attempts = new ArrayList<>();
        assertEquals("downloaded", AggregateInstallationVerifier.downloadWithRetry(attempt -> {
            attempts.add(attempt);
            if (attempt < 2) throw new ConnectException("Operation timed out (connect failed)");
            return "downloaded";
        }));
        assertEquals(List.of(1, 2), attempts);
    }

    @Test
    void retriesCollectTransportFailureButPreservesPermanentProvisioningFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        int exitCode = AggregateInstallationVerifier.downloadWithRetry(attempt -> {
            calls.incrementAndGet();
            AggregateInstallationVerifier.requireSuccessfulProcess(attempt == 1 ? 13 : 0,
                    Set.of(0), "org.eclipse.equinox.p2.director", "released-aggregate",
                    "phase=org.eclipse.equinox.internal.p2.engine.phases.Collect, operand=, action=).\n"
                    + "HttpComponents connection error response code 503.");
            return 0;
        });
        assertEquals(0, exitCode);
        assertEquals(2, calls.get());

        AtomicInteger permanentCalls = new AtomicInteger();
        IOException failure = assertThrows(IOException.class, () -> AggregateInstallationVerifier.downloadWithRetry(attempt -> {
            permanentCalls.incrementAndGet();
            AggregateInstallationVerifier.requireSuccessfulProcess(13, Set.of(0),
                    "org.eclipse.equinox.p2.director", "released-aggregate", "Cannot satisfy dependency sandbox_feature");
            return 0;
        }));
        assertTrue(failure.getMessage().contains("released-aggregate"));
        assertEquals(1, permanentCalls.get());
    }

    @Test
    void neverRetriesOtherApplicationsOrInstallationMutationFailures() {
        String transport = "HttpComponents connection error response code 503.";
        for (var scenario : Map.of("org.sandbox.cleanup", "Collect", "org.eclipse.equinox.p2.director", "Install").entrySet()) {
            AtomicInteger calls = new AtomicInteger();
            assertThrows(IOException.class, () -> AggregateInstallationVerifier.downloadWithRetry(attempt -> {
                calls.incrementAndGet();
                AggregateInstallationVerifier.requireSuccessfulProcess(13, Set.of(0), scenario.getKey(), "stage",
                        "phase=org.eclipse.equinox.internal.p2.engine.phases." + scenario.getValue() + ", operand=, action=).\n" + transport);
                return 0;
            }));
            assertEquals(1, calls.get());
        }
    }

    private static HttpServer metadataServer(AtomicInteger requests, int status, boolean permanent) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            archive.putNextEntry(new ZipEntry("content.xml"));
            archive.write("<repository><units><unit id='sandbox_feature.feature.group' version='1.3.5'/></units></repository>"
                    .getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/content.jar", exchange -> {
            try (exchange) {
                if (requests.incrementAndGet() == 1 || permanent) exchange.sendResponseHeaders(status, -1);
                else {
                    exchange.sendResponseHeaders(200, bytes.size());
                    bytes.writeTo(exchange.getResponseBody());
                }
            }
        });
        server.start();
        return server;
    }

    private static URI repository(HttpServer server) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + '/');
    }
}
