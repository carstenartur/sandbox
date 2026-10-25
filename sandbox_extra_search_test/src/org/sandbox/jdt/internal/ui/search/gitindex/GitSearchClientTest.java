/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class GitSearchClientTest {

	private HttpServer server;
	private int status;
	private String body;
	private final AtomicReference<String> request= new AtomicReference<>();
	private final AtomicReference<String> authorization= new AtomicReference<>();
	private final AtomicReference<String> method= new AtomicReference<>();

	@BeforeEach void start() throws Exception {
		status= 200;
		body= "{\"results\":[]}";
		server= HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			request.set(exchange.getRequestURI().toString());
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			method.set(exchange.getRequestMethod());
			byte[] bytes= body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (var output= exchange.getResponseBody()) { output.write(bytes); }
		});
		server.start();
		SemanticSearchClient.initialize("http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterEach void stop() {
		server.stop(0);
		SemanticSearchClient.initialize(null);
	}

	@Test void blankRepositoryIsRejectedBeforeSending() {
		assertThrows(IllegalArgumentException.class,
				() -> SemanticSearchClient.getInstance().searchCommitMessages(" ", "fix", 20));
		assertNull(request.get());
	}

	@Test void httpFailureIsNotAnEmptySearch() {
		status= 400;
		body= "{\"error\":\"Parameter 'repo' is required\"}";
		var failure= assertThrows(RuntimeException.class,
				() -> SemanticSearchClient.getInstance().searchCommitMessages("demo", "fix", 20));
		assertTrue(failure.getMessage().contains("400"));
	}

	@Test void malformedResponseIsNotAnEmptySearch() {
		body= "<html>proxy failure</html>";
		assertThrows(RuntimeException.class,
				() -> SemanticSearchClient.getInstance().searchCommitMessages("demo", "fix", 20));
	}

	@Test void commitResultsUseTheServersCommitFields() {
		body= "{\"results\":[{\"objectId\":\"abc123\",\"message\":\"Fix \\u00e4 and \\nnewline\",\"author\":\"Alice\"}]}";
		var hits= SemanticSearchClient.getInstance().searchCommitMessages("team demo", "fix & test", 20);
		assertEquals("abc123", hits.getFirst().getCommitHash());
		assertEquals("Fix ä and \nnewline", hits.getFirst().getContent());
		assertEquals("/api/search/commits?repo=team+demo&q=fix+%26+test&limit=20", request.get());
	}

	@Test void successfulEmptySearchRemainsEmpty() {
		assertTrue(SemanticSearchClient.getInstance().searchCommitMessages("demo", "missing", 20).isEmpty());
	}

	@Test void rebuildUsesAuthenticatedRepositoryScopedPost() {
		body= "{\"indexedCommits\":7}";
		assertEquals("Index rebuilt: 7 commits.", SemanticSearchClient.getInstance().rebuild("demo", "test-token"));
		assertEquals("/api/admin/rebuild?repo=demo", request.get());
		assertEquals("POST", method.get());
		assertEquals("Bearer test-token", authorization.get());
	}

	@Test void rejectedRebuildDoesNotReportSuccess() {
		status= 401;
		var failure= assertThrows(RuntimeException.class,
				() -> SemanticSearchClient.getInstance().rebuild("demo", "wrong-token"));
		assertTrue(failure.getMessage().contains("401"));
		assertFalse(failure.getMessage().contains("wrong-token"));
	}

	@Test void historyPreservesExactPathAndBlobIdentity() {
		body= "{\"results\":[{\"commitObjectId\":\"c1\",\"blobObjectId\":\"b1\",\"filePath\":\" src/Clock.java \",\"commitTime\":\"2026-10-25T12:00:00Z\"}]}";
		var row= SemanticSearchClient.getInstance().getFileHistory("demo", " src/Clock.java ").getFirst();
		assertEquals("b1", row.getBlobId());
		assertEquals(" src/Clock.java ", row.getPath());
		assertEquals("2026-10-25T12:00:00Z", row.getCommitTime());
		assertTrue(request.get().contains("path=+src%2FClock.java+"));
	}
}
