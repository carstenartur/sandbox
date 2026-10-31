# Functional conversion and finite mathematics completion plan

> **For agentic workers:** Use superpowers:subagent-driven-development or
> superpowers:executing-plans, with independent worktrees and a final fresh review.

**Goal:** Repair known converter semantics and deliver a tested opt-in combined
cleanup, measured finite-series examples, and reviewable integration changes.

**Architecture:** Conservative JDT converter admission, private working-copy
coordination in the mathematics layer, numerical proofs in the existing SDK.
At most two mathematics phases and one converter phase share one budget.

**Tech Stack:** Java 25, Maven 3.9.16, Tycho 5.0.4, Eclipse 4.41, JUnit and JMH.

**Spec:** `docs/superpowers/specs/2026-10-31-functional-math-completion-design.md`

## Global constraints

- Default converter behavior never enables mathematical search or save actions.
- Keep original Java overflow, evaluation order, calls, exceptions and termination.
- Never reuse stale AST attachments or weaken existing proof/source replay.
- Ordinary main-based PRs; review-sized slices; no stacked prerequisites.
- Record exact commands and source revisions; no success claim from test counts alone.

## Review focus

- Inclusive maximum endpoints and terminating exceptions before a wrapping update.
- Mutable bounds, method-call bounds, unboxing and changed evaluation frequency.
- Mathematically generated fallback loops modified by a subsequent style pass.
- Stale source/dependencies/options after preview and JDT merging edit roots.
- Exhausted work/time or cancellation between phases without partial buffer edits.

## Task 1: Counted-loop safety and recovered bugs

**Files:** `sandbox_functional_converter/.../TraditionalForHandler.java`,
`sandbox_functional_converter_test` regression classes, related converter docs.

**Interfaces:** Preserve `LoopConversionService.analyze` and existing handler APIs.
No new dependency on mathematics. Other tasks do not modify TraditionalForHandler.

- [x] Add a failing real-converter regression for the throwing sink at MAX_VALUE.
- [x] Reproduce effectful/mutable bound divergences; assert safe rejection or exact
  execution, plus existing positive constant/local-bound transformations.
- [x] Implement binding/type/range-aware conservative admission; reject unproved
  counter wrap and changes to bound effects/evaluation count.
- [x] Check current unused-parameter conversion with a throwing toString object.
- [x] Fix the review finding for checked exceptions escaping a converted lambda,
  including methods, constructors, explicit throws and implicit resource close.
- [x] Reject or correctly qualify imports when local, field or type names shadow
  IntStream; preserve complete multiargument initializer expressions and comments.
- [x] Run focused native tests, the complete relevant converter suite and core tests.
- [x] Commit the independently reviewable fix and report exact RED/GREEN evidence.

Published as Sandbox #1776, source `c07de575b159438f6be1a77b5be4ec4c9e68d2a4`.
The full reactor before the final scope fix passed 2,335 tests with three existing
skips; the final scope fix passed all 87 focused tests and SpotBugs. Independent
review found no blocking issues. The exact published source has passed Maven
verification; the remaining platform and metrics gates are tracked separately.

## Task 2: Explicit coordinated cleanup

**Create:** `sandbox_math_cleanup/src/org/sandbox/jdt/internal/ui/fix/FunctionalMathOptions.java`,
`FunctionalMathCoordinator.java`, `FunctionalMathChange.java` and native tests
`FunctionalMathCoordinatorTest` / `FunctionalMathCleanUpTest`.

**Modify:** MathematicalCleanUpCore, its option initializer and configuration tab;
LoopConversionService and UseFunctionalCallCleanUpCore only for opt-in coordination;
optional bundle metadata and affected help. Keep converter bugfix files untouched.

**Interfaces:** FunctionalMathOptions owns the opt-in key and immutable effective
options; coordinator consumes the original ICompilationUnit, selected options and
monitor, and returns a guarded final change. LoopConversionService receives fresh
ASTs plus excluded source ranges and cancellation. Mathematical APIs retain proof
ownership and expose only the constrained fresh-sum follow-up needed by this plan.

- [x] Native RED: combined request removes the mathematical loop and converts a
  separate eligible loop, while preview leaves the original buffer unchanged.
- [x] Implement private working-copy phases with bounded total work/time and
  deterministic mathematics-before-style ordering.
- [x] Keep generated mathematical regions protected; support a fresh bounded
  follow-up after eligible map fusion without re-optimizing templates.
- [x] Implement one guarded final ReplaceEdit and exact native Apply/Undo.
- [x] Test defaults, absent optional bundle, only-converter/only-math behavior,
  profile changes, stale buffers/dependencies, cancellation and budget exhaustion.
- [x] Update user-facing options/help and run the complete affected math/core suite.
- [x] Commit with source-bound proof, native test and policy evidence.

The implementation and review fixes are committed as `80f7a5a`, `9ea2b0d`, the
test-only default correction `78b82c8`, and wrapper integration fix `f93a123`.
Fresh review confirmed fixes for the dormant flag, unbudgeted snapshot work and
an uncaught fresh-sum replay limit. The native FileBuffer suffix guard is tested;
JDT's actual overlap deferral is distinguished from public edit reparenting.
The early int guard is protected in full on repeated invocation, while a new
sibling loop in the same method remains eligible. Loop and Supplier paths pass.
The full headless math run passed 870 tests with one existing mirror assumption
skip. Final combined native verification is recorded under Task 4.

## Task 3: Practical qualification

**Files:** existing `qa/math-optimization` Maven/JUnit/JMH infrastructure and
qualified result documentation. Measured short-range overhead justified a small
primitive-int fallback guard in `JavaRuntimeIterationEmitter`; its arithmetic,
evaluation order, source admission and budget behavior have separate tests and
independent review. The Regelsuche SDK and heuristic weights are unchanged.

**Interfaces:** Feed complete authored source through MathematicalAnalysis and
compile the exact generated output. Reuse existing hash/provenance receipt APIs.

- [x] Write short tests for source generation, output equivalence and tampering.
- [x] Add opt-in JMH cases for runtime polynomial loop/stream and linear/product
  recurrences, including small fallback and large optimized ranges.
- [x] Run warmups, independent forks and GC profiling; keep raw output and hashes.
- [x] Read current remote Bouncy Castle qualification or run one matched scan if
  fresh exact-source evidence is unavailable; preserve negative/no-gain outcomes.
- [x] Document measured costs, supported scope and remaining limitations.

Measurement source is public Sandbox #1777 commit
`ad17e5b3617ba8225a47c8712b5a14d5162b73b7`. The Bouncy Castle baseline is successful
workflow `41222781112`, actual checkout `a8f3b9b2274fd5f8dc90674a0317162f63e2ee27`.
Its source tree equals merged main `41c7b4ee67482d8c85184bd474e71ed9cf85d671`.
Matched scans on the measurement source retained every one of the 146 strict and
147 mathematical accepted spans, all ten parse diagnostics, and all 58/62 complete
generated files byte for byte. All eight baseline end-to-end hash-to-curve timing
pairs are inconclusive; no Bouncy Castle speedup is established.

Final data and the readable report are published in #1777 at `e91aa272` under
`qa/math-optimization/results/2026-10-31-finite-series/`. All five large synthetic
pairs have separated timing intervals; four small pairs remain inconclusive.
The GC samples cannot isolate candidate allocation costs. The measured source is
still `ad17e5b`; result publication does not relabel the measured revision.

## Task 4: Integrate and verify

**Files:** release/qualification provenance and reviewable branches derived from
the existing SDK and Sandbox integrations; preserve original drafts for comparison.

- [x] Combine independent commits locally without overwriting existing work.
- [x] Resolve the prerequisite integration state against current main. Regelsuche
  #1128 and Sandbox #1774 were merged by the repository owner during this work.
  The separately verified constant-only #1775 was consequently closed as
  superseded; no runtime/partition extraction or duplicate merge remains needed.
- [x] Obtain a fresh independent review; reproduce/fix material findings with tests.
- [ ] Publish tested branches/PR updates and verify exact-head required checks.
- [ ] Integrate only after checks, review state and policy are actually satisfied.
- [ ] Record remaining release blockers accurately; do not claim a release from
  a successful branch build alone.

The complete combined native reactor passed on `f93a123` (tree `88c8735b`):
**3,294 tests, zero failures/errors, three existing converter skips**, plus eight
clean SpotBugs gates. With the configured X11 test display, the Maven arguments were:
`-pl sandbox_common,sandbox_test_commons,sandbox_functional_converter_test,sandbox_math_cleanup_test -am verify`.
The prior combined run exposed the early-guard
template boundary defect; its regression and the independent follow-up review
are included in this final green result. GitHub integration gates remain separate.
