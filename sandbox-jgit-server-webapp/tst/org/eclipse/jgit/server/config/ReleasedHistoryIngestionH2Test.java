/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.server.JGitServerApplication;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Published Core/Search, real Smart HTTP and persistence across server restart. */
public class ReleasedHistoryIngestionH2Test {

	@Rule
	public final TemporaryFolder temporary = new TemporaryFolder();
	private JGitServerApplication application;
	private ServerPersistenceContext context;

	@After
	public void close() throws Exception {
		if (application != null) {
			application.stop();
		} else if (context != null) {
			context.close();
		}
	}

	@Test
	public void indexesHttpPushesAndRetainsSearchAndGitAfterRestart() throws Exception {
		String database = "push-" + UUID.randomUUID();
		Path index = temporary.newFolder("index").toPath();
		start(properties(database, index, "create"));
		ObjectId first;
		ObjectId second;
		try (Git client = Git.init().setDirectory(temporary.newFolder("client")).setInitialBranch("main").call()) {
			first = client.commit().setMessage("Needle first").setAuthor("Author", "author@example.org")
					.setCommitter("Author", "author@example.org").setAllowEmpty(true).call().getId();
			push(client, "example.git", "refs/heads/main:refs/heads/main");
			assertEquals(1, context.historySearch().searchMessages("example", "needle", 0, 10).size());
			second = client.commit().setMessage("Needle second").setAuthor("Author", "author@example.org")
					.setCommitter("Author", "author@example.org").setAllowEmpty(true).call().getId();
			push(client, "example.git", "refs/heads/main:refs/heads/main");
			assertEquals(2, context.historySearch().searchMessages("example", "needle", 0, 10).size());
			client.tag().setName("release").setAnnotated(true).setMessage("Release")
					.setTagger(new org.eclipse.jgit.lib.PersonIdent("Author", "author@example.org")).call();
			push(client, "other.git", "refs/tags/release:refs/tags/release");
			assertEquals(2, context.historySearch().searchMessages("other", "needle", 0, 10).size());
		}
		application.stop();
		application = null;
		context = null;
		start(properties(database, index, "validate"));
		assertEquals(2, context.repositoryService().delete("other.git").projectionRows());
		assertTrue(context.historySearch().searchMessages("other", "needle", 0, 10).isEmpty());
		assertEquals(java.util.Set.of(first.name(), second.name()), context.historySearch()
				.searchMessages("example", "needle", 0, 10).stream().map(hit -> hit.objectId())
				.collect(java.util.stream.Collectors.toSet()));
		try (Git clone = Git.cloneRepository().setURI(uri("example.git")).setBranch("main")
				.setDirectory(temporary.newFolder("clone")).call()) {
			assertEquals(second, clone.getRepository().resolve("HEAD"));
			assertEquals(first, clone.getRepository().resolve("HEAD~1"));
		}
		assertTrue(context.historySearch().searchMessages("missing", "needle", 0, 10).isEmpty());
	}

	@Test
	public void indexingFailureKeepsAcceptedPushAndReportsDegradedHealth() throws Exception {
		Properties properties = properties("failure-" + UUID.randomUUID(), temporary.newFolder("failed-index").toPath(), "create");
		properties.setProperty("jgit.storage.hibernate.search.index_batch_size", "0");
		start(properties);
		try (Git client = Git.init().setDirectory(temporary.newFolder("failed-client")).setInitialBranch("main").call()) {
			var id = client.commit().setMessage("Accepted history").setAuthor("Author", "author@example.org")
					.setCommitter("Author", "author@example.org").setAllowEmpty(true).call().getId();
			push(client, "example.git", "refs/heads/main:refs/heads/main");
			assertEquals(id, context.repositoryService().openOrCreate("example").resolve("refs/heads/main"));
			assertThrows(IllegalStateException.class, () -> context.historyMaintenance().checkHealthy());
			try (HttpClient http = HttpClient.newHttpClient()) {
				for (String prefix : List.of("/api", "/api/v1")) {
					var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:"
							+ application.getRestPort() + prefix + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
					assertEquals(response.body(), 503, response.statusCode());
					assertTrue(response.body().contains("History projection rebuild required"));
				}
			}
		}
	}

	@Test
	public void deletionWaitsForAnInFlightIndexRefresh() throws Exception {
		String database = "refresh-" + UUID.randomUUID();
		Path index = temporary.newFolder("refresh-index").toPath();
		start(properties(database, index, "create"));
		try (Git client = Git.init().setDirectory(temporary.newFolder("refresh-client")).setInitialBranch("main").call()) {
			client.commit().setMessage("Needle refresh").setAuthor("Author", "author@example.org")
					.setCommitter("Author", "author@example.org").setAllowEmpty(true).call();
			push(client, "example.git", "refs/heads/main:refs/heads/main");
		}
		application.stop();
		application = null;
		context = null;
		start(properties(database, index, "validate"));
		var entered = new java.util.concurrent.CountDownLatch(1);
		var resume = new java.util.concurrent.CountDownLatch(1);
		var actualFactory = context.sessionFactory();
		var pausedFactory = (org.hibernate.SessionFactory) java.lang.reflect.Proxy.newProxyInstance(
				org.hibernate.SessionFactory.class.getClassLoader(), new Class<?>[] {org.hibernate.SessionFactory.class},
				(proxy, method, arguments) -> {
					if (method.getName().equals("openSession")) {
						entered.countDown();
						assertTrue("Refresh must be released", resume.await(10, java.util.concurrent.TimeUnit.SECONDS));
					}
					try {
						return method.invoke(actualFactory, arguments);
					} catch (java.lang.reflect.InvocationTargetException failure) {
						throw failure.getCause();
					}
				});
		var maintenance = new org.eclipse.jgit.server.history.ReleasedSandboxHistoryMaintenance(pausedFactory);
		try (var repositories = new org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService(
				new io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory(actualFactory,
						List.of(new io.github.carstenartur.jgit.storage.hibernate.search.SearchRepositoryDeletionParticipant())), maintenance);
				var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var refresh = executor.submit(() -> { maintenance.refreshIndexes(pausedFactory); return null; });
			try {
				assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
				var deleting = new java.util.concurrent.CountDownLatch(1);
				var deletion = executor.submit(() -> { deleting.countDown(); return repositories.delete("example.git"); });
				assertTrue(deleting.await(10, java.util.concurrent.TimeUnit.SECONDS));
				assertThrows(java.util.concurrent.TimeoutException.class,
						() -> deletion.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
				resume.countDown();
				refresh.get(10, java.util.concurrent.TimeUnit.SECONDS);
				assertEquals(1, deletion.get(10, java.util.concurrent.TimeUnit.SECONDS).projectionRows());
				assertTrue(context.historySearch().searchMessages("example", "needle", 0, 10).isEmpty());
			} finally {
				resume.countDown();
			}
		}
	}

	@Test
	public void normalizesTransportNamesExactlyOnceForStorageAndProjections() throws Exception {
		start(properties("names-" + UUID.randomUUID(), temporary.newFolder("names-index").toPath(), "create"));
		try (Git client = Git.init().setDirectory(temporary.newFolder("names-client")).setInitialBranch("main").call()) {
			var id = client.commit().setMessage("Needle identity").setAuthor("Author", "author@example.org")
					.setCommitter("Author", "author@example.org").setAllowEmpty(true).call().getId();
			push(client, "example.git.git", "refs/heads/main:refs/heads/main");
			assertEquals("example.git", context.repositoryService().info("example.git.git").name());
			assertEquals(id, context.repositoryService().openOrCreate("example.git.git").resolve("refs/heads/main"));
			assertEquals(List.of(id.name()), context.historySearch().searchMessages("example.git.git", "needle", 0, 10)
					.stream().map(hit -> hit.objectId()).toList());
			assertTrue(context.historySearch().searchMessages("example", "needle", 0, 10).isEmpty());
		}
	}

	@Test
	public void rejectedAndDeletedRefsDoNotStartIndexingAndBlobTagsAreValid() throws Exception {
		Properties properties = properties("ignored-" + UUID.randomUUID(), temporary.newFolder("ignored-index").toPath(), "create");
		start(properties);
		var repository = context.repositoryService().openOrCreate("example");
		ReceiveCommand rejected = new ReceiveCommand(ObjectId.zeroId(), ObjectId.fromString("1".repeat(40)), "refs/heads/rejected");
		rejected.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON);
		ReceiveCommand deleted = new ReceiveCommand(ObjectId.fromString("1".repeat(40)), ObjectId.zeroId(), "refs/heads/deleted");
		deleted.setResult(ReceiveCommand.Result.OK);
		context.historyMaintenance().published("example", repository, List.of(rejected, deleted));
		context.historyMaintenance().checkHealthy();
		try (Git client = Git.init().setDirectory(temporary.newFolder("blob-client")).setInitialBranch("main").call()) {
			ObjectId blob;
			try (var inserter = client.getRepository().newObjectInserter()) {
				blob = inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, new byte[] {1, 2, 3});
				inserter.flush();
			}
			var update = client.getRepository().updateRef("refs/tags/blob");
			update.setNewObjectId(blob);
			update.update();
			push(client, "example.git", "refs/tags/blob:refs/tags/blob");
			assertEquals(blob, repository.resolve("refs/tags/blob"));
		}
		context.historyMaintenance().checkHealthy();
		assertTrue(context.historySearch().searchMessages("example", "needle", 0, 10).isEmpty());
	}

	private void push(Git client, String name, String specification) throws Exception {
		for (var result : client.push().setRemote(uri(name)).setRefSpecs(new RefSpec(specification)).call()) {
			for (var update : result.getRemoteUpdates()) {
				assertEquals(update.getMessage(), RemoteRefUpdate.Status.OK, update.getStatus());
			}
		}
	}

	private String uri(String name) {
		return "http://localhost:" + application.getGitPort() + "/git/" + name;
	}

	private void start(Properties properties) throws Exception {
		context = new ExternalServerPersistenceContext(properties);
		application = new JGitServerApplication();
		var start = JGitServerApplication.class.getDeclaredMethod("start", ServerPersistenceContext.class,
				int.class, int.class, String.class, String.class, boolean.class);
		start.setAccessible(true);
		start.invoke(application, context, 0, 0, null, null, true);
	}

	private static Properties properties(String database, Path index, String schema) {
		Properties properties = new Properties();
		properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1");
		properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
		properties.setProperty("hibernate.hbm2ddl.auto", schema);
		properties.setProperty("hibernate.connection.provider_class",
				"org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
		properties.setProperty("hibernate.cache.use_second_level_cache", "false");
		properties.setProperty("hibernate.search.backend.directory.type", "local-filesystem");
		properties.setProperty("hibernate.search.backend.directory.root", index.toString());
		properties.setProperty("hibernate.search.indexing.plan.synchronization.strategy", "sync");
		return properties;
	}
}
