# JGit Server WebApp (`sandbox-jgit-server-webapp`)

> **Navigation**: [Main README](../README.md) · [Consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md)

## Overview

`sandbox-jgit-server-webapp` is a Jakarta Servlet-based REST API server that provides web-based access to Git repositories stored through released `jgit-storage-hibernate` modules. It enables semantic and structural search over indexed Java repositories.

Normal startup uses published **0.11.8 Core, Search and Java Analysis** behind application-owned repository/search boundaries. The copied generic implementation is removed. Database adoption is an explicit operator procedure; normal startup validates the released schema and never performs the adoption.

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Health check — returns service status as JSON |
| `POST` | `/api/repos` | Create a new repository (body: `{"name":"...","description":"..."}`) |
| `GET` | `/api/repos/{name}` | Get repository info |
| `GET` | `/api/search/commits?repo=...&q=...` | Search commit messages |
| `GET` | `/api/search/paths?repo=...&q=...` | Search changed file paths |
| `GET` | `/api/search/types?repo=...&q=...` | Search Java types |
| `GET` | `/api/search/symbols?repo=...&q=...` | Search methods and symbols |
| `GET` | `/api/search/hierarchy?repo=...&q=...` | Find type hierarchy relationships |
| `GET` | `/api/search/source?repo=...&q=...` | Full-text source search |
| `GET` | `/api/search/semantic?repo=...&q=...` | Semantic (embedding-based) search |
| `GET` | `/api/search/hybrid?repo=...&q=...` | Combined keyword + semantic search |
| `GET` | `/api/search/similar?repo=...&q=...&blobId=...` | Find similar code blobs |
| `GET` | `/api/analytics/authors?repo=...` | Author commit statistics |
| `GET` | `/api/analytics/objects?repo=...` | Object type counts |
| `GET` | `/api/analytics/packs?repo=...` | Pack file statistics |

This table is a quick orientation only, not a full API reference.

Additional routes include:

- Versioned routes under `/api/v1/*`
- Additional `/api/search/*` endpoints such as `annotations`, `docs`, `fqn`, and `filehistory`, plus `migration/*` sub-routes
- Admin reindex endpoint: `POST /api/admin/reindex` (Bearer-token protected)

## Released-library consumer scope

The OSGi storage bridge consumes released Core only. The executable server consumes
Core, Search and Java Analysis at the same `jgit-storage-hibernate.version`.
`SandboxHistorySearch`, `SandboxFileHistory` and `SandboxSourceSearch` provide every
retained REST route; pools, cache, application reports and optional embeddings are
server-owned. See [runtime contracts](docs/released-runtime-cutover.md),
[generation lifecycle](docs/analysis-generation-lifecycle.md) and
[optional embeddings](docs/application-embeddings.md).

The released adapter preserves REST fields, full multiline commit messages,
repository isolation and `totalResults` as the size of the returned page. Queries
are literal text, with the existing default 20 / maximum 100 results. Stable
cursors support deep offsets with bounded memory; traversing a deep page still
costs time proportional to the skipped hits. The default message analyzer retains
CamelCase splitting and stop words; explicit operator-supplied analyzer settings
are preserved. Changing analyzer profiles requires rebuilding the derived index.

Two changed-path semantics are deliberate: paths come from the first-parent diff
(root commits include all paths; deletions count), and all neutral analyzed path
terms must match. The copied implementation enumerated whole trees and matched
any analyzed term. Java search uses immutable historical generations rather than
copied ORM projections; optional vectors are generated lazily within those generations.

Successful Smart HTTP pushes feed the released
`CommitIndexer`, including history reachable through annotated commit tags.
Rejected commands and ref deletions add no projections; valid blob/tree tags are
ignored. Each request owns a repository reference separate from the service.
Closing a request therefore does not dispose the service's cached handle.
Repository deletion also removes released Search projections transactionally.

Indexing is synchronous and uses the released 0.11.6 multi-tip API with bounded
batches. A successful complete ancestry walk certifies its tips for later pushes;
subsequent calls traverse only history beyond those tips. Existing projection rows
alone never certify ancestry. The process keeps at most 128 certified tips for each
of 256 recently used repositories. Restart, eviction, deletion, failure and rebuild
invalidate certificates; the next push safely walks the full relevant history again.
If old boundary ancestry has been pruned, indexing retries once without boundaries.

An indexing failure cannot undo an accepted Git push. It is logged and both health
URLs return 503 while that repository has a known failure in this process.
`/admin/reindex` retains its response and refreshes Lucene from existing ORM rows;
it does not restore missing SQL rows or clear that failure.

`POST /api/admin/rebuild?repo=NAME` (also `/api/v1/admin/rebuild`) uses the existing
`JGIT_ADMIN_TOKEN` Bearer authentication and an existing-only read lease. Missing
identities return 404 without creating a repository. It refreshes the reader under
the writer lock, clears all old certificates before purging, then reconstructs the
projection from all named-ref ancestry plus detached HEAD. Successful completion
clears the known failure. Configuration/storage failures return 500 and remain
visible through health; an interrupted/failed rebuild can leave partial projections
and is safely rerunnable. The response reports `removedProjections` and
`indexedCommits`. Repository aliases are normalized exactly once.

The shared locks serialize local pushes, deletion, refresh and rebuild. Stop other
server processes and external projection writers during an administrative rebuild.
Incremental pushes retain historical rows after ref deletion or force push; a full
rebuild prunes history no longer reachable from refs/HEAD. Failure state and
certificates are process-local; health is not a durable completeness audit.

Exact `filehistory?path=...` reads now have a released-backend adapter. It walks
all parents from named refs and detached HEAD, plus still-available commits retained
by released Search after a ref deletion or force push. Valid non-commit tags are
ignored. Each commit containing the exact path contributes a version, even when
the blob is unchanged or is also present under another path. Binary entries and
submodules are included without loading their target as text. Paths are matched
before paging; ordering is author time descending, then commit ID for ties.
Renames start a separate path history. Whitespace filenames remain valid; a trailing slash never matches a file. Missing
repositories return no results without being created. Reads hold the existing-only
repository lease and honor interruption. Missing authoritative Git objects remain
errors; a historical projection whose root was already pruned cannot recover it.

See [jgit-storage-hibernate consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md) for candidate substitution, OSGi/package checks, forbidden-module checks, and retained evidence.

## Database adoption safety

Do not point the released Core factory at an existing Sandbox database merely by changing Hibernate entity registration. Before any Flyway adoption migration or production-backend switch, run the read-only legacy-schema preflight described in [JGit storage legacy-schema adoption](../docs/jgit-storage-legacy-adoption.md).

The accepted Core adoption baseline introduced published pre-library migration paths for PostgreSQL, HSQLDB and Microsoft SQL Server; later selected compatible Core releases retain the consumer contract. The maintenance command rejects unsupported database families before opening them, requires an existing HSQLDB database, marks the JDBC connection read-only, starts neither Hibernate nor Jetty and performs no DDL. A successful report is a prerequisite for the later database-specific migration; it is not itself a migration.

Core and Search have separate published migration streams for PostgreSQL and SQL Server; HSQLDB Search is available from 0.11.6. The normal application context validates both exact migration histories before Hibernate starts. The copied commit projection must be archived and rebuilt from Git using the documented procedure; it cannot be baselined as released Search.

The startup guard checks the effective Hibernate settings, including inherited JVM settings and classpath `hibernate.properties`, before opening JDBC connections. It requires `hibernate.hbm2ddl.auto=validate` and rejects all Jakarta/Javax database and script schema-generation actions (including `none`) and contributor-specific action overrides. Hibernate receives the same validated settings snapshot; script generation must run separately from server startup.

## First start and restart

Build the standalone modules with the [consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md).
For a new local persistent database, run:

```bash
JGIT_DB_URL='jdbc:h2:file:./data/jgit' java -jar target/jgit-server.jar --initialize-empty-schema
```

The explicit flag installs Core/Search into an empty schema and starts the server.
Stop with Ctrl-C; subsequent runs use the same database URL and omit the flag.
`--help` describes the command. A populated legacy database requires the separate
adoption procedure above. `JGIT_DB_DDL_AUTO` defaults to `validate`; `update`,
`create` and conflicting schema-generation settings are rejected.

## Docker

`Dockerfile.jgit` builds the executable server on Java 25. Compose selects SQL Server
and Elasticsearch 8.19.12. Provision the configured SQL Server database first using
your database administration procedure (`init-db/` contains the SQL template).
For an empty target, build and initialize it explicitly:

```bash
docker compose build jgit-server
docker compose run --rm --service-ports jgit-server --initialize-empty-schema
```

After stopping that first run, `docker compose up -d jgit-server` uses normal validated
startup. Existing databases follow the adoption procedure, not empty-schema initialization.

## Architecture

The application owns REST/Smart HTTP, repository leases, generation caches, report
formulas and optional model lifetime. Released Core owns authoritative Git storage;
released Search owns commit projections; released Java Analysis owns Java parsing,
semantic comparison and generic file indexing. No copied generic implementation is
included in the executable. The packaged-JAR integration test validates its actual
runtime providers, first start, SIGTERM and restart.

## Related Modules

- **[sandbox-jgit-storage-hibernate](../sandbox-jgit-storage-hibernate/README.md)** — minimal Core OSGi integration boundary

The released context serves `/analytics/objects` and `/analytics/packs` through Core's
public `RepositoryStorageStatistics`. Counts include published objects unreachable from
refs; they do not approximate storage by walking reachable history. Missing repositories
return zero counts without creating an identity. Pack totals retain Core's physical
catalog semantics (distinct pack groups and declared file bytes, including staging rows).
Concurrent pushes or repacks may change an inventory while it is being read.

`SandboxRepositoryService.withExistingRepository` provides scoped existing-only reads
for trusted server analysis. Released Core permits discovery/read and denies ref updates
through this handle. The callback must not retain/close the repository or close the
service. Active readers participate in the local lifecycle: deletion is rejected while
one is active; shutdown rejects new readers and waits for existing readers to finish
before closing storage and Hibernate. Independent readers do not hold the lifecycle
monitor during their work. This is local coordination, not a distributed snapshot or
an object-insertion security boundary.

### Released Java project analysis

The server now consumes the published Java Analysis module alongside Core and
Search; the OSGi storage bridge remains Core-only. `ReleasedJavaProjectAnalysis`
creates Sandbox `AnalyzedFile` values from an entire `JavaProjectSnapshot` and an
explicit `JavaAnalysisConfiguration`. Sibling sources are analyzed together and
unchanged blob bytes are analyzed again in another commit's project context.
The adapter does not download dependencies, register Java ORM entities, load an
embedding model, or expose parser objects through REST.

`AnalyzedFile` copies provenance, file facets and diagnostic values. Mutable
library analysis entities remain internal for subsequent semantic comparisons.
Quality includes the analysis-run status, problem/error counts, binding-status
counts, unresolved dependencies and configuration diagnostics; a zero syntax-error
count does not imply a complete classpath. `JavaHistoryCorpus` loads complete
commit trees across all refs, all parents, detached HEAD and retained Search roots.
Public Maven/Gradle resolvers use only the configured local artifact repository.
Malformed build metadata falls back to available source roots with diagnostics;
absolute/escaping roots are omitted. Oversized and binary source/build files are
omitted with diagnostics in the analyzed files of that commit.

Historical file search retains one record per repository/blob. Its explicit
`oldest-author-oid-path-v1` policy picks author time ascending, then commit ID and
path. This replaces ingestion-order provenance: adding an older root can change
the canonical path, module and author. Per-commit file history remains separate.
Semantic comparisons analyze both complete commit contexts independently of the
canonical blob record. Source, path and API results share a bounded generation
cache, with one build budget and pinned readers through complete DTO mapping.

Git names that cannot safely share a portable temporary source tree (backslashes,
case aliases, reserved names, trailing dots/spaces or excessive component/path
lengths) retain their original provenance and searchable facets. Those files use
the public per-file analyzer with individual binding recovery. Other files still
share their complete available project context; both groups expose the omitted
context in diagnostics. Ordinary I/O failures remain errors.

Compatibility facets preserve constructor names, enum fields, ordered duplicate
reference types, parameter-before-return ordering, import-based legacy reference
qualification, flat nested-type names and interface-extends placement in
`implementsTypes`. Field declaration groups and raw parent types use the public
0.11.7 structure API. Primary type documentation collapses whitespace before its
2,000-character limit; it is a normalized view, not an AST pretty-printer rendering.
The complete documentation search facet includes all Javadocs, and the
string facet also includes decoded text blocks (values of at most three
characters remain omitted). Records are now recognized. These additions are
explicitly tested through the normal REST backend.
