# Released runtime cutover

The runtime integration is under development. The first implementation slice supplies
application report formulas and full-tree API comparison. Normal startup has not switched yet.
Only merged prerequisites are consumed; the final change will wire the released Core, Search
and Java analysis modules, validate the schema and remove the copied generic implementation.

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
