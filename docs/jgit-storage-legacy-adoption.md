# JGit storage legacy-schema adoption

Sandbox currently contains the pre-library JGit/Hibernate schema. Do not switch the server to the released `jgit-storage-hibernate-core` factory and do not run adoption DDL until the existing database has passed the released read-only validator.

## Supported database boundary

The published pre-library adoption migrations in `jgit-storage-hibernate-core:0.1.15` support PostgreSQL, HSQLDB and Microsoft SQL Server. The maintenance command rejects H2 and every other JDBC family before opening a connection. A schema-shape check must not be presented as migration eligibility when no matching published Flyway location exists.

The shaded server JAR contains the PostgreSQL, HSQLDB and SQL Server JDBC drivers required by the supported paths. SQL Server support applies to Core storage only; `jgit-storage-hibernate-search` does not yet publish SQL Server migrations.

## Safety boundary

The preflight command:

- opens a plain JDBC connection using the normal `JGIT_DB_*` environment variables;
- accepts PostgreSQL, HSQLDB and SQL Server, matching the published 0.1.15 legacy-adoption migrations;
- requires `;ifexists=true` in an HSQLDB URL so a mistyped file path cannot create a database;
- marks the opened JDBC connection read-only before the first schema query;
- calls `LegacyCoreSchemaAdoption.requireSafeToAdopt(...)` from the pinned non-SNAPSHOT Core release;
- does not build a Hibernate `SessionFactory`;
- does not start Jetty;
- does not execute Flyway or any other DDL;
- writes no database rows;
- prints one bounded JSON report and exits with a non-zero status when adoption is unsafe or unsupported.

The command is not a migration. A successful report only establishes that the database is eligible for the separately reviewed Flyway adoption procedure.

## Before running

1. Stop all writers.
2. Take a restorable database backup.
3. Record repository and pack counts, ordered SHA-256 checksums of every `git_packs.data` value, refs and complete reflog rows including timestamps.
4. Use a restored production-like database first. Do not make the initial attempt against the only production copy.
5. Confirm the JDBC family and select only its matching `CoreSchemaMigrations` location. Never run PostgreSQL or HSQLDB SQL against SQL Server.
6. Keep Search disabled during the Core cut-over on SQL Server; it has no released SQL Server migration contract.

## Build and run

Build the normal server artifact from a clean checkout:

```shell
mvn --no-transfer-progress --batch-mode \
  --projects sandbox-jgit-server-webapp --also-make \
  -Dtycho.localArtifacts=ignore clean verify
```

Run the alternate maintenance entry point from the shaded server JAR:

```shell
java -cp sandbox-jgit-server-webapp/target/jgit-server.jar \
  org.eclipse.jgit.server.config.LegacyCoreSchemaPreflight
```

Configure the connection with the same variables as the server:

- `JGIT_DB_URL`, beginning with `jdbc:postgresql:`, `jdbc:hsqldb:` or `jdbc:sqlserver:`;
- for HSQLDB, include `;ifexists=true` in `JGIT_DB_URL`;
- `JGIT_DB_USER`;
- `JGIT_DB_PASSWORD` or `JGIT_DB_PASSWORD_FILE`;
- `JGIT_DB_DRIVER` when explicit driver loading is desired.

The command deliberately ignores `JGIT_DB_DDL_AUTO`; no Hibernate bootstrap occurs.

## Report

A safe pre-library schema produces JSON containing at least:

- discovered and missing columns;
- pack-row count;
- incomplete-row count;
- deterministically ordered duplicate logical pack identities;
- presence of `committed` and `committed_at`;
- `requiresAdoption`.

For the supported legacy shape, `requiresAdoption` is `true`. An already adopted shape can be safe while reporting `requiresAdoption` as `false`; do not rerun the first adoption migration in that state.

The validator fails before DDL when it finds:

- a database family without a published pre-library adoption migration;
- an HSQLDB URL that could create a missing database;
- missing legacy columns;
- only one of `committed` or `committed_at`;
- null or otherwise incomplete pack rows;
- negative file sizes;
- `pack_extension` values longer than 32 Unicode characters;
- duplicate `(repository_name, pack_name, pack_extension)` identities.

Resolve rejected rows from application knowledge or restore a known-good backup. The preflight never selects a duplicate row or truncates a value automatically.

## Next step after a successful preflight

Run the database-specific legacy-adoption Flyway stream from the released Core artifact:

```java
Flyway.configure()
    .dataSource(dataSource)
    .locations(CoreSchemaMigrations.SQL_SERVER_LEGACY_ADOPTION_LOCATION)
    .table(CoreSchemaMigrations.LEGACY_ADOPTION_SCHEMA_HISTORY_TABLE)
    .baselineOnMigrate(true)
    .baselineVersion(CoreSchemaMigrations.PRE_MIGRATION_BASELINE_VERSION)
    .baselineDescription("before pre-library core adoption")
    .load()
    .migrate();
```

Use `POSTGRESQL_LEGACY_ADOPTION_LOCATION` or `HSQLDB_LEGACY_ADOPTION_LOCATION` for those databases. SQL Server deployments require Flyway's `flyway-sqlserver` module in the migration tool.

After the adoption stream succeeds, establish the normal Core history at `CURRENT_SCHEMA_VERSION`, apply the remaining regular migrations from the matching Core location, and start Hibernate only with `hibernate.hbm2ddl.auto=validate`. For SQL Server, the released migration normalizes copied `datetime2(6)` values to the Core `datetimeoffset(7)` mapping, preserves legacy inline BLOB bytes and retains the wider physical reflog-message column.

Before enabling writers:

- compare every recorded inline BLOB checksum and reflog row;
- reopen and traverse existing repositories;
- verify a ref update creates a queryable reflog;
- verify a sufficiently large non-compressible payload can use `git_pack_chunks` while small payloads may remain inline by design;
- archive Flyway output and deployed artifact checksums.

Rollback is database restore plus the previous application artifact; no reverse migration is assumed.

## Prepared external-Core runtime boundary

After the database-specific adoption or installation has established the normal Core Flyway history, application code can explicitly construct the prepared runtime boundary with:

```java
ServerPersistenceContext context =
    HibernateConfig.createExternalPersistenceContext(properties);
```

This factory is deliberately separate from the normal server startup path. Before Hibernate starts, it requires:

- `hibernate.hbm2ddl.auto=validate` exactly;
- the normal `jgit_storage_hibernate_core_schema_history` table in the selected schema;
- no unsuccessful row in that history;
- at least one successful versioned Core migration.

The subsequent Hibernate bootstrap performs the definitive physical-schema validation. The startup guard neither runs Flyway nor repairs a history table.

The external context registers the released `CoreEntities` exactly once and adds only the explicitly reviewed Sandbox projections `GitCommitIndex`, `JavaBlobIndex` and `FilePathHistory`. It deliberately excludes:

- the copied `GitPackEntity` and `GitReflogEntity`, whose Hibernate entity names collide with released Core mappings;
- the copied `GitObjectEntity` and `GitRefEntity`, because the released pack/reftable backend does not maintain those legacy tables.

Repository handles are served through `ExternalHibernateRepositoryService` and closed before the application-owned `SessionFactory`. This makes the Core lifecycle executable without leaking the external implementation into REST or Smart HTTP callers.

Normal `JGitServerApplication` startup still selects the copied persistence context. Activating the external context globally remains blocked until Search, analytics and Java-analysis queries no longer depend on copied Core/query entities and their schema/data transition has independent integration evidence. No environment switch is provided that could bypass this boundary accidentally.
