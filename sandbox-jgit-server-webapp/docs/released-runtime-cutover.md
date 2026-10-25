# Released runtime cutover

Normal startup uses published **0.11.8 Core, Search and Java Analysis**. The server
registers only the released Core and Search entities. Text, path, report, API and
optional semantic routes use application DTOs through both `/api` and `/api/v1`.
The copied storage/parser/query implementation is removed; the OSGi bridge consumes
Core only. Existing production databases still require explicit operator adoption.

## Startup and ownership

Normal startup requires validated Core/Search Flyway histories and
`hibernate.hbm2ddl.auto=validate` (the default). `--initialize-empty-schema` is an
exclusive, explicit first-start operation: it refuses a target containing any table
or view, installs both released streams and starts the server. Subsequent starts
omit the flag. It never adopts or repairs a populated legacy database.

The context owns one shared source/path/API generation cache and one lazy encoder.
Queries hold an existing-only repository lease and a generation pin until DTO mapping
finishes. Accepted pushes, rebuild, deletion and refresh invalidate affected data.
Shutdown drains readers, then closes generations, model, repositories and Hibernate;
SIGTERM and the end/interruption of the main join use that same application shutdown.

## Input isolation and search compatibility

Malformed historical properties retain their raw-text facet and report row, without
invalid decoded properties metadata. Released search-document validation omits only
an invalid facet, or an invalid document when its metadata cannot be indexed. Analyzer
term failures are isolated with public index probes on the exceptional path. Reports
retain the complete eligible corpus. These omissions are logged by blob identity and
field; raw file contents are not logged. I/O, interruption and fatal failures remain
errors. Retained historical bad files cannot block unrelated source queries after
later deletion.

Nonempty whitespace module/file-type filters remain exact matches. The adapter pages
through the public index in bounded batches and applies offset/limit after that exact
metadata match. Whole qualified-name values retain the established analyzer semantics;
they are not split or truncated to fit an index keyword.

`ReleasedRuntimeStartupH2Test` exercises the default factory, real Smart HTTP, both REST
aliases, restart and blocked-reader shutdown. `ReleasedPackagingIT` checks the shipped
JAR's classes and performs explicit initialization, SIGTERM and normal restart in
separate JVMs. The consumer workflow supplies real Elasticsearch 8.19.12; local builds
without `JGIT_TEST_ELASTICSEARCH_HOSTS` skip only that backend test.

## API comparison

Both requested commit trees are analyzed in their complete sibling-source context. Added and
removed files are determined by path, including unchanged blobs at multiple paths. The public
`JavaSemanticDiff` compares declarations; binding-quality changes alone do not change an API.
Sandbox retains the `methods changed`, `signatures changed`, `visibility changed` descriptions
when those file facets differ. Otherwise semantic declaration changes (including modifiers
and annotations) use the conservative `declarations changed` description: heuristic matches
do not establish exact renames. Public-only filtering retains the existing file-level
visibility rule. Ordering is deterministic by path. Unknown commits produce a read error.
This corrects the previous comparison of canonical blob provenance rather than whole trees.
Semantic comparison is scoped to each retained path using its already analyzed declarations,
so an unmatched declaration in a deleted file cannot consume an unrelated file's exact match.
A declaration transferred between retained files changes both file reports; whole-file moves
are represented by the added/removed path lists.

## Historical reports

Reports count one canonical historical row per repository/blob, including non-Java rows.
They do not describe only the current checkout or count every declaration within a file.
Author/type and package groups include null values. Complexity averages line counts at exact
author timestamps. Monster classes use an inclusive threshold and descending line counts.
Dead-code candidates use the existing import-name and immediate-package wildcard heuristic,
including self-imports; this is not call-graph reachability. Paging follows filtering.
The test proxy counts rows whose annotations contain the JUnit4/5 fully qualified Test names.
Deprecation reports retain the exact short `Deprecated` newline-token rule. Import migration
retains SQL LIKE `%` and `_` wildcard semantics at the start of import lines. Empty summaries
retain null dates. Null groups sort first; ties retain deterministic corpus order.
