# Source-search REST boundary

`SandboxSourceSearch` supplies application-owned source-file results, report rows,
API changes and all-version path search. `SearchResource` no longer reads copied
ORM entities, constructs a query service per request or owns a model directly.
Its injected constructor borrows commit history, file history and source search
from their application context. The context must close those services after
requests finish; servlet shutdown does not close borrowed services.

The compatibility constructors create `CopiedSandboxSourceSearch`, which delegates
to existing query behavior and copies results into immutable records/lists. Only
that servlet-owned adapter closes its own optional legacy encoder on destruction;
it never closes the native session factory. This temporary adapter is removed
when the released runtime implementation is selected.

The boundary preserves existing REST behavior, including these explicit details:

- Source pages contain the same ten file fields and page-length `totalResults`.
- API comparisons retain grouped added/removed/changed files and their eight-field
  file views. The implementation selects the comparison semantics; this slice
  still delegates to the old provenance-based query.
- Author/type, complexity and test-ratio rows become typed values without changing
  their public field names, numeric types or nullable text values.
- Input sanitization, module/file-type parameters, pagination defaults/caps,
  path prefix dispatch, migration reports and per-commit path responses remain.
- Semantic/hybrid/similar responses report offset zero. The existing requirements
  for `q` on similar and complexity routes remain until an explicit API change.

HTTP contract tests exercise both `/api/search` and `/api/v1/search` aliases with
an injected boundary. Existing real-database server tests exercise the transitional
adapter. This independent slice does not select released startup or implement a
second parser, index or persistence backend.
