# JGit Server WebApp (`sandbox-jgit-server-webapp`)

> **Navigation**: [Main README](../README.md) · [Consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md)

## Overview

`sandbox-jgit-server-webapp` is a Jakarta Servlet-based REST API server that provides web-based access to Git repositories indexed by the `sandbox-jgit-storage-hibernate` integration module. It enables semantic and structural search over indexed Java repositories.

Repository lifecycle callers are being moved behind the application-owned `SandboxRepositoryService` boundary so the copied storage backend can be replaced by released `jgit-storage-hibernate` modules in independently verified stages. The copied backend remains the production default until its database schema has passed the documented adoption and rollback procedure.

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
| `GET` | `/api/search/similar?repo=...&blobId=...` | Find similar code blobs |
| `GET` | `/api/analytics/authors?repo=...` | Author commit statistics |
| `GET` | `/api/analytics/objects?repo=...` | Object type counts |
| `GET` | `/api/analytics/packs?repo=...` | Pack file statistics |

This table is a quick orientation only, not a full API reference.

Additional routes include:

- Versioned routes under `/api/v1/*`
- Additional `/api/search/*` endpoints such as `annotations`, `docs`, `fqn`, and `filehistory`, plus `migration/*` sub-routes
- Admin reindex endpoint: `POST /api/admin/reindex` (Bearer-token protected)

## Released-library consumer scope

The storage bridge consumes released Core only. The server additionally consumes
released Search at the same `jgit-storage-hibernate.version`. Its guarded external
context uses `SandboxHistorySearch` for commit/path queries, author counts and the
search health probe; normal startup still selects the copied adapter until the
remaining Java, full-history rebuild and schema migration slices are complete.

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
any analyzed term. Java projections and optional embeddings remain transitional;
resolving Search does not qualify normal-startup or production database cutover.

Successful Smart HTTP pushes on the external context now feed the released
`CommitIndexer`, including history reachable through annotated commit tags.
Rejected commands and ref deletions add no projections; valid blob/tree tags are
ignored. Each request owns a repository reference separate from the service.
Closing a request therefore does not dispose the service's cached handle.
Repository deletion also removes released Search projections transactionally.

Indexing is synchronous and uses bounded library batches. In 0.11.5 the public
`indexCommitsFrom` API still walks the entire ancestry of each new tip, including
previously indexed commits; repeated/shared tips can repeat that work. A successful
incremental push therefore does not yet provide bounded latency on large histories.
A public incremental/multi-tip API remains a qualification item before final cutover.

An indexing failure cannot undo an accepted Git push. It is logged and makes both
health URLs return 503 for the rest of that process. `/admin/reindex` retains its
existing response and refreshes Lucene from existing ORM rows, serialized against
projection writers; it does not reconstruct missing rows or clear that failure.
The explicit Git-history rebuild endpoint and durable recovery state are separate
migration work. Incremental history retains projections from force-pushed or deleted
refs until a full rebuild prunes unreachable commits.

See [jgit-storage-hibernate consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md) for candidate substitution, OSGi/package checks, forbidden-module checks, and retained evidence.

## Database adoption safety

Do not point the released Core factory at an existing Sandbox database merely by changing Hibernate entity registration. Before any Flyway adoption migration or production-backend switch, run the read-only legacy-schema preflight described in [JGit storage legacy-schema adoption](../docs/jgit-storage-legacy-adoption.md).

The accepted Core adoption baseline introduced published pre-library migration paths for PostgreSQL, HSQLDB and Microsoft SQL Server; later selected compatible Core releases retain the consumer contract. The maintenance command rejects unsupported database families before opening them, requires an existing HSQLDB database, marks the JDBC connection read-only, starts neither Hibernate nor Jetty and performs no DDL. A successful report is a prerequisite for the later database-specific migration; it is not itself a migration.

SQL Server support in the accepted migration baseline applies to Core storage. Search migration support is a separate contract, so the production cut-over must keep copied or separately isolated search projections until their own migration slice is verified.

## Docker

```bash
# Build and start with Docker Compose
docker-compose up --build
```

The `Dockerfile.jgit` and `docker-compose.yml` provide a self-contained deployment with database initialization scripts in `init-db/`.

## Architecture

```text
JGitServerApplication (Jakarta Servlet container)
  → RepositoryResource, SearchResource, AnalyticsResource, AdminResource, HealthResource
  → SandboxRepositoryService / HibernateRepositoryResolver
  → sandbox-jgit-storage-hibernate (current indexing and query integration)
  → HibernateConfig, ElasticsearchConfig, RepositoryManagerConfig
```

## Related Modules

- **[sandbox-jgit-storage-hibernate](../sandbox-jgit-storage-hibernate/README.md)** — current Core integration boundary plus transitional search/analysis code

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
an object-insertion security boundary. Normal startup remains on the transition path
until the remaining Java projections and schema cutover are qualified.
