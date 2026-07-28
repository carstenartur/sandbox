# JGit storage legacy-schema adoption

Sandbox currently contains the pre-library JGit/Hibernate schema. Do not switch the server to the released `jgit-storage-hibernate-core` factory and do not run adoption DDL until the existing database has passed the released read-only validator.

## Supported database boundary

The published pre-library adoption migrations in `jgit-storage-hibernate-core:0.1.14` support PostgreSQL and HSQLDB. The maintenance command therefore fails before opening H2, MSSQL or any other JDBC family. A schema-shape check alone must not be presented as migration eligibility when no matching published Flyway location exists.

The shaded server JAR contains the PostgreSQL and HSQLDB JDBC drivers required by these two supported paths. The existing MSSQL driver remains packaged only for the copied backend until the later production cut-over is complete.

## Safety boundary

The preflight command:

- opens a plain JDBC connection using the normal `JGIT_DB_*` environment variables;
- accepts only PostgreSQL or HSQLDB, matching the published legacy-adoption migrations;
- requires `;ifexists=true` in an HSQLDB URL so a mistyped file path cannot create a new database;
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
3. Record repository and pack counts, ordered SHA-256 checksums of every `git_packs.data` value, refs and reflog rows.
4. Use a restored production-like database first. Do not make the initial attempt against the only production copy.
5. When the current deployment uses MSSQL, first decide between a verified PostgreSQL data transfer and implementing generic MSSQL support upstream. Do not run PostgreSQL migration SQL against MSSQL.

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

- `JGIT_DB_URL`, beginning with `jdbc:postgresql:` or `jdbc:hsqldb:`;
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
- duplicate logical pack identities;
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
- `pack_extension` values longer than 32 characters;
- duplicate `(repository_name, pack_name, pack_extension)` identities.

Resolve rejected rows from application knowledge or restore a known-good backup. The preflight never selects a duplicate row or truncates a value automatically.

## Next step after a successful preflight

The next implementation slice must run the matching PostgreSQL or HSQLDB legacy-adoption Flyway location from `CoreSchemaMigrations`, establish the normal Core schema history, start Hibernate with `hibernate.hbm2ddl.auto=validate`, compare all recorded BLOB checksums and reflogs, and exercise repository traversal and ref updates before writers are enabled. Rollback is database restore plus the previous application artifact; no reverse migration is assumed.
