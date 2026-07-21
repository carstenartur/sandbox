# Post-merge QA: planned multi-file cleanups

## Scope and evidence

This review covers the scope-expansion patch merged into the JDT UI fork as commit `37da2aee4f2013669e4466f180fb822255382b4b` and the Sandbox planning/consumer implementation merged as `61b10590146557da3cbf9e56e140c7fd18a1f734`.

Before merge, both PR heads passed their complete available validation. Sandbox passed the full Maven/Tycho/PDE reactor, Core Module Build, CodeQL, Codacy Checkstyle/SpotBugs, and test-report generation. The JDT fork passed Maven, repository PR rules, CodeQL, and Codacy after rebasing to the current fork `master`.

## Confirmed strengths

- The design reuses the existing JDT cleanup lifecycle instead of introducing a parallel `CompositeChange` API.
- Plans contain Java-model handles, binding keys, names, and semantic expectations rather than retained AST nodes.
- Every affected compilation unit owns its own `ASTRewrite`; cross-AST node editing is avoided.
- The JDT patch expands targets before preconditions, then preserves the existing parser, working-copy fixpoint, overlap handling, validation, preview, apply, and undo pipeline.
- Scope expansion is transitive, de-duplicated, cancellation-aware, project-bound, and fails atomically on invalid provider output.
- Int-to-Enum remains conservative: public/protected APIs, incomplete data flows, aliases, flags, arithmetic, arbitrary values, unresolved references, and external numeric semantics are rejected.
- JUnit coordination rejects mixed `@Rule`/`@ClassRule` lifecycle use and prevents duplicate local-plugin processing.

## Current support boundary

### Int-to-Enum

Supported automatically:

- a private closed state flow inside one compilation unit;
- a package-private state owner and proven callers when the complete required source scope is present.

Not yet supported automatically:

- public/protected API migration;
- interface/override hierarchies;
- persisted or serialized numeric identity;
- aliases, ranges, bit masks, or arbitrary integer propagation;
- new source-file creation.

### JUnit

The coordinated path currently covers named JUnit 4 `ExternalResource` implementations and proven `@Rule`/`@ClassRule` fields in the same or other compilation units. Test hierarchies, suites/runners, external method sources, shared assertion helpers, and dependency removal remain separate stages.

### Execution surfaces

- Explicit IDE cleanup on a complete selection can use the existing lifecycle.
- Automatic related-file discovery requires the patched `org.eclipse.jdt.ui` product/profile (#1209).
- Save actions remain intentionally single-file.
- The current headless application still executes one refactoring per file; atomic project transactions are #1210.

## QA findings and gates

### Immediate correctness and ergonomics

- Scope expansion is currently driven by top-level cleanup enablement. Local-only profiles can therefore request full-project analysis. Gate expansion by the actual coordinated sub-option (#1228).
- Most rejected candidates are silent. Show added scope and stable rejection reasons in preview/report output (#1214).

### Lifecycle proof

- Add end-to-end atomic apply/undo, stale-plan, save-action, provider-failure, and multi-project tests (#1213).
- Reconcile/build transformed sources so tests prove semantic validity, not only expected text (#1222).

### Scale and source boundaries

- Replace unconditional full-project expansion with candidate-driven JDT Search closure (#1212).
- Define production/test/generated source-root policy (#1224).
- Add time, memory, source-count, and cancellation limits (#1221).

### Compatibility and generated code

- Define public/numeric compatibility modes before widening enum migration (#1216).
- Standardize nested enum visibility and qualification (#1223).
- Centralize generated-name collision handling (#1225).
- Classify cleanup impact so project-wide migrations cannot masquerade as local save-safe cleanups (#1226).

### Distribution and release evidence

- Publish the patched JDT UI bundle through a pinned, reproducible p2/product path with stock-product regression (#1209).
- Add installation/startup/update-site smoke tests before describing release builds as production-suitable (#1215).
- Associate post-merge squash commits with exact tests and published artifacts (#1218).
- Generate a capability inventory to prevent version/status documentation drift (#1219).

## Prioritized implementation order

1. #1228
2. #1213 and #1222
3. #1212, #1224, and #1221
4. #1214 and #1226
5. #1216, #1223, and #1225
6. #1217
7. #1210, #1209, and #1215
8. #1218 and #1219

The umbrella roadmap is #1229.

## Conclusion

The merged code is a technically sound first implementation of planned multi-file cleanups for existing Java compilation units. It is suitable for controlled experimental use and continued development. Advertising transparent, scalable, automatic project-wide cleanup support should wait for option gating, lifecycle/semantic tests, candidate-driven scope discovery, and product-delivery validation.
