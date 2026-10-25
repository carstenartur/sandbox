# Source-search REST boundary

`SandboxSourceSearch` supplies application-owned source-file results, report rows,
API changes and all-version path search. `SearchResource` no longer reads copied
ORM entities, constructs a query service per request or owns a model directly.
Its injected constructor borrows commit history, file history and source search
from their application context. The context must close those services after
requests finish; servlet shutdown does not close borrowed services.

Normal startup injects `ReleasedSandboxSourceSearch`. Compatibility constructors
and the copied query adapter have been removed. Source/path/API generations and
one optional encoder are owned by the application context, which drains pinned
readers before closing resources.

The boundary preserves existing REST behavior, including these explicit details:

- Source pages contain the same ten file fields and page-length `totalResults`.
- API comparisons retain grouped added/removed/changed files and their eight-field
  file views. Comparison now uses both complete commit trees, with the documented
  [API semantics](released-runtime-cutover.md#api-comparison).
- Author/type, complexity and test-ratio rows become typed values without changing
  their public field names, numeric types or nullable text values.
- Input sanitization, module/file-type parameters, pagination defaults/caps,
  path prefix dispatch, migration reports and per-commit path responses remain.
- Semantic/hybrid/similar responses report offset zero. The existing requirements
  for `q` on similar and complexity routes remain until an explicit API change.

HTTP contract tests exercise both `/api/search` and `/api/v1/search` aliases with
an injected boundary. Real-database and packaged-JAR tests exercise normal released
startup, both aliases, Git pushes, restart and shutdown. Parsing and generic indexes
are provided by public released library APIs.
