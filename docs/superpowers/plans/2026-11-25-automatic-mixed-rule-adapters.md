# Automatic mixed resource adapters implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review the bounded production change. Follow the native TDD evidence and final corpus gates.

**Goal:** Add a real supported strategy for shared Rule/ClassRule fixtures, then complete #1931 without weakening qualification.

**Architecture:** Immutable coordinated plans preserve shared fixture declarations and register proven consumers with the existing support runtime. Strict retains complete unproved consumers; a component without a supported strategy stays fatal. PDE dependency changes share the existing restoration transaction.

**Tech Stack:** Java 25, Eclipse 4.41 ASTRewrite/LTK/PDE, Tycho 5.0.4, JUnit Jupiter/Vintage; Java 21 support artifact.

**Spec:** docs/superpowers/specs/2026-11-25-automatic-mixed-rule-adapters-design.md

## Global Constraints

- Preserve complete before/after/Undo compiler diagnostics and optional warnings.
- Preserve fixture instance identity, helper access, constructor assignment, virtual dispatch and retained Vintage contracts.
- Use typed Charset APIs, no new Python automation, ordinary PR against main.
- No untrusted cleanup option may claim dependency availability.
- No current-main or native JUnit 6 claim from an older/different tree.

## Review Focus

- Inherited declarations still required by a retained Vintage subclass.
- Peer rule ordering, receiver reassignment/aliases and TestName timing.
- Public versus protected hooks and source outside the writable selected scope.
- Real PDE provider/class-space availability and all metadata restoration exits.
- Reapplication, stale bindings, generated-name collisions and exact native Undo.

### Task 1: Supported mixed consumers and atomic PDE support

**Files:** JUnitMultiFilePlanner.java, JUnitMigrationPlan.java, dedicated mixed-plan/rewrite classes if needed, JUnitCleanUpCore/retention helpers only at established integration points; ProjectWideCodeCleanupApplication.java and dependency helpers; native JUnit cleanup and cleanup-application tests; existing migration documentation.

**Interfaces:** Consume the existing ExternalResourceExtension perTest/perClass/group factories. Signal generated dependency through bound or syntax-confirmed org.sandbox.junit.jupiter references. The controller publishes only reviewed commits and manages #1931 separately.

- [ ] Read the spec, repository instructions, existing resource planning, strict retention tests and support runtime contracts.
- [ ] Add a genuine native regression for an instance owner, a class owner and a retained Vintage consumer of the same fixture. The current planner must fail with MIXED_RULE_LIFECYCLE before implementation.
- [ ] Implement the smallest immutable strategy with real provider proof, source/binding revalidation, protected shared declarations and explicit retained-owner diagnostics.
- [ ] Implement metadata provisioning/restoration using existing snapshots; add failing tests for missing/incompatible provider, CHECK/failure/no-op restoration and successful committed support import.
- [ ] Run produced Apply sources with real engines and compare identities, outcomes and lifecycle traces; verify complete diagnostics and exact Undo.
- [ ] Run the affected native suites and negative mixed-shape tests. Keep every unsupported case visible.
- [ ] Update documentation and produce a concise report naming exact commands, code tree and residual limitations.
- [ ] Obtain independent task review, fix verified findings and commit the bounded production slice.

### Task 2: Hosted verification and completion

**Files:** Existing CI and corpus artifacts; no weakened profiles or expected-output substitutions.

- [ ] Publish the production PR directly against current main; require exact-head Maven, metadata/native tests, quality and relevant distribution/screenshot gates.
- [ ] Merge only after review and required checks pass.
- [ ] Integrate the verified main into existing #1931, retaining every corpus registration/profile and diagnostic/source restoration check.
- [ ] Run the complete Core/UI/SWT matrix; inspect actual patches and retained markers for the mixed component.
- [ ] Resolve concrete regressions, require a complete result on the final tree, and independently review #1931.
- [ ] Merge #1931 with the checked head, verify resulting main, and close reference #1955 without merging.
