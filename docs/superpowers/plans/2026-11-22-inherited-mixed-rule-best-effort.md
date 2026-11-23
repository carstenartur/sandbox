# Inherited mixed Rule best-effort implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve unsupported inherited mixed Rule components in best-effort mode while migrating independent JUnit tests.

**Architecture:** Extend the existing bound hierarchy analysis and per-unit gap propagation. Replan coordinated ExternalResource migration with exclusions for retained components. The same gaps protect those units during per-file execution and supply documentation markers. Strict planning and local gap analysis use their established eligibility.

**Tech Stack:** Java 25 build, Eclipse/JDT 4.41, Tycho 5.0.4, native JUnit tests.

**Spec:** `docs/superpowers/specs/2026-11-22-inherited-mixed-rule-best-effort-design.md`

## Global Constraints

- Build with Java 25 against the repository baseline.
- Use native Eclipse/JDT JUnit tests and existing compiler/lifecycle assertions; add no Python automation, warning suppression, ignored failures or screenshot changes.
- Aim for at most 1,500 changed text lines; the existing PR's documented policy exception covers necessary native/shared-helper evidence. Stop before 2,000 lines and keep corrections on the existing ordinary PR against current main.
- Strict mode must retain its fatal mixed-lifecycle precondition and make no edits.
- Preserve compiler diagnostic identities and multiplicities, all selected/dependent units, and exact source restoration after Undo.
- Do not implement automatic mixed-lifecycle adapters or depend on unmerged #1971.

## Review Focus

- Different concrete fixtures sharing a source ancestor must be recognized as one mixed component.
- Inherited rule consumers with no local field declaration must retain their JUnit 4 lifecycle and discovery.
- Two unrelated fixture hierarchies must not be linked solely by the binary ExternalResource base.
- Direct mixed use, source test ancestors, and a second fixture used by a blocked test must remain protected together with their connected consumers from per-file fallback rewrites.
- Independent fixtures and their rule owners in separate compilation units must still migrate together. A retained Rule can identify a second source fixture through parenthesized construction or a typed factory initializer.
- Use a resolved source initializer and its Rule or ClassRule kind as retention evidence. An unsupported initializer and its selected fixture stay together under their own reason code when the ordinary rewrite cannot migrate the field.
- Repeated best-effort application must not duplicate markers or allow later partial migration.
- JUnit 4 assumptions must retain their exception contract for Vintage consumers and shared helpers; an eager, source-snapshot-bound group decision must work in the stock per-project lifecycle and without optional scope expansion.

---

### Task 1: Isolate the complete mixed component

**Files:**
- Modify: `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitBestEffortSupport.java`
- Modify: `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitMultiFilePlanner.java`
- Modify: `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/fix/JUnitCleanUpCore.java`
- Test: `sandbox_junit_cleanup_test/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitBestEffortSupportTest.java`
- Test: `sandbox_junit_cleanup_test/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitBestEffortMigrationCleanUpTest.java`
- Modify: `sandbox_junit_cleanup_help/html/best-effort.html` only to describe the repaired behavior.

**Interfaces:**
- Consumes: existing `JUnitBestEffortSupport.analyze(IJavaProject, ICompilationUnit[], EnumSet<JUnitCleanUpFixCore>, IProgressMonitor)` and its `Analysis`, `Gap`, `SourceType` and `ResourceUsage` contracts.
- Produces: per-unit `MIXED_RULE_LIFECYCLE` gaps for a proven mixed source component and `EXTERNAL_RESOURCE_INITIALIZER_UNSUPPORTED` gaps for a connected initializer contract that requires retention. Core passes those unit handles to coordinated ExternalResource replanning and protects the units in per-file execution. The legacy defer flag reports that a component was retained.

- [x] **Step 1: Add the native failing regression.**
  Add sibling instance/class fixtures extending one source fixture base, their rule-owning tests, a test inheriting an instance rule, and an independent ordinary JUnit 4 test. Assert the full affected-unit gap set and deferred coordinated planning. Include a source test ancestor contributing JUnit 4 hooks without its own Rule field, and a second fixture used by a blocked owner and another test. Add a separate unrelated-hierarchy case that remains unblocked. Strengthen direct mixed-use coverage to include consumer units, replacing the old one-gap-only expectation with meaningful component assertions.

- [x] **Step 2: Run the focused analysis tests before implementation.**
  Use Java 25 and the native test runtime; retain the exact failing test and diagnostic. The expected failure is missing inherited mixed gaps/defer, not missing dependencies or an invalid fixture.

- [x] **Step 3: Implement source-hierarchy classification and component propagation.**
  Project instance/class uses onto the source ancestors whose hooks coordinated migration would change. Propagate protection through participating fixture descendants, rule-consuming test ancestors/descendants and other Rule fixtures connected through a blocked owner, using resolved source bindings until the affected component is closed. Reuse existing gap ordering and marker generation. Do not join independent fixtures through the binary JUnit base; do not alter strict eligibility, public method signatures or unrelated cleanup behavior.

- [x] **Step 4: Prove actual cleanup behavior.**
  In native cleanup tests, verify that strict mode rejects the mixed component without writing sources. In best effort, Apply must produce markers while preserving coupled Rule/ClassRule, fixture inheritance/hooks and JUnit 4 test/lifecycle annotations. Verify Jupiter migration of the independent test and its separately declared fixture, diagnostic identities and multiplicities, repeat application for idempotence, and Undo for byte-exact restoration. Include the inheriting test with no local Rule declaration and exercise a second fixture through parenthesized and typed factory initializers.

- [x] **Step 5: Run the focused test classes and inspect the diff.**
  Expected: analysis and actual-cleanup tests pass, no new compiler diagnostics, exact Undo, unchanged strict behavior. Standard Maven entry: `xvfb-run -a ./mvnw -pl sandbox_junit_cleanup_test -am -Dtest=JUnitBestEffortSupportTest,JUnitBestEffortMigrationCleanUpTest -Dsurefire.failIfNoSpecifiedTests=false test`. Use the existing bounded native runtime if local reactor provisioning is unavailable, verify the compiled source identity and state the dependency limitation. Do not claim an unrun Maven reactor passed.

- [x] **Step 6: Document and commit the coherent repair.**
  Add a short help paragraph explaining retained inherited mixed components in best effort. Run `git diff --check`, measure changed text lines against the recorded main base, and commit the code/tests/help. Report exact commands, RED/GREEN results and remaining hosted-CI needs. Root owns independent review, remote publication and merge.

### Bounded post-publication correction: retained assumptions

- [x] Reproduce direct and separate-helper assumption outcome changes with real cleanup Apply before implementation; retain the native RED and two independent no-retained controls.
- [x] Remove `ASSUME` from the safe-operation allowlist and require eager complete source-group compatibility before migrating assumptions in ordinary helper units. Prove actual annotation/runner conversion and account for binary test ancestors. Retain `ASSUME_OPTIMIZATION`, independent migrations and strict behavior.
- [x] Verify native abort/failure counts, both project orders, unselected/read-only callers, source/binary ancestors, retained timeout/runner contracts, a subsequent closed group, complete diagnostics and exact Undo. Replay exported actual Apply sources with the second actual engine family.
- [x] Run the existing 111 affected cases with the new regressions, document exact source/artifact identities and the bounded report, and commit this correction locally for one scoped independent review.
- [x] Address the confirmed review omission of generated/derived consumers with public, protected or multi-statement helpers. Reproduce native RED, share the complete observational source inventory between permission and freshness, then repeat the existing qualification and actual-source JUnit 6 replay without broadening edit eligibility.
