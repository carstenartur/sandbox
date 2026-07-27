# Sandbox JGit storage integration (`sandbox-jgit-storage-hibernate`)

> **Navigation**: [Main README](../README.md) · [ADR 0002](../docs/adr/0002-adopt-released-jgit-storage-hibernate.md)

## Current role

This module is being converted from a copied generic JGit/Hibernate implementation into the Sandbox-owned integration layer for [`jgit-storage-hibernate`](https://github.com/carstenartur/jgit-storage-hibernate).

Released version `0.1.13` is now consumed through its public Core API. New Sandbox code should enter generic database-backed Git storage through `org.sandbox.jgit.storage.integration.JGitStorageLibraryBoundary` or another explicitly reviewed adapter. It must not add new dependencies on the copied `org.eclipse.jgit.storage.hibernate` implementation packages.

## Ownership boundary

The external library owns:

- Git packs, refs, Reftables, reflogs and repository lifecycle;
- generic commit/history projections and full-text search;
- binding-aware Java history and semantic analysis;
- generic architecture rules and drift analysis;
- module-owned entities, migrations, facades and DTOs.

Sandbox retains:

- REST resources and transport DTOs;
- Eclipse/UI integration;
- Sandbox operational configuration;
- optional embedding and rank-fusion experiments;
- temporary migration adapters until copied callers are removed.

## Migration status

1. **Core dependency and public integration boundary — in progress.**
2. Repository construction/lifecycle cut-over — pending.
3. Search projection cut-over — pending.
4. Java-analysis cut-over — pending.
5. Removal of copied generic packages and schemas — pending.

The copied implementation remains temporarily buildable so each data/schema and service cut-over can be reviewed independently. It is no longer the intended location for new generic storage functionality.

## Existing Sandbox-specific capabilities

- semantic embedding and rank-fusion experiments;
- REST-facing query composition used by `sandbox-jgit-server-webapp`;
- operational integration with the Sandbox product.

## Related module

- **[sandbox-jgit-server-webapp](../sandbox-jgit-server-webapp/README.md)** — REST server that will consume the integration boundary during the repository-lifecycle cut-over.
