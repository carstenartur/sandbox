/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.server.history.ReleasedSandboxHistorySearch;
import org.eclipse.jgit.server.history.SandboxHistoryElasticsearchAnalysisConfigurer;
import org.junit.Assume;
import org.junit.Test;
import io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;
import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitIndexer;

/** Real backend contract. The consumer CI supplies an isolated Elasticsearch service. */
public class ReleasedHistoryElasticsearchTest {
  @Test public void indexesAndSearchesMessagesAndPathsWithRepositoryIsolation() throws Exception {
    String hosts = System.getenv("JGIT_TEST_ELASTICSEARCH_HOSTS");
    Assume.assumeTrue("Elasticsearch service is supplied by the consumer-contract workflow", hosts != null && !hosts.isBlank());
    var settings = new Properties();
    settings.setProperty("hibernate.connection.url", "jdbc:h2:mem:es-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    settings.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    settings.setProperty("hibernate.hbm2ddl.auto", "validate");
    settings.setProperty("hibernate.search.backend.type", "elasticsearch");
    settings.setProperty("hibernate.search.backend.hosts", hosts);
    settings.setProperty("hibernate.search.backend.protocol", "http");
    settings.setProperty("hibernate.search.schema_management.strategy", "drop-and-create-and-drop");
    settings.setProperty("hibernate.search.indexing.plan.synchronization.strategy", "sync");
    ReleasedSchemaInitializer.initializeEmpty(settings);
    try (var context = HibernateConfig.createPersistenceContext(settings)) {
      var factory = context.sessionFactory();
      var repositories = context.repositoryService();
        var repository = repositories.openOrCreate("es-contract");
        org.eclipse.jgit.lib.ObjectId commit;
        try (var inserter = repository.newObjectInserter()) {
          var tree = new TreeFormatter();
          tree.append("readme.txt", FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, "Searchable change".getBytes(StandardCharsets.UTF_8)));
          var builder = new CommitBuilder();
          builder.setTreeId(inserter.insert(tree));
          builder.setAuthor(new PersonIdent("Author", "author@example.test")); builder.setCommitter(builder.getAuthor());
          builder.setMessage("FixHttpParser marker"); commit = inserter.insert(builder); inserter.flush();
        }
        new CommitIndexer(factory, "es-contract").indexCommit(repository, commit);
        var history = context.historySearch();
        history.checkSearch();
        assertEquals(List.of(commit.name()), history.searchMessages("es-contract", "parser", 0, 10).stream().map(hit -> hit.objectId()).toList());
        assertEquals(commit.name(), history.searchChangedPaths("es-contract", "readme", 0, 10).getFirst().objectId());
        assertTrue(history.searchMessages("other", "parser", 0, 10).isEmpty());
        assertTrue(history.searchMessages("es-contract", "parser", 1, 10).isEmpty());
    }
  }
}
