# JGit Storage Backend (`sandbox-jgit-storage-hibernate`)

> **Navigation**: [Main README](../README.md)

## Overview

`sandbox-jgit-storage-hibernate` is a Hibernate/Lucene-based JGit storage backend for persistent Git object indexing and querying. It enables structural analysis and semantic search over Java source code stored in Git repositories.

## Features

- **Commit indexing** — stores Git commits and blob contents in a relational database via Hibernate ORM
- **Java structure extraction** — parses Java blobs with Eclipse JDT AST to extract structural information (`JavaBlobExtractor`, `JavaStructureVisitor`)
- **Full-text and semantic search** — combines BM25 keyword search with embedding-based semantic search (rank fusion via `RankFusionUtil`)
- **Index migration** — `IndexMigrationService` supports schema evolution and incremental re-indexing
- **REST-accessible query service** — `GitDatabaseQueryService` exposes indexed data to the server webapp

## Architecture

```
CommitIndexer (walks Git history)
  → JavaBlobExtractor (AST parse Java blobs)
  → BlobIndexer (stores to Hibernate)
  → EmbeddingBackfillService (generates embeddings for semantic search)
  → GitDatabaseQueryService (query API)
```

Key packages:
- `org.eclipse.jgit.storage.hibernate.service` — indexing and query services
- `org.eclipse.jgit.storage.hibernate.search` — AST-based structure extraction and rank fusion

## Dependencies

- Hibernate ORM (JPA persistence)
- Eclipse JDT Core (Java AST parsing)
- JGit (Git repository access)

## Related Modules

- **[sandbox-jgit-server-webapp](../sandbox-jgit-server-webapp/README.md)** — REST server that uses this storage backend
