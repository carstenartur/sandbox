/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeminiClientTransportTest {

	private static final Response SUCCESS = new Response(200,
			"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[{\\\"relevant\\\":false}]\"}]}}]}");

	@Test
	void retriesTimeoutAndReturnsTheEvaluation() throws IOException {
		ScriptedHttpClient transport = new ScriptedHttpClient(
				new HttpTimeoutException("request timed out"), SUCCESS);
		try (GeminiClient client = newClient(transport)) {
			var result = evaluate(client);
			assertEquals(List.of("hash"), result.stream().map(CommitEvaluation::commitHash).toList());
			assertEquals(2, transport.requests.size());
			assertEquals(List.of(5000L), transport.waits);
			assertEquals(transport.requests.get(0).uri(), transport.requests.get(1).uri());
			assertFalse(client.isApiUnavailable());
		}
	}

	@Test
	void retriesConnectionResetAndReturnsTheEvaluation() throws IOException {
		ScriptedHttpClient transport = new ScriptedHttpClient(new SocketException("Connection reset"), SUCCESS);
		try (GeminiClient client = newClient(transport)) {
			assertEquals(1, evaluate(client).size());
			assertEquals(2, transport.requests.size());
		}
	}

	@Test
	void retriesServiceOverloadAndReturnsTheEvaluation() throws IOException {
		ScriptedHttpClient transport = new ScriptedHttpClient(
				new Response(503, "Service temporarily overloaded"), SUCCESS);
		try (GeminiClient client = newClient(transport)) {
			var result = evaluate(client);
			assertEquals(List.of("hash"), result.stream().map(CommitEvaluation::commitHash).toList());
			assertEquals(2, transport.requests.size());
			assertEquals(List.of(5000L), transport.waits);
			assertFalse(client.isApiUnavailable());
		}
	}

	@Test
	void exhaustedServiceOverloadRetriesReportTheLastFailure() {
		Response unavailable = new Response(503, "Service temporarily overloaded");
		ScriptedHttpClient transport = new ScriptedHttpClient(
				unavailable, unavailable, unavailable, unavailable, new Response(503, "Still overloaded"));
		try (GeminiClient client = newClient(transport)) {
			IOException failure = assertThrows(IOException.class, () -> evaluate(client));
			assertTrue(failure.getMessage().contains("503"));
			assertTrue(failure.getMessage().contains("Still overloaded"));
			assertEquals(5, transport.requests.size(), "Retries must stop at the existing five-attempt bound");
			assertEquals(List.of(5000L, 10000L, 20000L, 40000L), transport.waits);
		}
	}

	@Test
	void serviceOverloadDoesNotRestartAnExhaustedRetryBudget() {
		ScriptedHttpClient transport = new ScriptedHttpClient(new Response(503, "Service overloaded"));
		try (GeminiClient client = newClient(transport)) {
			client.setMaxFailureDuration(Duration.ofSeconds(-1));
			assertThrows(IOException.class, () -> evaluate(client));
			assertEquals(1, transport.requests.size());
			assertTrue(transport.waits.isEmpty());
		}
	}

	@Test
	void interruptionDuringServiceOverloadBackoffStopsImmediately() {
		ScriptedHttpClient transport = new ScriptedHttpClient(new Response(503, "Service overloaded"));
		try (GeminiClient client = new GeminiClient("test-key", transport, "gemini-2.5-flash", millis -> {
			throw new InterruptedException("cancelled during backoff");
		})) {
			IOException failure = assertThrows(IOException.class, () -> evaluate(client));
			assertInstanceOf(InterruptedException.class, failure.getCause());
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(1, transport.requests.size());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	@Timeout(5)
	void exhaustedTimeoutRetriesThrowTheLastFailure() {
		HttpTimeoutException failure = new HttpTimeoutException("request timed out");
		ScriptedHttpClient transport = new ScriptedHttpClient(failure, failure, failure, failure, failure);
		try (GeminiClient client = newClient(transport)) {
			assertSame(failure, assertThrows(HttpTimeoutException.class, () -> evaluate(client)));
			assertEquals(5, transport.requests.size(), "Retries must stop at the existing five-attempt bound");
			assertEquals(List.of(5000L, 10000L, 20000L, 40000L), transport.waits,
					"Back off only before another attempt, without adding real test delays");
		}
	}

	@Test
	void permanentTransportFailureIsNotRetried() {
		SSLHandshakeException failure = new SSLHandshakeException("certificate rejected");
		ScriptedHttpClient transport = new ScriptedHttpClient(failure);
		try (GeminiClient client = newClient(transport)) {
			assertSame(failure, assertThrows(IOException.class, () -> evaluate(client)));
			assertEquals(1, transport.requests.size());
			assertTrue(transport.waits.isEmpty());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 401, 403 })
	void permanentHttpFailureIsReportedWithoutRetry(int status) {
		ScriptedHttpClient transport = new ScriptedHttpClient(new Response(status, "permission denied"));
		try (GeminiClient client = newClient(transport)) {
			IOException failure = assertThrows(IOException.class, () -> evaluate(client));
			assertTrue(failure.getMessage().contains(Integer.toString(status)));
			assertEquals(1, transport.requests.size());
		}
	}

	@Test
	void interruptionStopsImmediatelyAndPreservesInterruptFlag() {
		ScriptedHttpClient transport = new ScriptedHttpClient(new InterruptedException("cancelled"));
		try (GeminiClient client = newClient(transport)) {
			IOException failure = assertThrows(IOException.class, () -> evaluate(client));
			assertInstanceOf(InterruptedException.class, failure.getCause());
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(1, transport.requests.size());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void interruptionDuringRetryWaitStopsImmediately() {
		ScriptedHttpClient transport = new ScriptedHttpClient(new HttpTimeoutException("request timed out"));
		try (GeminiClient client = new GeminiClient("test-key", transport, "gemini-2.5-flash", millis -> {
			throw new InterruptedException("cancelled during backoff");
		})) {
			IOException failure = assertThrows(IOException.class, () -> evaluate(client));
			assertInstanceOf(InterruptedException.class, failure.getCause());
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(1, transport.requests.size());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void rateLimitResponseStillRetriesUsingRetryAfter() throws IOException {
		ScriptedHttpClient transport = new ScriptedHttpClient(new Response(429, "rate limited"), SUCCESS);
		try (GeminiClient client = newClient(transport)) {
			assertEquals(1, evaluate(client).size());
			assertEquals(2, transport.requests.size());
			assertEquals(List.of(0L), transport.waits);
		}
	}

	private static GeminiClient newClient(ScriptedHttpClient transport) {
		return new GeminiClient("test-key", transport, "gemini-2.5-flash", transport.waits::add);
	}

	private static List<CommitEvaluation> evaluate(GeminiClient client) throws IOException {
		return client.evaluateBatch("prompt", List.of("hash"), List.of("message"), "repository");
	}

	private record Response(int statusCode, String body) implements HttpResponse<String> {
		@Override public HttpRequest request() { return null; }
		@Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
		@Override public HttpHeaders headers() {
			return HttpHeaders.of(Map.of("Retry-After", List.of("0")), (name, value) -> true);
		}
		@Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
		@Override public URI uri() { return URI.create("https://example.invalid"); }
		@Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
	}

	/** Scripted transport only; parsing, retrying and interruption use the real client. */
	private static final class ScriptedHttpClient extends HttpClient {
		private final List<Object> outcomes;
		private final List<HttpRequest> requests = new ArrayList<>();
		private final List<Long> waits = new ArrayList<>();

		ScriptedHttpClient(Object... outcomes) {
			this.outcomes = List.of(outcomes);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
				throws IOException, InterruptedException {
			Object outcome = outcomes.get(requests.size());
			requests.add(request);
			if (outcome instanceof IOException failure) {
				throw failure;
			}
			if (outcome instanceof InterruptedException failure) {
				throw failure;
			}
			return (HttpResponse<T>) outcome;
		}

		@Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
		@Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
		@Override public Redirect followRedirects() { return Redirect.NEVER; }
		@Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
		@Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
		@Override public SSLParameters sslParameters() { return new SSLParameters(); }
		@Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
		@Override public Version version() { return Version.HTTP_1_1; }
		@Override public Optional<Executor> executor() { return Optional.empty(); }
		@Override public void close() { }
		@Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
				HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
		@Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
				HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
			throw new UnsupportedOperationException();
		}
	}
}
