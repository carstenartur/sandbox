# ADR 0002: Adopt the released jgit-storage-hibernate modules

- **Status:** Accepted
- **Date:** 2026-07-27
- **Issue:** #1303

## Context

`sandbox-jgit-storage-hibernate` currently contains a copied generic JGit/Hibernate implementation together with Sandbox-specific search presentation, embeddings and REST-facing query services. The released `io.github.carstenartur:jgit-storage-hibernate` project now owns generic database-backed Git storage, history search, Java analysis and architecture analysis as separate modules.

Keeping both implementations independently editable would create divergent schemas, transaction behaviour, JGit compatibility and query semantics.

## Decision

Sandbox will adopt the released library in reviewable slices.

### Ownership

The external project owns:

- Git objects, packs, refs, Reftables, reflogs and repository lifecycle;
- generic history projections and full-text history search;
- binding-aware Java history and semantic code analysis;
- generic architecture rules, evidence and drift analysis;
- module-owned entities, schema migrations, facades, query objects and DTOs.

Sandbox owns:

- Sandbox REST resources and transport DTOs;
- UI and Eclipse integration;
- operational configuration specific to the Sandbox product;
- optional embedding/rank-fusion experiments not provided by the generic library;
- migration adapters required only while copied callers are being removed.

### First slice

The module consumes released version `0.1.14` from the anonymous static Maven repository documented by the external project. The release preserves the supported public API of `0.1.13` while adding the migration, repository-lock and chunked-storage work needed by the later database cut-over. `JGitStorageLibraryBoundary` exposes the public `RepositoryName` and `CoreEntities` contracts to Sandbox code. New integration code must use this boundary or another explicitly reviewed public-library adapter; it must not add new dependencies on copied `org.eclipse.jgit.storage.hibernate` implementation classes.

### Repository-service boundary slice

Before changing the active storage backend, server repository lifecycle code is moved behind `SandboxRepositoryService`:

- REST resources consume stable Sandbox metadata and public JGit `Repository` objects;
- Smart HTTP delegates repository opening and process-owned handle management to the service;
- default-repository provisioning uses the same boundary;
- `CopiedHibernateRepositoryService` is the only application adapter allowed to construct or cache the copied `HibernateRepository` implementation;
- Search and Java-analysis services may temporarily continue to use the legacy session-factory provider until their dedicated adoption slices.

The resolver retains a deprecated generic compatibility method only so existing integration tests and callers compile during the staged transition. Its erased public contract is `Repository`; new application code must use `getRepositoryService()`. The compatibility method is removed when copied-backend integration tests move to the external Core factory.

### Public factory-adapter slice

`ExternalHibernateRepositoryService` adapts the released `HibernateRepositoryFactory` and owns the resulting `HibernateGitStorage` handles. It exposes only public JGit `Repository` instances and `SandboxRepositoryInfo` to the server. Unit tests use a fake implementation of the released factory contract, so the application-side lifecycle, identity normalization, caching, metadata and close semantics can be verified without importing the external implementation packages or requiring a database.

This adapter is deliberately not made the production default in the same slice. The existing Sandbox `SessionFactory` still registers copied Core entity classes and owns a schema whose exact adoption state must be established before the external factory is allowed to write to it. Registering copied and external Core entities together would also create duplicate persistence mappings. Production wiring changes only after the external Core migration runbook, Hibernate validation and repository-level rollback test have succeeded on a restored database.

### Subsequent slices

1. Classify the existing Core schema, apply the matching `jgit-storage-hibernate-core` adoption/migration path and verify backup restoration.
2. Construct a persistence context with external Core entities plus only the still-required Sandbox projection entities, then switch production wiring from `CopiedHibernateRepositoryService` to `ExternalHibernateRepositoryService`.
3. Replace copied commit/history entities and indexers with `jgit-storage-hibernate-search`.
4. Replace copied Java AST/history analysis with `jgit-storage-hibernate-java-analysis`.
5. Move Sandbox-only embeddings and REST query composition behind application-owned interfaces.
6. Remove copied generic packages, migrations and dependencies.
7. Add a build rule that rejects new source references to removed/copied implementation packages.

Each slice must preserve repository data through the external migration/adoption runbook and must keep one authoritative implementation active.

## Consequences

- The first slice adds a released dependency without immediately deleting the copied implementation.
- The repository-service boundary makes the later factory cut-over local instead of requiring simultaneous REST, Smart HTTP and startup rewrites.
- The public factory adapter completes the application-side cut-over point while leaving the persistence migration explicit and independently reversible.
- The temporary coexistence and deprecated compatibility method are explicit and bounded by this migration plan.
- Public external APIs and application-owned interfaces, not implementation packages, define the replacement contract.
- Database migration and service cut-over remain separate follow-up changes and cannot be represented as a package rename.
