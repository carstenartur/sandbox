# Post-PR-cleanup project QA — 2026-07-21

## Scope

This assessment was performed after every open pull request in `carstenartur/sandbox` and `carstenartur/eclipse.jdt.ui` had been reviewed, merged, or closed. It rechecks the effective target branches rather than trusting PR descriptions.

Reviewed areas:

- open pull-request state and retained issue tracks;
- build and test baseline;
- cleanup scope/lifecycle behavior;
- XML cleanup semantics and preferences;
- CLI and product/update-site boundaries;
- documentation correctness and inventory;
- remaining release and production-readiness gates.

## Repository state

At the start of the second QA:

- Sandbox `main`: `c1a8cf018c108090786ace8f5dacb3adfd42bfe5` (#1237 merged);
- JDT UI fork `master`: `37da2aee4f2013669e4466f180fb822255382b4b` (#94 merged).

At the end of PR review, both repositories had zero open pull requests. The complete decision record is [`open-pr-review-2026-07-21.md`](open-pr-review-2026-07-21.md).

## Build and test baseline

The previously merged implementation heads passed the full Maven/Tycho/PDE reactor, CodeQL, Codacy, and test inventory. The documented baseline remains:

| Item | Value |
|---|---|
| Java | 21 |
| Eclipse target | 2025-12 |
| Tycho | 5.0.3 |
| Test inventory before this QA branch | 1,930 total / 1,864 enabled / 66 disabled |

This QA branch adds focused tests for XML indentation and JUnit scope expansion. Final branch CI is required before merge and becomes the new source of truth for totals.

## Defects fixed by this QA

### XML indentation preference

**Finding:** `XML_CLEANUP_INDENT` was read by the cleanup and passed to `SchemaTransformationUtils`, but the transformer always used `indent=yes`. The stylesheet also requested indentation and preserved formatting-only whitespace, contradicting the compact-default documentation.

**Correction:**

- set `OutputKeys.INDENT` from the actual preference;
- make the stylesheet compact by default;
- strip formatting-only whitespace before serialization;
- preserve meaningful text, comments, attributes, namespaces, and processing instructions;
- add tests proving compact/indented layout difference, semantic equality, and compact default behavior.

**Documentation:** XML architecture and roadmap were rewritten around the actual JDT and standalone PDE paths, security properties, tests, and remaining limitations.

### JUnit local options caused full-project expansion

**Finding:** `JUnitCleanUpCore#discoverAdditionalCompilationUnits` returned every project source unit whenever the top-level JUnit cleanup was enabled. A local assertion or annotation migration therefore paid the cost and UI impact of project-wide analysis even though only `RULEEXTERNALRESOURCE` currently needs coordinated files.

**Correction:**

- return an empty additional scope unless `JUnitCleanUpFixCore.RULEEXTERNALRESOURCE` is in the computed fix set;
- retain full-project scope for the coordinated named `ExternalResource` migration;
- add positive and negative scope-provider regression tests.

**Remaining boundary:** Int-to-Enum still has one option for both local and project-wide behavior. It needs an explicit project-wide option or candidate-driven discovery (#1228, #1212).

## Cleanup architecture assessment

### Confirmed sound properties

- planning uses the established cleanup lifecycle;
- plans contain handles/binding keys rather than retained AST nodes;
- each compilation unit owns its rewrite;
- the patched JDT UI target expansion feeds the existing preview/apply/undo pipeline;
- invalid provider output and stale expected targets fail instead of silently producing partial changes;
- save actions remain intentionally single-file.

### Remaining high-priority gates

1. Int-to-Enum minimal/candidate-driven scope (#1228, #1212).
2. Atomic apply/undo, stale-plan, cancellation, save-action, and multi-project lifecycle tests (#1213).
3. Semantic compilation/reconcile checks after positive multi-file transformations (#1222).
4. Source-root/generated-source and resource-limit policies (#1224, #1221).
5. Scope/rejection diagnostics and impact classification (#1214, #1226).

## CLI assessment

`sandbox_cleanup_application` still creates one `CleanUpRefactoring` per file. It can execute local cleanups, but it cannot yet guarantee one atomic project transaction for a coordinated plan. Grouping, snapshots, rollback, deterministic diffs, and machine-readable reports remain #1210.

Documentation correctly avoids claiming headless multi-file support.

## Product and p2 assessment

The standard CI primarily validates the development reactor. Packaging profiles and snapshot publication do not yet prove clean installation, singleton resolution, startup, and a cleanup smoke test for the published result.

Remaining gates:

- patched JDT UI product/update-site delivery (#1209);
- stock and patched install/startup smoke tests (#1215);
- exact squash-commit/test/artifact provenance (#1218).

Production-readiness wording remains intentionally withheld.

## Documentation QA

Updated in this QA:

- root correctness audit;
- documentation inventory;
- QA report index;
- XML architecture and roadmap;
- open PR decision record;
- this second project QA report.

Removed from current documentation:

- stale Tycho 5.0.2-as-current statements;
- Int-to-Enum-as-placeholder wording;
- obsolete missing-README claims;
- XML indent preference claims that were not implemented;
- ambiguous separation between JDT and standalone PDE execution.

Long-term version/capability drift prevention remains #1219.

## Additional verified limitations

### XML cleanup

- unconditional UTF-8 decoding/writing instead of Eclipse resource charset policy;
- no dirty-editor/external-modification integration test;
- no automatic PDE/schema validation after rewrite;
- complete-document memory use and temporary-file round trip;
- fixed eligible locations.

### Disabled tests

The 66 disabled tests remain concentrated in JUnit and functional conversion. They include both unsupported features and known safety bugs. Public descriptions must continue to reflect enabled, verified behavior rather than total test declarations.

### Upstream JDT contribution work

Closing unsafe fork PRs does not close the underlying upstream issues. New branches should start from current upstream `master`, exclude fork infrastructure, use stable bindings/signatures, integrate with working copies/LTK, and include exact source, compile, undo, dirty-editor, cancellation, and UI-cost tests.

## Priority after this QA

### P0

- merge this branch only after full Maven/Tycho, CodeQL, Codacy, and test-report success;
- implement Int-to-Enum scope separation/minimal discovery;
- add lifecycle and semantic multi-file integration tests.

### P1

- headless project transaction;
- scope diagnostics and resource/source-root policy;
- XML charset/conflict/validation tests;
- product and update-site smoke tests.

### P2

- broader JUnit migration;
- enum compatibility/naming/visibility policy;
- generated capability/documentation inventory.

## Conclusion

The PR backlog is empty and no unsafe branch was merged merely because it compiled. The second QA found and fixed two concrete product defects—XML indentation preference behavior and unnecessary JUnit project expansion—and synchronized their documentation and tests. The project remains a coherent experimental platform with explicit, testable gates before broader automatic cleanup or production-use claims.
