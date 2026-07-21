# Sandbox project QA — 2026-07-21

## Executive assessment

The repository builds and tests successfully on its supported Java 21 / Eclipse 2025-12 / Tycho 5.0.3 baseline. The planned multi-file cleanup implementation and its JDT UI scope-expansion dependency were reviewed, fully validated, and squash-merged. The project has a strong experimental test and CI foundation, but it should not yet describe the published release channel or automatic project-wide cleanup behavior as production-ready.

The primary risks are not immediate compile failures. They are product/release evidence, project-wide cleanup scale and ergonomics, incomplete end-to-end lifecycle tests, headless transaction boundaries, and documentation/capability drift.

## Actions completed during QA

- Squash-merged `carstenartur/eclipse.jdt.ui#94` as `37da2aee4f2013669e4466f180fb822255382b4b`.
- Squash-merged `carstenartur/sandbox#1207` as `61b10590146557da3cbf9e56e140c7fd18a1f734`.
- Closed obsolete/superseded PRs:
  - Sandbox #1205;
  - JDT UI fork #68;
  - Sandbox #989 and #952 are superseded by the repository QA correction PR.
- Corrected JDT UI issue #67 so it no longer claims the superseded #68 design was merged.
- Closed completed implementation issue #1206 and linked the deliberate follow-ups.
- Added a dependency-ordered QA roadmap (#1229) and bounded implementation issues for the findings below.

## Build and dependency baseline

Verified source-of-truth values:

| Component | Current baseline |
|---|---|
| Java | 21 |
| Eclipse target | 2025-12 |
| Tycho | 5.0.3 |
| Default Maven profile | development reactor, excluding product/update-site packaging |
| Full product/update site | `-Pproduct,repo` |

The contributor and README documentation contained stale Tycho 5.0.1/5.0.2 references. The QA correction PR aligns them with the POM property and the effective build.

## Test inventory

The merged multi-file implementation's final test report discovered:

- 1,930 tests in 15 test modules;
- 1,864 enabled tests;
- 66 disabled tests.

The disabled tests are concentrated in:

| Module | Total | Enabled | Disabled |
|---|---:|---:|---:|
| `sandbox_junit_cleanup_test` | 650 | 614 | 36 |
| `sandbox_functional_converter_test` | 545 | 519 | 26 |
| `sandbox_common_test` | 360 | 358 | 2 |
| `sandbox_jface_cleanup_test` | 15 | 14 | 1 |
| `sandbox_platform_helper_test` | 6 | 5 | 1 |

This is acceptable for an experimental repository only when disabled behavior is explicitly tracked and public capability descriptions do not imply those paths are complete. The README JUnit description is therefore narrowed to the implemented scope. Future work should consolidate capability/test status through #1219.

## CI and security

The merged implementation heads passed:

- full Maven/Tycho/PDE reactor;
- Core Module Build;
- CodeQL;
- Codacy Checkstyle and SpotBugs;
- test inventory/report generation.

The repository has a broad workflow set, including two mining workflows that were missing from README badges. The QA correction adds those badges.

A provenance gap remains after squash merges: the merge commit SHA differs from the fully tested PR head, and it is not always straightforward for tools/users to associate the exact `main` commit with test reports and published artifacts. This is tracked in #1218.

## Product, update site, and release claims

The normal Java CI intentionally excludes product/update-site profiles. Snapshot deployment builds `-Pproduct,repo` with tests skipped, then publishes the p2 repository. This packages the product, but does not by itself prove clean installation, bundle resolution, startup, or cleanup execution in the published artifact.

The previous README wording described release builds as suitable for production while all plugins were also labeled experimental. The QA correction replaces that claim with a versioned-release description. Automated installation/startup/update-site smoke tests and promotion gates are tracked in #1215.

The optional patched `org.eclipse.jdt.ui` product path is separate and requires pinned, reproducible p2 delivery, stock-product regression, singleton/version checks, and smoke testing (#1209).

## Cleanup architecture QA

### Positive findings

- The implementation reuses the established JDT cleanup/refactoring lifecycle.
- Per-file changes remain owned by their compilation unit and are combined by LTK.
- Plans store stable Java-model/binding identities rather than AST nodes.
- Invalid scope-provider output and stale required targets fail instead of silently applying partial changes.
- The JDT patch preserves existing parser, working-copy, overlap, preview, apply, and undo infrastructure.

### Immediate defect/ergonomics finding

Both coordinated consumers currently request every project source unit when their top-level cleanup is enabled. JUnit does so even if the enabled sub-options do not include the only implemented coordinated case (`RULEEXTERNALRESOURCE`). Immediate option gating is #1228; minimal candidate-driven search is #1212.

### Required lifecycle evidence

The current tests prove planning and expected transformed text, but critical end-to-end guarantees still need explicit tests:

- atomic perform/undo across files;
- stale-plan rejection after earlier cleanup edits;
- save-action isolation;
- cross-project/missing/deleted provider targets;
- multi-project plan isolation;
- semantic reconcile/build after transformation.

These are #1213 and #1222.

### Scale and workspace boundaries

Full-project AST planning needs candidate-driven scope, source-root policy, cancellation/resource limits, and performance regression data (#1212, #1224, #1221).

### Compatibility and generated source

Public/numeric enum migration, nested enum visibility/qualification, generated-name collisions, and impact classification must be designed before extending the automatic cleanup beyond closed private/package-private flows (#1216, #1223, #1225, #1226).

### JUnit scope

The coordinated path currently covers named JUnit 4 `ExternalResource` types and proven rule fields. Hierarchies, suites/runners, parameterized providers, shared helpers, and dependency cleanup remain staged work (#1217).

## Headless and distribution QA

`sandbox_cleanup_application` creates one `CleanUpRefactoring` per file. CHECK/DIFF modes perform and restore one file at a time, so they cannot provide a project-wide atomic transaction for planned multi-file cleanups. The required grouping, snapshot, rollback, deterministic patch, and report changes are #1210.

The README and contributor reference are corrected so they no longer imply current CLI support for coordinated multi-file transactions.

## Documentation and repository hygiene

Findings corrected in the QA PR:

- stale Tycho version references;
- contradictory release stability language;
- missing mining badges;
- obsolete blanket prohibition of shared cleanup lifecycle bases;
- understated Int-to-Enum and overstated JUnit/CLI capability descriptions;
- obsolete Copilot lessons redirect;
- missing merged-commit and QA roadmap references.

Long-term generated capability/version consistency is #1219.

## Priority and release gates

### P0 — before advertising automatic project-wide cleanup support

1. #1228 — option gating;
2. #1213 and #1222 — atomic lifecycle and semantic tests;
3. #1212 — candidate-driven scope discovery.

### P1 — before broader project use

- #1224 and #1221 — source-root/resource policies;
- #1214 and #1226 — diagnostics and impact classification;
- #1210 — headless project transactions.

### P2 — before broadening transformation semantics

- #1216, #1223, #1225 — enum compatibility and generated-source policy;
- #1217 — wider coordinated JUnit migration.

### Distribution/release gates

- #1209 — patched JDT UI p2/product delivery;
- #1215 — installation/startup/update-site verification;
- #1218 — post-merge artifact provenance;
- #1219 — generated capability inventory.

The umbrella ordering is #1229.

## Final conclusion

The repository is in a coherent, buildable experimental state. The merged multi-file cleanup architecture is technically sound for its deliberately narrow initial cases, and the associated PRs were appropriate to merge. The project now has explicit evidence and issue boundaries for the remaining risks. Production-readiness claims should remain withheld until the P0 lifecycle/scope gates and distribution smoke tests are complete.
