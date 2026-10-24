# Mathematics release qualification implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Close concrete release-qualification gaps in issue #1664 without weakening numerical or CI gates.
**Architecture:** Extend existing JUnit round trips and the existing generated-Java JMH harness. Preserve the production optimizer and SDK unless a new regression proves a defect. Reuse exact emitted Java, bootstrap-only execution, pinned source receipts and existing Maven/Tycho CI.
**Tech Stack:** Java 25, JUnit 6, Tycho 5.0.4, Eclipse 2026-09, JMH 1.37.
**Spec:** https://github.com/carstenartur/sandbox/issues/1657 (P07–P13); https://github.com/carstenartur/sandbox/issues/1664.

## Global constraints
- Base current main `16cec8649bb601b5990611325f4ad2727a905af2`; ordinary PR against main, no stack.
- All target Java releases 8–25 are advertised; FLOAT/DOUBLE before 17 remain explicit rejections.
- Maven/JUnit remain authoritative; no new Python automation or CI framework.
- Bound the PR to 1,500 changed text lines; retain strict checks and avoid combinatorial test-count growth.
- Heldout selection is frozen before running the unchanged optimizer. New synthetic controls are not unseen upstream corpus evidence.
- Performance uses actual generated Java, multiple forks, warmup, raw data and allocations; guarded/checked costs remain included.
- No stable release/tag publication. The declined browser authentication remains respected.

## Review focus
- Older target APIs accidentally accepted because the test parser always uses Java 17.
- Narrow integral promotions, eliminated original overflow, and fallback behavior at boundaries.
- Incorrect generated fixture passing benchmark setup because an expected exception masks the wrong path.
- Fixed or tiny benchmark inputs producing constant-folding or unrepresentative safety-cost claims.
- Heldout cases relabeled or silently removed after a negative result.

### Task 1: Target/profile round trips
**Files:** `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/MathTestSupport.java`; new target/profile matrix test in the same directory.
**Interfaces:** Add `parse(String source, int targetJava)` while preserving `parse(String)`. Test real MathematicalAnalysis, verified evidence, edit, exact undo, and compiled generated Java.
- [ ] Add bounded matrix tests grouped by target release, batch compiling cases to avoid one compiler startup per combination.
- [ ] Exercise supported numeric kinds and all three safety profiles with edge values and independent checked expectations. Reject floating point targets before 17 explicitly.
- [ ] Verify controls fail when a generated body is corrupted; fix production only for a reproduced defect.
- [ ] Run focused JUnit and the normal Maven/Tycho gate; record outcomes.

### Task 2: Profile performance evidence
**Files:** existing `MathematicsOptimizationBenchmark.java`, its contract tests and fixture export support; minimal Maven test wiring if needed.
**Interfaces:** Preserve the existing long-array computation interface; use real generated long addition/subtraction round trips with safe and overflow input sets.
- [ ] Add contract tests for selectable inputs and explicit expected generated exceptions before implementing the harness extension.
- [ ] Retain default small/wide fixture behavior; reject wrong values, missing expected exceptions and unexpected exceptions before measurement.
- [ ] Export source-bound original/generated bodies with safety profile, goal and generation-time provenance.
- [ ] Run two JMH forks, three warmups, five measurement iterations and GC profiling. Preserve raw data and report negative results honestly.

### Task 3: Frozen independent controls and acceptance record
**Files:** one compact JUnit control class in the existing math test module; existing QA documentation.
**Interfaces:** Frozen scratch selection supplies exact formulas and independent oracles before optimizer evaluation.
- [ ] Record all outcomes for the independently selected synthetic cases, renamed variants, negative controls and stable optimized variants.
- [ ] Retain source/proof/compile/execute/undo checks and explicit unchanged outcomes.
- [ ] Reconcile issue #1664 with observed evidence; do not close unsupported full-spec requirements.
- [ ] Obtain independent whole-change review, verify required CI, and integrate the small PR.
