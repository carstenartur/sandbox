/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;

import java.net.URI;
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
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.server.JGitServerApplication;
import org.eclipse.jgit.server.history.SandboxFileHistory.FileVersion;
import org.junit.Test;
import com.google.gson.JsonParser;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitIndexer;

public class ReleasedFileHistoryH2Test {
  private static final Instant EPOCH = Instant.parse("2025-01-01T00:00:00Z");
  private int sequence;

  @Test
  public void includesAllParentsAndTagsAndUnchangedVersionsBeforeAuthorTimePaging() throws Exception {
    try (var context = new ExternalServerPersistenceContext(properties(UUID.randomUUID().toString(), "create"))) {
      Repository repository = context.repositoryService().openOrCreate("example");
      var original = Map.of("keep.txt", "same", "alias.txt", "same", "dir/a.bin", "\0binary", " ", "space filename");
      ObjectId root = commit(repository, 10, original, false);
      ObjectId main = commit(repository, 50, original, false, root);
      ObjectId side = commit(repository, 40, Map.of("keep.txt", "side"), false, root);
      ObjectId merge = commit(repository, 20, original, false, main, side);
      ObjectId tagged = commit(repository, 30, Map.of("keep.txt", "tag only"), false);
      ref(repository, "refs/heads/main", merge);
      tag(repository, "release", tagged, Constants.OBJ_COMMIT);
      try (var inserter = repository.newObjectInserter()) {
        ObjectId blob = inserter.insert(Constants.OBJ_BLOB, new byte[] {9});
        inserter.flush();
        ref(repository, "refs/tags/blob", blob);
        tag(repository, "annotated-blob", blob, Constants.OBJ_BLOB);
      }
      var history = context.fileHistory();
      var all = history.versions("example.git", "keep.txt", 0, 100);
      assertEquals(List.of(main.name(), side.name(), tagged.name(), merge.name(), root.name()), ids(all));
      assertEquals(all.get(0).blobObjectId(), all.get(3).blobObjectId());
      assertEquals(all.get(0).blobObjectId(), all.get(4).blobObjectId());
      assertEquals(List.of(side.name(), tagged.name()), ids(history.versions("example", "keep.txt", 1, 2)));
      assertEquals(3, history.versions("example", "alias.txt", 0, 100).size());
      assertEquals(3, history.versions("example", " ", 0, 100).size());
      assertEquals(3, history.versions("example", "dir/a.bin", 0, 100).size());
      assertEquals("bin", history.versions("example", "dir/a.bin", 0, 100).getFirst().fileType());
      assertTrue(history.versions("example", "dir", 0, 100).isEmpty());
      assertTrue(history.versions("example", "keep.txt/", 0, 100).isEmpty());
      assertTrue(history.versions("example", "/", 0, 100).isEmpty());
      assertTrue(history.versions("example", "keep.txt", Integer.MAX_VALUE, 1).isEmpty());
      assertTrue(history.versions("missing", "keep.txt", 0, 100).isEmpty());
      assertThrows(java.io.FileNotFoundException.class,
          () -> context.repositoryService().withExistingRepository("missing", ignored -> null));
    }
  }

  @Test
  public void preservesIndexedHistoryAfterRefDeletionAndReadsGitlinksWithoutLoadingTheirTargets() throws Exception {
    try (var context = new ExternalServerPersistenceContext(properties(UUID.randomUUID().toString(), "create"))) {
      Repository repository = context.repositoryService().openOrCreate("example");
      ObjectId historical = commit(repository, 10, Map.of("keep.txt", "historical"), true);
      new CommitIndexer(context.sessionFactory(), "example").indexCommit(repository, historical);
      ObjectId current = commit(repository, 20, Map.of("current.txt", "current"), false);
      ref(repository, "refs/heads/main", current);
      assertEquals(List.of(historical.name()), ids(context.fileHistory().versions("example", "keep.txt", 0, 10)));
      var gitlink = context.fileHistory().versions("example", "vendor", 0, 10).getFirst();
      assertEquals("1111111111111111111111111111111111111111", gitlink.blobObjectId());
      assertEquals("unknown", gitlink.fileType());
      assertEquals(current, repository.resolve("refs/heads/main"));
      assertEquals(1, context.fileHistory().versions("example", "current.txt", 0, 10).size());
    }
  }

  @Test
  public void includesDetachedHeadWithoutNamedRefsOrProjectionRows() throws Exception {
    try (var context = new ExternalServerPersistenceContext(properties(UUID.randomUUID().toString(), "create"))) {
      Repository repository = context.repositoryService().openOrCreate("detached");
      ObjectId commit = commit(repository, 10, Map.of("keep.txt", "detached"), false);
      var update = repository.updateRef(Constants.HEAD, true);
      update.setNewObjectId(commit);
      update.setForceUpdate(true);
      assertTrue(List.of(org.eclipse.jgit.lib.RefUpdate.Result.NEW, org.eclipse.jgit.lib.RefUpdate.Result.FORCED)
          .contains(update.update()));
      assertTrue(repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS).isEmpty());
      assertEquals(List.of(commit.name()), ids(context.fileHistory().versions("detached", "keep.txt", 0, 10)));
    }
  }

  @Test
  public void filtersMissingPathsBeforePagingAndOrdersEqualAuthorTimesByObjectId() throws Exception {
    String database = UUID.randomUUID().toString();
    List<String> expected;
    try (var context = new ExternalServerPersistenceContext(properties(database, "create"))) {
      Repository repository = context.repositoryService().openOrCreate("ties");
      ObjectId first = commit(repository, 10, Map.of("keep.txt", "first"), false);
      ObjectId absent = commit(repository, 100, Map.of("other.txt", "absent"), false, first);
      ObjectId second = commit(repository, 10, Map.of("keep.txt", "second"), false, absent);
      ObjectId tip = commit(repository, 200, Map.of("other.txt", "tip"), false, second);
      ref(repository, "refs/heads/main", tip);
      expected = java.util.stream.Stream.of(first.name(), second.name()).sorted().toList();
      assertEquals(expected, ids(context.fileHistory().versions("ties", "keep.txt", 0, 10)));
      assertEquals(expected.subList(1, 2), ids(context.fileHistory().versions("ties", "keep.txt", 1, 1)));
    }
    try (var context = new ExternalServerPersistenceContext(properties(database, "validate"))) {
      try {
        Thread.currentThread().interrupt();
        assertThrows(java.io.InterruptedIOException.class,
            () -> context.fileHistory().versions("ties", "keep.txt", 0, 10));
        assertTrue(Thread.currentThread().isInterrupted());
      } finally {
        Thread.interrupted();
      }
      assertFalse(context.repositoryService().isOpen("ties"));
      assertEquals(expected, ids(context.fileHistory().versions("ties", "keep.txt", 0, 10)));
    }
  }

  @Test
  public void exposesExactHistoryThroughBothPrefixesAfterRestartWithSingleNormalization() throws Exception {
    String database = UUID.randomUUID().toString();
    ObjectId id;
    try (var context = new ExternalServerPersistenceContext(properties(database, "create"))) {
      Repository repository = context.repositoryService().openOrCreate("example.git.git");
      id = commit(repository, 10, Map.of("keep.txt", "same"), false);
      ref(repository, "refs/heads/main", id);
    }
    var context = new ExternalServerPersistenceContext(properties(database, "validate"));
    var application = new JGitServerApplication();
    try {
      var start = JGitServerApplication.class.getDeclaredMethod("start", ServerPersistenceContext.class,
          int.class, int.class, String.class, String.class, boolean.class);
      start.setAccessible(true);
      start.invoke(application, context, 0, 0, null, null, true);
      try (var client = HttpClient.newHttpClient()) {
        for (String prefix : List.of("/api", "/api/v1")) {
          var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"
              + application.getRestPort() + prefix + "/search/filehistory?repo=example.git.git&q=ignored&path=keep.txt"))
              .GET().build(), HttpResponse.BodyHandlers.ofString());
          assertEquals(response.body(), 200, response.statusCode());
          var body = JsonParser.parseString(response.body()).getAsJsonObject();
          assertEquals("keep.txt", body.get("query").getAsString());
          assertEquals("example.git.git", body.get("repository").getAsString());
          assertEquals(1, body.get("totalResults").getAsInt());
          var file = body.getAsJsonArray("results").get(0).getAsJsonObject();
          assertEquals(id.name(), file.get("commitObjectId").getAsString());
          assertEquals(EPOCH.plusSeconds(10).toString(), file.get("commitTime").getAsString());
          assertEquals("txt", file.get("fileType").getAsString());
        }
      }
      assertFalse(context.repositoryService().isOpen("example.git.git"));
    } finally {
      application.stop();
    }
  }

  private ObjectId commit(Repository repository, int authorSeconds, Map<String, String> files, boolean gitlink,
      ObjectId... parents) throws Exception {
    try (var inserter = repository.newObjectInserter()) {
      var entries = new TreeMap<String, TreeEntry>();
      for (var file : files.entrySet()) {
        entries.put(file.getKey(), new TreeEntry(FileMode.REGULAR_FILE,
            inserter.insert(Constants.OBJ_BLOB, file.getValue().getBytes(StandardCharsets.UTF_8))));
      }
      if (gitlink) {
        entries.put("vendor", new TreeEntry(FileMode.GITLINK,
            ObjectId.fromString("1111111111111111111111111111111111111111")));
      }
      var commit = new CommitBuilder();
      commit.setTreeId(tree(inserter, entries));
      commit.setParentIds(parents);
      commit.setAuthor(new PersonIdent("Author", "author@example.org", EPOCH.plusSeconds(authorSeconds), ZoneOffset.UTC));
      commit.setCommitter(new PersonIdent("Committer", "committer@example.org", EPOCH.plusSeconds(++sequence), ZoneOffset.UTC));
      commit.setMessage("History " + sequence);
      var id = inserter.insert(commit);
      inserter.flush();
      return id;
    }
  }

  private record TreeEntry(FileMode mode, ObjectId id) { }

  private static ObjectId tree(ObjectInserter inserter, Map<String, TreeEntry> paths) throws Exception {
    // Construct bare Git objects: checkout-oriented DirCacheEntry rejects valid Git names on Windows.
    var entries = new TreeMap<String, TreeEntry>();
    var directories = new TreeMap<String, Map<String, TreeEntry>>();
    for (var path : paths.entrySet()) {
      int slash = path.getKey().indexOf('/');
      if (slash < 0) entries.put(path.getKey(), path.getValue());
      else directories.computeIfAbsent(path.getKey().substring(0, slash), ignored -> new TreeMap<>())
          .put(path.getKey().substring(slash + 1), path.getValue());
    }
    for (var directory : directories.entrySet()) {
      entries.put(directory.getKey() + "/", new TreeEntry(FileMode.TREE, tree(inserter, directory.getValue())));
    }
    var tree = new TreeFormatter();
    for (var entry : entries.entrySet()) {
      String name = entry.getKey();
      if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
      tree.append(name, entry.getValue().mode(), entry.getValue().id());
    }
    return inserter.insert(tree);
  }

  private static void ref(Repository repository, String name, ObjectId id) throws Exception {
    var update = repository.updateRef(name);
    update.setNewObjectId(id);
    update.setForceUpdate(true);
    var result = update.update();
    assertTrue(result.toString(), List.of(org.eclipse.jgit.lib.RefUpdate.Result.NEW,
        org.eclipse.jgit.lib.RefUpdate.Result.FORCED, org.eclipse.jgit.lib.RefUpdate.Result.FAST_FORWARD).contains(result));
  }

  private static void tag(Repository repository, String name, ObjectId target, int type) throws Exception {
    try (var inserter = repository.newObjectInserter()) {
      var tag = new TagBuilder();
      tag.setTag(name);
      tag.setObjectId(target, type);
      tag.setTagger(new PersonIdent("Author", "author@example.org"));
      tag.setMessage(name);
      var id = inserter.insert(tag);
      inserter.flush();
      ref(repository, "refs/tags/" + name, id);
    }
  }

  private static List<String> ids(List<FileVersion> versions) {
    return versions.stream().map(FileVersion::commitObjectId).toList();
  }

  private static Properties properties(String database, String ddl) {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:file-history-" + database + ";DB_CLOSE_DELAY=-1");
    properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    properties.setProperty("hibernate.hbm2ddl.auto", ddl);
    properties.setProperty("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    properties.setProperty("hibernate.cache.use_second_level_cache", "false");
    properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
    return properties;
  }
}
