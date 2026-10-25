# Released JGit library migration implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan
> task-by-task. Continue the complete migration; a completed version-update slice
> is not completion of the runtime migration.

**Goal:** Adopt stable 0.11.5 and replace Sandbox's copied generic backend with
released Core, Search and Java Analysis APIs.

**Architecture:** Keep the existing repository lifecycle boundary and REST server.
Replace generic capabilities behind application-owned DTOs and interfaces in
small, independently tested changes. Switch normal startup only when its wired
resources are compatible with the released persistence context.

**Tech Stack:** Java 25, Maven, JUnit, Hibernate ORM/Search, JGit, Jetty and Flyway.

**Spec:** `docs/superpowers/specs/2026-10-25-jgit-library-migration-design.md`

## Global constraints

- Stable `jgit-storage-hibernate` 0.11.5 includes the public API prerequisites
  identified against 0.11.4; no copied upstream implementations.
- Java 25; all assertions belong in Maven/JUnit.
- Ordinary PRs directly against current main, within the existing size policy.
- Preserve Git storage and REST compatibility; no production database writes.
- Preserve historical version/commit evidence rather than relabelling it.

## Review focus

- Divergent consumer versions or transitive Hibernate/JGit overrides.
- Changed-path searches that accidentally enumerate unchanged tree entries.
- Successful Git pushes followed by failed or stale projections.
- Startup against an existing unmigrated schema and shutdown after failed startup.
- Java analysis with missing classpath entries or moved source files.

## Slice 1: Qualify stable Core 0.11.5

**Files:**
- Modify: `sandbox-jgit-storage-hibernate/pom.xml`
- Modify: `sandbox-jgit-server-webapp/pom.xml`
- Modify: `sandbox-jgit-server-webapp/docs/jgit-storage-migration-matrix.json`
- Existing tests: storage integration tests and all server tests.

**Interfaces:** consumes the released `jgit-storage-hibernate.version` property;
produces a version-aligned, packaged consumer baseline for subsequent slices.

- [ ] Build the unchanged parent and both consumer modules with Java 25; record
  any baseline environment/build failure separately from code regressions.
- [ ] Set both `jgit-storage-hibernate.version` properties to `0.11.5` and update
  the selected/current-version fields in the ledger, retaining historical evidence.
- [ ] Run the existing full consumer contract and module `verify` goals. Expected:
  0.11.5 resolves in both modules, all tests pass, the OSGi manifest imports the
  released Core API, and the shaded server includes the released factory.
- [ ] Review the complete diff and open an ordinary PR against main.
- [ ] Verify actual-head CI and review findings before integration.

## Remaining slices and gates

Each slice gets a concrete test-first task brief before implementation, using
the exact released APIs discovered in its preceding slice. This avoids planning
later code against an assumed upstream interface.

- [ ] Search boundary: introduce `SandboxHistorySearch` with immutable commit-hit
  DTOs; exercise commit/path/time/author queries and pagination in H2.
- [ ] Search ingestion/rebuild: replace copied indexing with released
  `CommitIndexer`, test root/merge/delete paths and query-after-restart behavior.
- [ ] Java boundary: introduce `SandboxJavaHistory`, use `JavaProjectAnalyzer`,
  `SemanticHistoryQuery` and `JavaSemanticDiff`, test moves and binding diagnostics.
- [ ] Extensions: isolate optional embeddings/specialized reports and test
  available/disabled capability responses without native loading on Core startup.
- [ ] Schema and startup: released migration locations, validate-only deployment,
  disposable adoption integrity rehearsal, external default and real Smart HTTP
  restart round trip. Verify failed index updates do not fail published Git refs.
- [ ] Removal/deployment: delete replaced generic sources, update packaging and
  consumer descriptors, test shaded artifact and full main CI.

The migration matrix and the execution ledger retain completion state for every
slice. No task is completed merely because its dependency resolves.
