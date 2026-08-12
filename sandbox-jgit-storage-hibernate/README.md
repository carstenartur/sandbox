# Sandbox JGit storage integration (`sandbox-jgit-storage-hibernate`)

> **Navigation**: [Main README](../README.md) · [Consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md) · [ADR 0002](../docs/adr/0002-adopt-released-jgit-storage-hibernate.md)

## Current role

This module is the Sandbox-owned integration layer around the released [`jgit-storage-hibernate`](https://github.com/carstenartur/jgit-storage-hibernate) Core API. It is being separated from the copied generic JGit/Hibernate implementation in reviewable migration slices.

The selected released version is defined only by:

```xml
<jgit-storage-hibernate.version>...</jgit-storage-hibernate.version>
```

in [`pom.xml`](pom.xml). At the time of this documentation update it is `0.1.18`; automated candidate runs replace that property with the same-run library version and verify the resolved dependency tree.

New Sandbox code must enter generic database-backed Git storage through `org.sandbox.jgit.storage.integration.JGitStorageLibraryBoundary` or another explicitly reviewed public adapter. It must not add new dependencies on copied `org.eclipse.jgit.storage.hibernate` implementation packages.

## Authoritative consumer scope

Sandbox currently consumes **Core only**:

```text
io.github.carstenartur:jgit-storage-hibernate-core
```

The consumer contract deliberately rejects upstream Search, Java Analysis, Architecture, and benchmark modules. Their capabilities are not considered integrated merely because an artifact resolves; each needs its own schema, adapter, endpoint, and compatibility migration slice.

See [jgit-storage-hibernate consumer contract](../docs/JGIT_STORAGE_HIBERNATE_CONTRACT.md) for the executable baseline/candidate protocol and retained evidence.

## Ownership boundary

The external library owns:

- Git packs, refs, Reftables, reflogs, locks, and repository lifecycle;
- released Core entities, migrations, factories, and public DTOs;
- generic capabilities only after their dedicated modules are adopted.

Sandbox retains:

- REST resources and transport DTOs;
- Eclipse/UI and operational integration;
- current copied search and Java-analysis projections during migration;
- optional embedding and rank-fusion experiments;
- temporary adapters required to move deployed data and callers safely.

## Migration status

1. **Core dependency and public integration boundary — present and contract-tested.**
2. Repository construction/lifecycle cut-over — guarded external path verified; normal startup cut-over pending production-like database evidence.
3. Search projection cut-over — pending.
4. Java-analysis cut-over — pending.
5. Removal of copied generic packages and schemas — pending.

The copied implementation remains temporarily buildable so data/schema and service cut-overs can be reviewed independently. It is not the intended location for new generic storage functionality.

## OSGi bridge contract

The plain Maven module is also packaged as an OSGi bundle. Its Bnd configuration must:

- export the Sandbox integration boundary;
- import the released Core API packages;
- produce a valid `Bundle-SymbolicName`;
- remain free of malformed multiline package instructions.

`OsgiManifestContractTest` verifies the generated manifest in the ordinary Maven build. The external consumer script additionally inspects the built JAR, dependency tree, logs, and test reports.

## Related module

- **[sandbox-jgit-server-webapp](../sandbox-jgit-server-webapp/README.md)** — REST and Smart HTTP server using the staged repository-service boundary.
