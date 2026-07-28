# JGit Server WebApp (`sandbox-jgit-server-webapp`)

> **Navigation**: [Main README](../README.md)

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

## Database adoption safety

Do not point the released Core factory at an existing Sandbox database merely by changing Hibernate entity registration. Before any Flyway adoption migration or production-backend switch, run the read-only legacy-schema preflight described in [JGit storage legacy-schema adoption](../docs/jgit-storage-legacy-adoption.md).

The pinned `jgit-storage-hibernate-core:0.1.15` release publishes pre-library migration paths for PostgreSQL, HSQLDB and Microsoft SQL Server. The maintenance command rejects H2 and other unsupported database families before opening them, requires an existing HSQLDB database, marks the JDBC connection read-only, starts neither Hibernate nor Jetty and performs no DDL. A successful report is a prerequisite for the later database-specific migration; it is not itself a migration.

SQL Server support in 0.1.15 applies to Core storage. The generic Search module does not yet publish a SQL Server migration contract, so the production cut-over must keep copied or separately isolated search projections until their own migration slice is verified.

## Docker

```bash
# Build and start with Docker Compose
docker-compose up --build
```

The `Dockerfile.jgit` and `docker-compose.yml` provide a self-contained deployment with database initialization scripts in `init-db/`.

## Architecture

```
JGitServerApplication (Jakarta Servlet container)
  → RepositoryResource, SearchResource, AnalyticsResource, AdminResource, HealthResource
  → SandboxRepositoryService / HibernateRepositoryResolver
  → sandbox-jgit-storage-hibernate (current indexing and query integration)
  → HibernateConfig, ElasticsearchConfig, RepositoryManagerConfig
```

## Related Modules

- **[sandbox-jgit-storage-hibernate](../sandbox-jgit-storage-hibernate/README.md)** — current storage/search integration and staged external-library adoption boundary
