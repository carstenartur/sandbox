# JGit Server WebApp (`sandbox-jgit-server-webapp`)

> **Navigation**: [Main README](../README.md)

## Overview

`sandbox-jgit-server-webapp` is a Jakarta Servlet-based REST API server that provides web-based access to Git repositories indexed by the `sandbox-jgit-storage-hibernate` backend. It enables semantic and structural search over indexed Java repositories.

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

This table is a quick orientation only, not a full API reference. The server also exposes versioned routes under `/api/v1/*`, additional `/api/search/*` endpoints (for example `annotations`, `docs`, `fqn`, `filehistory`, `migration/*`), and an admin reindex endpoint (`POST /api/admin/reindex`, Bearer-token protected).

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
  → HibernateRepositoryResolver (maps repo names to Hibernate-backed JGit repos)
  → sandbox-jgit-storage-hibernate (indexing and query backend)
  → HibernateConfig, ElasticsearchConfig, RepositoryManagerConfig
```

## Related Modules

- **[sandbox-jgit-storage-hibernate](../sandbox-jgit-storage-hibernate/README.md)** — the underlying storage and indexing backend
