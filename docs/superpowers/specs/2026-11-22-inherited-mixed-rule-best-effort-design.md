# Best-effort isolation for inherited mixed Rule lifecycles

## Intent and evidence

Repair the existing opt-in best-effort policy while preserving strict migration behavior. Reference PR #1955, run 45041401147, UI artifact 14772927564, contains a concrete failure on `org.eclipse.jdt.ui.tests.refactoring`: `bestEffort: true`, `coordinatedExternalResourceDeferred: false`, and fatal `MIXED_RULE_LIFECYCLE` before any edit.

`JUnitBestEffortSupport` groups fields by the concrete fixture binding. `JUnitMultiFilePlanner` also considers the source ancestors whose hooks it would rewrite. Different instance/class fixtures sharing a source ancestor therefore escape best-effort isolation. A diagnostic only on the shared fixture is insufficient: per-file rewriting protects only compilation units with their own gaps.

## Required behavior

- Analyze the same bound, source-defined ExternalResource hierarchy used by coordinated planning. The common binary JUnit `ExternalResource` base alone must not link unrelated fixtures.
- For an unsupported mixed lifecycle, mark the complete affected source component: shared fixture ancestors, participating fixture descendants, rule-owning tests, source test ancestors contributing lifecycle/discovery, and inheriting consumers. Include a second fixture used by a blocked test and its other consumers whenever rewriting it alone would break the retained Rule contract; propagate this protection until the connected component is closed.
- In explicit best-effort mode, preserve that component's JUnit 4 fixture inheritance, hook signatures, Rule/ClassRule fields, test discovery and lifecycle contracts. Add deterministic, idempotent actionable documentation markers. Existing independently safe assertion/assumption rewrites may continue.
- Continue ordinary migration in genuinely independent tests and fixture components.
- Strict mode must retain its fatal mixed-lifecycle precondition and make no edits.
- Preserve compiler diagnostic identities and multiplicities, all selected/dependent units, and exact source restoration after Undo.

## Approach and boundaries

Extend hierarchy classification and propagate per-compilation-unit gaps through source bindings. In best effort, use the resolved source fixture and the field's Rule or ClassRule kind as retention evidence for parenthesized constructions and typed factory calls. Ordinary bindings still determine unsupported-rule classification and rewrite eligibility. The established exact-binding protection also applies when the ordinary binding supplies the only fixture identity. Use a separate reason code to retain a source fixture with one lifecycle when the existing Rule rewrite cannot migrate its initializer and field together. Use the gaps from both retention cases to exclude affected Rule fields and fixture chains during coordinated replanning and to protect the same units during per-file execution. Existing analysis callers stay compatible, and strict planning and local gap eligibility follow their established rules.

Only downgrading the fatal diagnostic would permit partial lifecycle rewrites. Globally disabling all JUnit migration would suppress independent progress. Automatic per-use lifecycle adapters require additional runtime, ordering, field-shadowing and dependency proof and are a separate follow-up after #1971. This repair does not claim that capability.

## Constraints and acceptance

Build with Java 25 against the repository baseline. Use native Eclipse/JDT JUnit tests and existing compiler/lifecycle assertions; add no Python automation, warning suppression, ignored failures or screenshot changes. Keep the complete change below 1,500 changed text lines and create an ordinary PR against current main. The user has already authorized continued repairs and qualified merges.

Acceptance requires failing-before/passing-after regressions for sibling fixtures, inherited consumers, source test ancestors contributing hooks, and a second fixture shared with a blocked test. An independent test must still migrate, unrelated fixture hierarchies must stay independent, strict mode must make no edits, repeated best-effort application must be idempotent, diagnostics must be preserved and Undo must restore exact sources.
