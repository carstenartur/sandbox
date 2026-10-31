# Java Mathematics Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans task by task. Track steps with checkboxes.

**Goal:** Extend verified mathematics cleanup across Java callable, branch,
iteration and memory contexts without changing their observable execution.

**Architecture:** Reuse the existing extractor, Regelsuche SDK and independent
Java emission verifier. Add source-context adapters and ULR descriptors, keeping
numerical identities in Regelsuche and effects in Sandbox.

**Tech Stack:** Java 25, Eclipse JDT, Maven/JUnit, Tycho, shared ULR core.

**Spec:** `docs/superpowers/specs/2026-10-31-java-mathematics-context-design.md`

## Global Constraints

- Java 25, existing Maven/JUnit and Tycho builds; no separate test runner.
- Existing safety profiles, explicit mathematical opt-in and budgets stay intact.
- No algebraic formula catalogue or CAS process in Sandbox.
- ULR core stays JDT-independent. Numerical proof must not depend on renderers.
- Publish ordinary PRs against main. No stacked PRs.

## Review Focus

- Lambda target overload and nearest callable return type must remain unchanged.
- Creation-time facts must not justify deferred callback execution-time facts.
- Null/throwing receivers, argument order and class initialization must survive.
- Empty iterations, mutable captured state and floating-point grouping must survive.
- New contexts share the original file budget and cannot reuse stale source facts.

### Task 1: Numeric callable and branch expression contexts

**Files:** Modify `JavaComputationExtractor.java`, `MathematicalAnalysis.java` in
`sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/`; add focused
helpers in that package as necessary. Add `JavaCallableContextTest.java` and
`JavaBranchExpressionTest.java` in the mathematics test packages.

**Interfaces:** Existing `Extraction`/`JavaComputationRegion` remain compatible.
Expression-only output anchors are identified from the resolved AST, never a
textual marker. Numeric expression replacements must keep the original type.

- [x] Write failing tests: block and expression `IntUnaryOperator`, primitive
  widening/narrowing, nested lambdas, two-argument reducers, deferred field reads,
  conditional arms, switch yields, unbraced loop/if returns, exact Undo and comments.
- [x] Run those tests with `mvn -f qa/math-optimization/pom.xml -Dtest=JavaCallableContextTest,JavaBranchExpressionTest test`; confirm missing support.
- [x] Resolve returns at the nearest callable; admit expression regions only with
  provable source shape and verified emission, keeping evaluation in place.
- [x] Run focused tests and complete `mvn -f qa/math-optimization/pom.xml test`.
- [x] Commit the implementation and tests.

### Task 2: Exact-dispatch instance method summaries

**Files:** `JavaScalarMethodBody.java`, related extractor helpers and
`SourceMethodSummaryTest.java`; add `InstanceMethodExpansionTest.java`.

**Interfaces:** Extend existing `resolve(..., boolean retainedCall)`; no numeric
protocol change. The resolved source method and invocation binding must agree.

- [x] Test private/final implicit-this methods, final-class local receivers in
  retained tasks, throwing/null receivers, overrides, source staleness, state reads.
- [x] Run focused tests RED, implement receiver/dispatch admission, run GREEN.
- [x] Run the complete mathematics suite and commit.

### Task 3: Shared numeric iteration contexts and stream composition

**Files:** shared core model/terminal descriptors; new JDT numeric context helper
in the mathematics package; `MathematicalAnalysis.java`; relevant bundle/build
descriptors; `JavaNumericStreamTest.java` and core descriptor behavioral tests.

**Interfaces:** Typed ULR descriptors retain source/element/operation/terminal
shape; mathematical adapters compose `JointComputationPlan` inputs by bindings.
Keep converter rendering admission separate from numeric inspection admission.

- [x] Test IntStream/LongStream `sum`, `reduce`, `min`, `max`, method references,
  adjacent-map cancellations, overflow and rejection across filter/peek/parallel.
- [x] Run RED, implement shared context and verified in-place map composition.
- [x] Run core and mathematics suites, native compilation, commit.

### Task 4: Loop-invariant numeric computation

**Files:** new `JavaLoopInvariantAnalysis.java`, integration in existing analysis,
`JavaLoopInvariantTest.java` and ULR context helper.

**Interfaces:** Produce ordinary source-bound `Replacement`/`VerifiedRegion`
evidence using existing numeric verification and compile validation.

- [x] Test for/enhanced-for/while/do contexts, unmodified primitive parameters,
  empty loops, branch-local declarations, modified inputs, exceptions and nesting.
- [x] Run RED, implement bounded total-expression motion with unchanged loop
  controls; reject unavailable inputs, memory reads and exception-bearing work.
- [x] Run full mathematics suite; commit.

### Task 5: Ordered object, nested-array and boxed numeric reads

**Files:** `JavaSourceReadShape.java`, `JavaComputationExtractor.java`, source
capture helpers, `JavaExtendedSourceReadTest.java`.

**Interfaces:** Existing occurrence-specific `ArrayRead` source obligations; no
new heap equalities. Capture original source syntax and primitive result kind.

- [x] Test nested primitive arrays, object field chains, accessor calls/unboxing,
  null/short arrays, receiver mutations, call counts/order, overwritten locals.
- [x] Run RED, implement bounded ordered captures and original effect boundaries.
- [x] Run full mathematics suite; commit.

### Task 6: Qualification, documentation and integration

**Files:** plugin README/ARCHITECTURE/help; qualification report beside this plan.

- [x] Run full mathematics and unchanged-core ULR tests, native compile/SpotBugs.
- [ ] Refresh repository policy gates after documentation commit (controller).
- [x] Run matched BC/SWT corpus profiles; report exact inputs and changed spans.
- [ ] Review the complete branch independently and fix material findings.
- [x] Document delivered support, remaining upstream proof requirements and
  measured limitations.
- [ ] Publish final integrated head and reviewable ordinary main-based slices
  (controller; draft/reference PR #1772 is not a merge candidate).

## Execution record

Baseline `4f816e7`: 568 mathematics tests, zero failures/errors, one existing
optional skip. Local Java 25/Maven 3.9.16 toolchain verified. User's implementation
request authorizes execution of the preceding architectural recommendation.

Tasks 1–5 are implemented and independently reviewed: `d402504`, `ff50dd3`,
`e9f5c08`, `118c43e` + review fix `ee58321`, and `f648240`. The
[2026-10-31 qualification](2026-10-31-java-mathematics-context-qualification.md)
records the frozen code/test/corpus evidence and the remaining controller gates.
Full mathematics: 714 tests, zero failures/errors, one existing optional skip.
ULR core: 229 passing at Task3, unchanged since. Native core/math/test/help/feature
packaging and SpotBugs passed. Matched BC strict 120 -> 146, mathematical 129 ->
147, all prior spans retained; SWT ImageData 0 -> 0. Final whole-branch review,
refreshed policy and final integrated publication/CI remain pending here.
