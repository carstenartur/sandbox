/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.server.JGitServerApplication;
import org.junit.Test;
import com.google.gson.JsonParser;

/** Existing-only readers and physical statistics use released Core with real H2 data. */
public class ReleasedRepositoryReadH2Test {

	@Test
	public void readsExistingIdentityWithoutCreatingOrPublishingRefs() throws Exception {
		String database = UUID.randomUUID().toString();
		ObjectId blob;
		try (var context = new ExternalServerPersistenceContext(properties(database, "create"))) {
			var repository = context.repositoryService().openOrCreate("example.git.git");
			try (var inserter = repository.newObjectInserter()) {
				blob = inserter.insert(Constants.OBJ_BLOB, new byte[] {1, 2, 3});
				inserter.flush();
			}
			var ref = repository.updateRef("refs/tags/content");
			ref.setNewObjectId(blob);
			assertEquals(RefUpdate.Result.NEW, ref.update());
		}
		try (var context = new ExternalServerPersistenceContext(properties(database, "validate"))) {
			var service = context.repositoryService();
			assertThrows(java.io.FileNotFoundException.class,
					() -> service.withExistingRepository("missing", repository -> null));
			assertThrows(java.io.FileNotFoundException.class,
					() -> service.withExistingRepository("example.git", repository -> null));
			assertEquals(blob, service.withExistingRepository("example.git.git", repository -> {
				assertTrue(service.isOpen("example.git.git"));
				assertThrows(IllegalStateException.class, () -> service.delete("example.git.git"));
				var denied = repository.updateRef("refs/tags/forbidden");
				denied.setNewObjectId(blob);
				assertEquals(RefUpdate.Result.REJECTED_OTHER_REASON, denied.update());
				assertNull(repository.exactRef("refs/tags/forbidden"));
				return repository.resolve("refs/tags/content");
			}));
			assertFalse(service.isOpen("example.git.git"));
			assertThrows(IOException.class, () -> service.withExistingRepository("example.git.git", repository -> {
				throw new IOException("failed read");
			}));
			assertFalse(service.isOpen("example.git.git"));
			service.delete("example.git.git");
			assertThrows(java.io.FileNotFoundException.class,
					() -> service.withExistingRepository("example.git.git", repository -> null));
		}
	}

	@Test
	public void shutdownWaitsForReadersAndRejectsNewOperations() throws Exception {
		String database = UUID.randomUUID().toString();
		try (var context = new ExternalServerPersistenceContext(properties(database, "create"))) {
			context.repositoryService().openOrCreate("example");
		}
		var context = new ExternalServerPersistenceContext(properties(database, "validate"));
		var entered = new CountDownLatch(1);
		var resume = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var reading = executor.submit(() -> context.repositoryService().withExistingRepository("example", repository -> {
				entered.countDown();
				try {
					assertTrue(resume.await(10, TimeUnit.SECONDS));
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					throw new IOException(failure);
				}
				assertFalse(context.sessionFactory().isClosed());
				return repository.resolve("HEAD");
			}));
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			var closing = new CountDownLatch(1);
			var close = executor.submit(() -> { closing.countDown(); context.close(); });
			assertTrue(closing.await(10, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> close.get(200, TimeUnit.MILLISECONDS));
			assertThrows(IllegalStateException.class,
					() -> context.repositoryService().withExistingRepository("example", repository -> null));
			resume.countDown();
			reading.get(10, TimeUnit.SECONDS);
			close.get(10, TimeUnit.SECONDS);
			assertTrue(context.sessionFactory().isClosed());
		} finally {
			resume.countDown();
			context.close();
		}
	}

	@Test
	public void concurrentClosersWaitForEveryReader() throws Exception {
		String database = UUID.randomUUID().toString();
		try (var context = new ExternalServerPersistenceContext(properties(database, "create"))) {
			context.repositoryService().openOrCreate("example");
		}
		try (var context = new ExternalServerPersistenceContext(properties(database, "validate"));
				var executor = Executors.newFixedThreadPool(4)) {
			var service = context.repositoryService();
			var entered = new CountDownLatch(2);
			var firstResume = new CountDownLatch(1);
			var secondResume = new CountDownLatch(1);
			try {
				var first = executor.submit(() -> service.withExistingRepository("example", repository -> {
					awaitReader(entered, firstResume);
					return repository.resolve("HEAD");
				}));
				var second = executor.submit(() -> service.withExistingRepository("example", repository -> {
					awaitReader(entered, secondResume);
					assertFalse(context.sessionFactory().isClosed());
					return repository.resolve("HEAD");
				}));
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				var closing = new CountDownLatch(2);
				var closeA = executor.submit(() -> { closing.countDown(); service.close(); });
				var closeB = executor.submit(() -> { closing.countDown(); service.close(); });
				assertTrue(closing.await(10, TimeUnit.SECONDS));
				assertThrows(TimeoutException.class, () -> closeA.get(100, TimeUnit.MILLISECONDS));
				assertThrows(TimeoutException.class, () -> closeB.get(100, TimeUnit.MILLISECONDS));
				firstResume.countDown();
				first.get(10, TimeUnit.SECONDS);
				assertThrows(TimeoutException.class, () -> closeA.get(100, TimeUnit.MILLISECONDS));
				assertThrows(TimeoutException.class, () -> closeB.get(100, TimeUnit.MILLISECONDS));
				secondResume.countDown();
				second.get(10, TimeUnit.SECONDS);
				closeA.get(10, TimeUnit.SECONDS);
				closeB.get(10, TimeUnit.SECONDS);
			} finally {
				firstResume.countDown();
				secondResume.countDown();
			}
		}
	}

	private static void awaitReader(CountDownLatch entered, CountDownLatch resume) throws IOException {
		entered.countDown();
		try {
			assertTrue(resume.await(10, TimeUnit.SECONDS));
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IOException(failure);
		}
	}

	@Test
	public void analyticsCountsStoredUnreachableObjectsAndDoesNotCreateMissingRepositories() throws Exception {
		var context = new ExternalServerPersistenceContext(properties(UUID.randomUUID().toString(), "create"));
		var application = new JGitServerApplication();
		try {
			var repository = context.repositoryService().openOrCreate("example.git.git");
			try (var inserter = repository.newObjectInserter()) {
				inserter.insert(Constants.OBJ_BLOB, new byte[] {1});
				inserter.insert(Constants.OBJ_BLOB, new byte[] {2});
				inserter.flush();
			}
			assertNull(repository.resolve("HEAD"));
			var start = JGitServerApplication.class.getDeclaredMethod("start", ServerPersistenceContext.class,
					int.class, int.class, String.class, String.class, boolean.class);
			start.setAccessible(true);
			start.invoke(application, context, 0, 0, null, null, true);
			try (var http = HttpClient.newHttpClient()) {
				for (String prefix : List.of("/api", "/api/v1")) {
					var counts = get(http, application, prefix + "/analytics/objects?repo=example.git.git").getAsJsonObject("objectCounts");
					assertEquals(2, counts.get("blobs").getAsLong());
					assertEquals(0, counts.get("commits").getAsLong());
					var packs = get(http, application, prefix + "/analytics/packs?repo=example.git.git");
					assertTrue(packs.get("packCount").getAsLong() > 0);
					assertTrue(packs.get("totalSizeBytes").getAsLong() > 0);
					assertEquals(0, get(http, application, prefix + "/analytics/objects?repo=missing").getAsJsonObject("objectCounts").get("blobs").getAsLong());
					assertEquals(0, get(http, application, prefix + "/analytics/packs?repo=missing").get("packCount").getAsLong());
				}
			}
			assertThrows(java.io.FileNotFoundException.class,
					() -> context.repositoryService().withExistingRepository("missing", ignored -> null));
			assertNull(repository.resolve("HEAD"));
		} finally {
			application.stop();
		}
	}

	private static com.google.gson.JsonObject get(HttpClient http, JGitServerApplication application, String path) throws Exception {
		var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + application.getRestPort() + path))
				.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(response.body(), 200, response.statusCode());
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private static Properties properties(String database, String ddl) {
		var result = new Properties();
		result.setProperty("hibernate.connection.url", "jdbc:h2:mem:reader-" + database + ";DB_CLOSE_DELAY=-1");
		result.setProperty("hibernate.connection.username", "sa");
		result.setProperty("hibernate.connection.password", "");
		result.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
		result.setProperty("hibernate.hbm2ddl.auto", ddl);
		result.setProperty("hibernate.search.backend.directory.type", "local-heap");
		result.setProperty("hibernate.cache.use_second_level_cache", "false");
		result.setProperty("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
		return result;
	}
}
