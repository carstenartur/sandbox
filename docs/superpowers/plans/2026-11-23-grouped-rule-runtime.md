# Grouped fixture runtime implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add explicit collectively snapshotted fixture groups to the existing staged JUnit support artifact, with measured failure boundaries and one-package compatibility evidence.

**Architecture:** Extend the current scoped lifecycle adapters with opaque hook descriptions and a per-invocation stack of receiver-bound cleanup actions. Preserve existing APIs and use a public OpenTest4J aggregate for multiple ordinary cleanup failures.

**Spec:** `docs/superpowers/specs/2026-11-23-grouped-rule-runtime-design.md`

**Base:** Actual main `8c5ec81095c6a268b0498a8516be5b4bc7e9f6a5`, after prerequisite #1981 merged. Work only on the isolated `feat/grouped-rule-runtime` checkout.

**Global Constraints:** Preserve all four existing APIs and actual Throwable/receiver identities. Validate the complete list and snapshot every receiver before setup. Guard exact class before factory/instance access. Preserve order, aliases, null behavior and successful-setup-only reverse cleanup with registration/invocation isolation. Match the spec's ordinary/fatal failure boundary; do not imply universal engine-owned cleanup or reporting. Maven/JUnit is the test authority; no mocks replacing Vintage/Jupiter execution, hidden failures, suppressed analysis, Python test/workflow code or screenshot changes. Java 25 remains the build/product/PDE baseline; only this support module targets Java 21. Verify one canonical packaged JAR on Java 21/25 × JUnit 5.14.4/6.1.3, including the real JDT loader. Automatic source conversion and provisioning remain separate. Reuse shared caches and existing checkout; root handles remote publication/merge. Aim at 1,350–1,450 changed text lines including docs and tests; notify root before 1,500, stop before 2,000 without a substantive pre-existing exception.

### Task 1: Implement and qualify grouped fixture hooks

**Files:**
- Modify `sandbox-junit-migration-support/src/main/java/org/sandbox/junit/jupiter/ExternalResourceExtension.java`.
- Add focused grouped-runtime tests in `sandbox-junit-migration-support/src/test/java/org/sandbox/junit/jupiter/`; reuse existing fixture/launcher conventions without obscuring the deferred single-fixture tests.
- Add `SupportArtifactIT.java` in that test package.
- Modify `sandbox-junit-migration-support/pom.xml` and `maven-bundle.bnd` for OpenTest4J and packaged execution.
- Update `sandbox-junit-migration-support/README.md`, `docs/junit-coordinated-migration.md` and `sandbox_junit_cleanup_help/html/usage.html` accurately and narrowly.

**Interfaces:** Add the exact public signatures from the Spec's Public API section. Use `ResourceHook` as an opaque immutable descriptor with private construction. The factory is `Function<? super ExtensionContext, ? extends List<ResourceHook>>`; the exact-class guard precedes any invocation. Existing two-argument and supplier overloads remain source/binary compatible.

- [ ] **Step 1: Establish actual baseline and behavioral RED.**
  Inspect current source/POM/manifest and repository testing rules. The exact base's hosted Maven, metrics, pinned migration and both distributions have passed; local caches were replaced. Run the unchanged support suite once using the genuine recovered Java25/Maven setup, retaining current receipts. Add native Vintage/Jupiter regressions for collective selection and partial unwind. A missing API compilation failure is an initial RED, not the final behavioral oracle. Record a concrete wrong snapshot/order or missing unwind using a bounded substitute before production passes.

- [ ] **Step 2: Implement entry/snapshot/cleanup discipline.**
  Validate all list elements before any selector, capture every receiver before setup, retain distinct successful actions, and remove state before reverse draining. Reuse the existing wrappers/store isolation where clear; avoid registration-global mutable invocation state or unintended AutoCloseable disposal. Preserve exact class guards on both sides. Implement ordinary identity/aggregation and the group-owned synthetic OOME policy exactly as the spec defines.

- [ ] **Step 3: Complete native behavior and boundary coverage.**
  Prove the Spec's behavioral evidence cases through real engines: ordering and user phases; peer mutation and receiver identity; aliases/nulls/list validation/mutation; exact-class/inheritance/hiding inputs; TestName position; setup depth and cleanup aggregation; assumptions and ordinary/body/lifecycle failures; suppression-disabled and fatal boundaries; repeat/reentry/concurrency and AutoCloseable; PER_CLASS construction counterexample. Use parameterization/clear shared fixtures to avoid boilerplate. Assert expected test/container counts and no unintended skips, not merely launcher completion. Do not speculate about the engine's native exception tree: capture and assert actual outcomes within the stated contract.

- [ ] **Step 4: Prove the packaged API on all supported consumers.**
  Add direct OpenTest4J version 1.3.0 and explicit `[1.3,2)` import. Keep Java21 compilation and current JVM enforcer. Add Surefire execution `packaged-runtime` bound to integration-test; its classesDirectory must not contain production classes and its additional classpath must name the canonical built JAR. Include existing support tests and SupportArtifactIT. Probe CodeSource, loaded bytes, all major65 classes, EE21, version ranges, SPI, actual runtime dependency versions and SHA-256. Build/package once, then run `surefire:test@packaged-runtime` for the remaining Java21/25 and BOM5.14.4/6.1.3 cells without repackaging. Keep the actual JDT JUnit5 loader/filter test in every cell. Save raw Maven/Surefire reports and artifact hash receipts; do not relabel old or interrupted evidence as new.

- [ ] **Step 5: Document the usable API and its limits.**
  Show a legally accessible two-fixture group with selectors that defer field reads and a factory obtaining the proper instance/context. Explain explicit effective order, collective snapshot, null/alias handling, successful-only reverse cleanup, ordinary aggregate behavior, exact-class guards and real engine-owned fatal/suppression/PER_CLASS limits. Keep field-hiding/order discovery, custom `apply`, automatic conversion and artifact/target provisioning marked open. A group API alone does not resolve the four reference-matrix failures.

- [ ] **Step 6: Verify, self-review and commit locally.**
  Run only meaningful focused iterations and the required final package matrix. Check source/package identity and repository diff whitespace. Record actual commands, exact per-cell XML counts and dependency/runtime versions, canonical artifact SHA and source commit. Measure diff size against the exact base and report before exceeding the target. Commit all implementation/tests/docs; write the requested report with status, commit range, tests, contract boundaries and any concrete concern. Do not publish or merge; root performs independent task and whole-branch reviews and current-main hosted qualification.

## Recovery and environment notes

The reviewed draft's semantic decisions were reconstructed after executor replacement. Its previous bytes and local receipts are unavailable; this plan requires new current-source proof. The normal recovered helpers live outside the repository, are supplied in the task dispatch, and must not be copied into tracked code. Missing external provisioning is a concrete blocker to report, not permission to weaken test selection or production behavior.
