# TODO — sandbox_jgit_db

## Phase 1b: Dependency Bundling
- [ ] Evaluate Hibernate bundling vs. lightweight JDBC+Lucene approach
- [ ] Add HSQLDB JAR to `lib/` and update `Bundle-ClassPath`
- [ ] Add Hibernate ORM + Hibernate Search JARs to `lib/`
- [ ] Resolve OSGi classloading conflicts with Eclipse Platform
- [ ] Connect `HsqlDbManager` to actual HSQLDB connection pool
- [ ] Wire `IncrementalIndexer.processCommit()` to `CommitIndexer` + `BlobIndexer`

## Phase 2: EGit Integration
- [ ] Implement full `IResourceChangeListener` with .git/refs and .git/objects filtering
- [ ] Add `RepositoryProvider` connection detection
- [ ] Persist last indexed commit SHA per repository in database
- [ ] Add automatic indexing on EGit commit/pull/push events
- [ ] Add `IProgressMonitor` support for background indexing jobs

## Phase 3: Eclipse Views
- [ ] Connect `GitSearchView` to `GitDatabaseQueryService.searchCommitMessages()`
- [ ] Connect `JavaTypeHistoryView` to `GitDatabaseQueryService.getFileHistory()`
- [ ] Connect `CommitAnalyticsView` to `GitDatabaseQueryService.getAuthorStatistics()`
- [ ] Add search result double-click navigation to source
- [ ] Add sorting and filtering to table viewers
- [ ] Add view icons

## Phase 4: Java-specific Queries
- [ ] "Who replaced `instanceof` with switch-pattern?" — full-text search with EcjTokenizer
- [ ] "All classes extending AbstractCleanUp" — `JavaBlobIndex.extendsTypes` query
- [ ] "Which commits added @Deprecated annotations?" — annotation diff query
- [ ] "Show all method renames in last 3 months" — time range + declaredMethods diff
- [ ] Cross-repository: "In which repos is ObjectId defined?"

## Phase 5: Advanced Integration
- [ ] Context menu extensions in Package Explorer
- [ ] Connection to `sandbox_mining_core` for refactoring pattern detection
- [ ] Automatic `.sandbox-hint` recognition on new commits
- [ ] Enhanced preferences with database location chooser
- [ ] "Clear Database" and "Compact Database" actions

## Known Risks
- Hibernate bundling may be too large (~30 MB JARs) — consider JDBI + Lucene standalone
- OSGi classloading conflicts between Hibernate and Eclipse Platform
- Performance with large repositories during initial index — needs progress monitoring
- HSQLDB corruption on Eclipse crash — mitigate with WAL mode and checkpoints
