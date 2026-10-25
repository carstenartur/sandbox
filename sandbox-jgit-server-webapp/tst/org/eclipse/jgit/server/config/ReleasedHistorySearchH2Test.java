/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.JGitServerApplication;
import org.eclipse.jgit.server.history.SandboxHistorySearch;
import org.eclipse.jgit.server.history.SandboxHistorySearch.AuthorCount;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitIndexer;
import io.github.carstenartur.jgit.storage.hibernate.search.service.GitHistorySearchService;

/** Real released projections, public JGit writes and the application's HTTP wiring. */
public class ReleasedHistorySearchH2Test {

	private final String repo = "history-" + UUID.randomUUID();
	private ServerPersistenceContext context;
	private SandboxHistorySearch history;
	private JGitServerApplication application;
	private int sequence;

	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:" + repo);
		properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
		properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
		properties.setProperty("hibernate.connection.provider_class",
				"org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
		properties.setProperty("hibernate.cache.use_second_level_cache", "false");
		properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
		properties.setProperty("hibernate.search.indexing.plan.synchronization.strategy", "sync");
		properties.setProperty(GitHistorySearchService.MAX_OFFSET_PROPERTY, "1");
		context = new ExternalServerPersistenceContext(properties);
		history = context.historySearch();
	}

	@After
	public void tearDown() throws Exception {
		if (application != null) {
			application.stop();
		} else if (context != null) {
			context.close();
		}
	}

	@Test
	public void searchesMessagesBeforePagingAndAfterRepositoryClose() throws Exception {
		commit(repo, "Unrelated", "A", "a@example.org", Map.of("needle.txt", "needle"));
		String message = "Needle title\n\nComplete multiline body\nwith detail.";
		ObjectId first = commit(repo, message, "A", "a@example.org", Map.of("file.txt", "one"));
		ObjectId second = commit(repo, "Needle second", "B", "b@example.org", Map.of("file.txt", "two"));
		ObjectId third = commit(repo, "Needle third", "C", "c@example.org", Map.of("file.txt", "three"));
		commit(repo + "-other", "Needle elsewhere", "X", "x@example.org", Map.of());
		context.repositoryService().close();

		var all = history.searchMessages(repo, "needle", 0, 10);
		assertEquals(3, all.size());
		assertEquals(java.util.Set.of(first.name(), second.name(), third.name()),
				all.stream().map(SandboxHistorySearch.CommitHit::objectId).collect(java.util.stream.Collectors.toSet()));
		assertEquals(message, all.stream().filter(hit -> hit.objectId().equals(first.name())).findFirst().orElseThrow().message());
		assertEquals("a@example.org", all.stream().filter(hit -> hit.objectId().equals(first.name())).findFirst().orElseThrow().authorEmail());
		for (int offset = 0; offset < all.size(); offset++) {
			assertEquals(List.of(all.get(offset)), history.searchMessages(repo, "needle", offset, 1));
		}
		assertTrue(history.searchMessages(repo, "needle", Integer.MAX_VALUE, 1).isEmpty());
		assertTrue(history.searchMessages(repo, " \t", 0, 10).isEmpty());
		assertTrue(history.searchMessages(" ", "needle", 0, 10).isEmpty());
		assertTrue(history.searchMessages("missing", "needle", 0, 10).isEmpty());
		history.checkSearch();
	}

	@Test
	public void keepsLiteralQueryInputAndCamelCaseMessageAnalysis() throws Exception {
		ObjectId id = commit(repo, "StringBuilder -needle", "A", "a@example.org", Map.of());
		assertEquals(List.of(id.name()), history.searchMessages(repo, "builder", 0, 10)
				.stream().map(SandboxHistorySearch.CommitHit::objectId).toList());
		assertEquals(List.of(id.name()), history.searchMessages(repo, "-needle", 0, 10)
				.stream().map(SandboxHistorySearch.CommitHit::objectId).toList());
		assertTrue(history.searchMessages(repo, "the", 0, 10).isEmpty());
		assertEquals("sandbox-commit-message-v1", context.sessionFactory().getProperties().get(GitTextAnalysis.PROFILE_ID_PROPERTY));
	}

	@Test
	public void searchesOnlyFirstParentChangesIncludingRootAndDeletion() throws Exception {
		ObjectId root = commit(repo, "Root", "A", "a@example.org", Map.of("keep.txt", "unchanged", "change.txt", "old"));
		ObjectId next = commit(repo, "Modify", "A", "a@example.org", Map.of("keep.txt", "unchanged", "change.txt", "new"), root);
		ObjectId deleted = commit(repo, "Delete", "A", "a@example.org", Map.of("keep.txt", "unchanged"), next);
		ObjectId side = commit(repo, "Side", "B", "b@example.org", Map.of("keep.txt", "unchanged", "change.txt", "old", "branch.txt", "added"), root);
		ObjectId merge = commit(repo, "Merge", "A", "a@example.org", Map.of("keep.txt", "unchanged", "branch.txt", "added"), deleted, side);

		assertEquals(List.of(root.name()), pathIds("keep", 0));
		assertEquals(java.util.Set.of(root.name(), next.name(), deleted.name()), java.util.Set.copyOf(pathIds("change", 0)));
		assertEquals(List.of(merge.name(), side.name()), pathIds("branch txt", 0));
		assertEquals(List.of(side.name()), pathIds("branch txt", 1));
		assertTrue(pathIds("branch missing", 0).isEmpty());
		assertTrue(pathIds(" ", 0).isEmpty());
		assertTrue(history.searchChangedPaths(repo, "branch", 100, 1).isEmpty());
		assertEquals("branch.txt", history.searchChangedPaths(repo, "branch", 0, 1).getFirst().changedPaths().trim());
	}

	@Test
	public void groupsAuthorsByNameAndEmailAcrossTheWholeHistory() throws Exception {
		commit(repo, "One", "A", "one@example.org", Map.of());
		commit(repo, "Two", "A", "one@example.org", Map.of());
		commit(repo, "Three", "A", "two@example.org", Map.of());
		commit(repo, "Four", "B", "one@example.org", Map.of());
		commit(repo + "-other", "Other", "A", "one@example.org", Map.of());
		assertEquals(List.of(new AuthorCount("A", "one@example.org", 2),
				new AuthorCount("A", "two@example.org", 1), new AuthorCount("B", "one@example.org", 1)), history.authors(repo));
		assertTrue(history.authors("missing").isEmpty());
		assertTrue(history.authors(" ").isEmpty());
	}

	@Test
	public void servesReleasedSearchHealthAndAuthorsThroughBothRestPrefixes() throws Exception {
		String message = "Needle title\n\nBody text";
		ObjectId id = commit(repo, message, "Author", "author@example.org", Map.of("needle.txt", "content"));
		application = new JGitServerApplication();
		var start = JGitServerApplication.class.getDeclaredMethod("start", ServerPersistenceContext.class,
				int.class, int.class, String.class, String.class, boolean.class);
		start.setAccessible(true);
		start.invoke(application, context, 0, 0, null, null, true);
		try (HttpClient client = HttpClient.newHttpClient()) {
			for (String prefix : List.of("/api", "/api/v1")) {
				assertEquals("UP", get(client, prefix + "/health", 200).get("status").getAsString());
				JsonObject response = get(client, prefix + "/search/commits?repo=" + repo + "&q=needle", 200);
				assertEquals(1, response.get("totalResults").getAsInt());
				var hit = response.getAsJsonArray("results").get(0).getAsJsonObject();
				assertEquals(id.name(), hit.get("objectId").getAsString());
				assertEquals(message, hit.get("message").getAsString());
				assertEquals("Author", hit.get("author").getAsString());
				assertEquals("author@example.org", hit.get("authorEmail").getAsString());
				assertEquals(0, get(client, prefix + "/search/commits?repo=" + repo + "&q=needle&offset=99999", 200).get("totalResults").getAsInt());
				assertEquals(0, get(client, prefix + "/search/commits?repo=" + repo + "&q=%20%09", 200).get("totalResults").getAsInt());
				assertEquals("needle.txt", get(client, prefix + "/search/paths?repo=" + repo + "&q=needle", 200)
						.getAsJsonArray("results").get(0).getAsJsonObject().get("changedPaths").getAsString().trim());
				assertEquals(1, get(client, prefix + "/analytics/authors?repo=" + repo, 200).getAsJsonArray("authors").get(0).getAsJsonObject().get("commits").getAsLong());
				get(client, prefix + "/search/commits?repo=" + repo, 400);
				get(client, prefix + "/search/commits?repo=" + repo + "&q=" + URLEncoder.encode("x".repeat(1001), StandardCharsets.UTF_8), 400);
			}
		}
		assertFalse(context.sessionFactory().isClosed());
	}

	private List<String> pathIds(String query, int offset) {
		return history.searchChangedPaths(repo, query, offset, 10).stream().map(SandboxHistorySearch.PathHit::objectId).toList();
	}

	private JsonObject get(HttpClient client, String path, int expectedStatus) throws Exception {
		var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + application.getRestPort() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(response.body(), expectedStatus, response.statusCode());
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private ObjectId commit(String repositoryName, String message, String name, String email,
			Map<String, String> files, ObjectId... parents) throws Exception {
		Repository repository = context.repositoryService().openOrCreate(repositoryName);
		ObjectId id;
		try (var inserter = repository.newObjectInserter()) {
			DirCache cache = DirCache.newInCore();
			var builder = cache.builder();
			for (var file : new TreeMap<>(files).entrySet()) {
				DirCacheEntry entry = new DirCacheEntry(file.getKey());
				entry.setFileMode(FileMode.REGULAR_FILE);
				entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.getValue().getBytes(StandardCharsets.UTF_8)));
				builder.add(entry);
			}
			builder.finish();
			CommitBuilder commit = new CommitBuilder();
			commit.setTreeId(cache.writeTree(inserter));
			commit.setParentIds(parents);
			PersonIdent author = new PersonIdent(name, email, Instant.ofEpochSecond(1_700_000_000L + sequence++), ZoneOffset.UTC);
			commit.setAuthor(author);
			commit.setCommitter(author);
			commit.setMessage(message);
			id = inserter.insert(commit);
			inserter.flush();
		}
		new CommitIndexer(context.sessionFactory(), repositoryName).indexCommit(repository, id);
		return id;
	}
}
