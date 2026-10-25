# Pre-library HSQLDB fixture

`copied-hsql-prelibrary.sql` is a frozen SQL/BLOB snapshot created by Sandbox's
copied production backend, whose source is preserved at commit
`0e6e48bf1c95fa45f367f2ed1988959bde57caf7`. It contains the real pre-library
Hibernate table/index definitions and rows for repository `copied-example`.
The seed is the original `ReleasedSchemaAdoptionHsqlTest`: one commit containing
`readme.txt` with `historical needle`, `refs/heads/main`, and an explicit SQL reflog
entry `legacy reflog preserved`. The author and commit time are fixed in that test.
The immutable commit is `7b7b9e9852d8d97bd49feebe76c28a15a4a7cac7`.

The capture used HSQLDB 2.7.4, Hibernate ORM 7.4.12.Final and JGit
7.8.0.202609011348-r. Schema statements came from HSQLDB's `SCRIPT` output; row
values were exported with JDBC, preserving timestamps and every BLOB byte as a
hexadecimal SQL literal. Database/user settings were excluded. Before inclusion,
the SQL was restored into a separate empty database and the copied backend read
the original Git commit and reflog from the restored packs.

The adoption test verifies this file's SHA-256 before loading it. It then runs the
published Core adoption/normal migrations and Search installation, preserving
pack checksums, refs, complete reflog rows and the archived copied projection.
Its setup no longer compiles or starts the copied backend. Keep this fixture
unchanged when removing that backend; a deliberate new legacy fixture must record
its provenance and update the pinned checksum together.
