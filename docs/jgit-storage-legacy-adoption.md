# JGit storage legacy-schema adoption

Sandbox currently contains the pre-library JGit/Hibernate schema. Do not switch the server to the released `jgit-storage-hibernate-core` factory and do not run adoption DDL until the existing database has passed the released read-only validator.

## Supported database boundary

The published pre-library adoption migrations in `jgit-storage-hibernate-core:0.1.15` support PostgreSQL, HSQLDB and Microsoft SQL Server. The maintenance command rejects H2 and every other JDBC family before opening a connection. A schema-shape check must not be presented as migration eligibility when no matching published Flyway location exists.

The shaded server JAR contains the JDBC drivers and Flyway database modules for PostgreSQL, HSQLDB and SQL Server. Released Search includes H2, PostgreSQL and SQL Server migrations; HSQLDB Search installation is available from 0.11.6. H2 remains a fresh-installation path only: no pre-library H2 adoption stream is published.

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
6. Plan a separate Search installation after Core adoption. The copied `git_commit_index` is incompatible with released Search and must be explicitly archived, including conflicting index/constraint names, before installing the released projection. Never baseline the copied table as a released Search schema.

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

## Search installation after Core adoption

Keep writers stopped. Archive the copied commit projection without deleting its rows.
For HSQLDB, the exercised table/index rename is:

```sql
ALTER TABLE git_commit_index RENAME TO sandbox_legacy_commit_index;
ALTER INDEX idx_commit_repo RENAME TO sandbox_legacy_commit_repo;
ALTER INDEX idx_commit_repo_time RENAME TO sandbox_legacy_commit_repo_time;
```

Check the actual schema for any additional conflicting constraint/index names before
running the matching database's commands. PostgreSQL and SQL Server require their
own reviewed DDL. Existing archived projection data is retained for rollback and
comparison. The copied Java/file projection tables may remain as unused legacy data; the new runtime neither registers nor reads them.

Install released Search from the selected artifact, with its separate history table:

```java
Flyway.configure()
    .dataSource(dataSource)
    .locations(SearchSchemaMigrations.HSQLDB_LOCATION)
    .table(SearchSchemaMigrations.SCHEMA_HISTORY_TABLE)
    .baselineOnMigrate(true)
    .baselineVersion(SearchSchemaMigrations.PRE_MIGRATION_BASELINE_VERSION)
    .failOnMissingLocations(true)
    .load()
    .migrate();
```

Use `POSTGRESQL_LOCATION` or `SQL_SERVER_LOCATION` for those database families.
The baseline here is **0**, allowing all new Search SQL to run alongside already
migrated Core; it does not adopt the old copied projection. After the SQL migration, initialize each **recorded existing repository identity**
once through the released `DefaultHibernateRepositoryFactory.open` API and close
the returned storage handle. This establishes the per-repository lock metadata;
the normal migration backfills lifecycle identities but does not create every lock
row. Existing-only secured readers deliberately reject this incomplete state rather
than writing missing metadata. Confirm the original refs and pack checksums during
this operator step; do not substitute guessed names or use it as a discovery API.

Reconstruct Search
from Git using the released `CommitProjectionRebuilder`, with projection writers
stopped. Refreshing Lucene from ORM rows alone does not restore missing SQL rows.

`ReleasedSchemaAdoptionHsqlTest` rehearses this sequence against a file database
restored from a pinned SQL/BLOB snapshot written by the copied backend. The
[fixture provenance](../sandbox-jgit-server-webapp/tst-rsrc/fixtures/README.md)
records its original commit and export/restore procedure. Test setup no longer
starts the copied backend. It records inline-pack SHA-256 checksums, the entire
SQL reflog and copied projection rows; closes/reopens the database; applies the
published adoption and regular streams; archives Search; validates the new schema;
reads the original ref/commit/reflog; rebuilds Search; restarts and performs a new
released-backend ref update. This is disposable HSQLDB evidence, not evidence that
any production database or another database family has been migrated.

## Prepared released runtime boundary

After the database-specific adoption or installation has established the normal Core Flyway history, application code can explicitly construct the prepared runtime boundary with:

```java
ServerPersistenceContext context =
    HibernateConfig.createPersistenceContext(properties);
```

This is the normal server startup factory. Before Hibernate starts, it requires:

- `hibernate.hbm2ddl.auto=validate` exactly and no competing Jakarta/legacy Javax database or script schema-generation actions, including `none`, or contributor-specific action overrides; the checked snapshot includes Hibernate's inherited JVM/classpath settings and is passed to bootstrap unchanged;
- a supported matching Core/Search migration pair for the JDBC family;
- completed SQL migrations in both dedicated Flyway histories, with no pending migrations, failed rows, unknown future versions or checksum mismatches;
- histories in the configured schema (no fallback to another schema).

The read-only Flyway verifier calls only `info` and `validate`. It performs no
migration, baseline, repair or schema creation. Embedded double quotes in a schema
name are rejected before connecting because the selected Flyway driver does not
escape them correctly; ordinary quoted identifiers such as `tenant-one` are tested.
Hibernate then validates the physical mappings. The context registers released Core
and Search once. Historical Java/file queries use Git-derived application generations;
no copied Core, Search or Java projection entities are registered.

Repository handles are served through `ExternalHibernateRepositoryService` and closed before the application-owned `SessionFactory`. This makes the Core lifecycle executable without leaking the external implementation into REST or Smart HTTP callers.

Normal `JGitServerApplication` startup uses this released persistence context. It does not run adoption migrations or delete archived derived tables. `--initialize-empty-schema` is only for a completely empty schema and refuses existing tables/views. Operator adoption remains necessary for deployed legacy databases; the frozen HSQLDB fixture qualifies the documented rehearsal independently of the removed copied implementation.
