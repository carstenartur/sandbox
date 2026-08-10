# ADR 0002: Adopt the released jgit-storage-hibernate modules

- **Status:** Accepted
- **Date:** 2026-07-27
- **Issue:** #1303
- **Implementation note (2026-08-09):** the executable consumer contract is currently Core-only. The selected release is defined by `jgit-storage-hibernate.version` in the consumer POM (currently `0.1.18`), not by the historical version recorded when this ADR was accepted.

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

### Accepted baseline and current version selection

This decision was accepted against released Core baseline `0.1.15`, which preserved the public factory API used by the adapter and added the Microsoft SQL Server Core provisioning and copied-Sandbox legacy-adoption paths required by the deployed database.

The implementation has since advanced to later compatible Core releases. The authoritative selected version is the `jgit-storage-hibernate.version` property in `sandbox-jgit-storage-hibernate/pom.xml`; `sandbox-jgit-server-webapp/docs/jgit-storage-migration-matrix.json` records the corresponding migration evidence. The real-consumer candidate workflow replaces only this upstream version in POM files and then executes the Sandbox-owned contract.

`JGitStorageLibraryBoundary` exposes the public `RepositoryName` and `CoreEntities` contracts to Sandbox code. New integration code must use this boundary or another explicitly reviewed public-library adapter; it must not add new dependencies on copied `org.eclipse.jgit.storage.hibernate` implementation classes.

The current executable consumer scope is deliberately **Core only**. Search and Java Analysis remain later migration slices and are rejected by the contract until their schema, adapter, endpoint and compatibility work is implemented. Resolving those artifacts alone is not integration evidence.

SQL Server support in the accepted baseline applies to Core storage only. The external Search module did not publish the corresponding SQL Server migration contract at the time of this decision, so copied or separately isolated search projections remain outside the Core cut-over.

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

This adapter is deliberately not made the production default in the same slice. The existing Sandbox `SessionFactory` still registers copied Core entity classes and owns a schema whose exact adoption state must be established before the external factory is allowed to write to it. Registering copied and external Core entities together would also create duplicate persistence mappings.

### Read-only schema-preflight slice

A standalone maintenance entry point uses the released `LegacyCoreSchemaAdoption` validator through plain JDBC. It accepts only database families with a published adoption migration for the selected Core line, marks the connection read-only and starts neither Hibernate, Jetty nor Flyway. Its deterministic JSON report is evidence for the later maintenance operation; it does not mutate or migrate the database.

Production wiring changes only after the database-specific adoption stream has run on a restored production-like database, all recorded BLOB checksums and reflog rows have been compared, Hibernate `validate` has passed and rollback by database restore has been exercised.

### Consumer compatibility contract

The Sandbox-owned script `.github/jgit-storage-hibernate-contract.sh` is the executable compatibility boundary. The central library invokes it in baseline and candidate modes. It verifies:

- Java 21 and the selected/candidate Core version;
- Sandbox boundary, repository-lifecycle and preflight tests;
- the absence of unadopted upstream modules;
- generated OSGi exports/imports and bridge JAR contents;
- standalone shaded-server packaging;
- machine-readable dependency, manifest, build-log and JUnit evidence.

The detailed protocol is documented in `docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md`.

### Subsequent slices

1. Run the released SQL Server Core adoption/migration path against a restored Sandbox database and verify backup restoration.
2. Construct a persistence context with external Core entities plus only the still-required Sandbox projection entities, then switch production wiring from `CopiedHibernateRepositoryService` to `ExternalHibernateRepositoryService`.
3. Replace copied commit/history entities and indexers with `jgit-storage-hibernate-search` after that module has a supported database path, or isolate the projections behind a process/application boundary.
4. Replace copied Java AST/history analysis with `jgit-storage-hibernate-java-analysis`.
5. Move Sandbox-only embeddings and REST query composition behind application-owned interfaces.
6. Remove copied generic packages, migrations and dependencies.
7. Add a build rule that rejects new source references to removed/copied implementation packages.

Each slice must preserve repository data through the external migration/adoption runbook and must keep one authoritative implementation active.

## Consequences

- The released Core dependency is pinned without immediately deleting the copied implementation.
- The repository-service boundary makes the later factory cut-over local instead of requiring simultaneous REST, Smart HTTP and startup rewrites.
- The public factory adapter completes the application-side cut-over point while leaving persistence migration explicit and independently reversible.
- The read-only preflight can classify the deployed SQL Server schema without allowing Hibernate `update` or Flyway DDL to run implicitly.
- Core and Search database support are not conflated.
- The temporary coexistence and deprecated compatibility method are explicit and bounded by this migration plan.
- Public external APIs and application-owned interfaces, not implementation packages, define the replacement contract.
- Database migration and service cut-over remain separate follow-up changes and cannot be represented as a package rename.
- Search or Java Analysis may enter the consumer matrix only together with their completed Sandbox migration slice.
