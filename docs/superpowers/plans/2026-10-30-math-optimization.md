# Mathematics optimization implementation plan

> Use superpowers:executing-plans. The user has explicitly requested all five
> reviewed improvements; execute inline without another approval round.

**Goal:** Deliver usable, bounded improvements in both SDK and Java source cleanup.
**Architecture:** Shared Regelsuche search/proof with Sandbox source realization.
**Tech Stack:** Java 25, Maven/JUnit, JDT, existing standalone SDK packaging.
**Spec:** ../specs/2026-10-30-math-optimization-design.md

## Global constraints

- Preserve Java effects, selected types, proof gates, exact Undo and source receipts.
- One ordinary main-based branch per repository; no stacked PRs.
- Use existing Maven qualification; keep budgets and benchmark input identities.
- Do not claim performance from candidate counts or operation estimates.
- Keep integration reviewable; describe a coordinated cross-repository exception
  if the reviewed implementation exceeds the repository line limit.

## Review focus

- Exception-visible intermediate locals and overlapping arrays.
- Cross-class initialization and mutable or stale helper definitions.
- Java shift masking, sign extension and narrow/wide mixed arithmetic.
- Unknown numeric bounds and overflow in cost estimates.
- Cancellation or failure during explanation/replay and exhausted file budgets.

### Task 1: Complete explanation accounting (Regelsuche, then Sandbox)
- [x] Add regression for successful verification plus presentation, budget stop,
  cancellation and unchanged derivation replay configuration.
- [x] Extend ComputationExplanations.Result with total work; support a caller's
  remaining allowance without modifying the original request identity.
- [x] Verify SDK tests, then debit that total in MathematicalAnalysis.

### Task 2: Magnitude and emitted-source candidate ranking
- [x] Test differently sized BigInteger calculations, intermediate growth,
  unknown bounds and consumer ranking between verified alternatives.
- [x] Add bounded numeric cost estimation and a metered ranking interface to
  existing JointPlanSearch/ComputationOptimizer. Preserve default compatibility.
- [x] Let Sandbox score actual emitted capture/guard/local costs before selection;
  retain independent proof and final compilation gates.

### Task 3: Generic carry, mask and range relationships
- [x] Add independent proof and optimization regressions for carry-aware disjoint
  word joins and masked arithmetic, including wrong-mask/width counterexamples.
- [x] Extend the existing typed candidates and bit proof, with versioned evidence,
  bounded traversal and no BC-specific names.
- [x] Run the whole SDK/dependency reactor and package a clean source-pinned SDK
  with the existing consumer verification; integrate its exact bytes in Sandbox.

### Task 4: Realize complete source tasks
- [x] Compile and compare cross-store candidates, aliasing, null/short arrays,
  try/catch local state, changing input locals, comments and exact Undo.
- [x] Add a bounded task realizer preserving the event schedule. Reuse emitted
  expressions and independent re-extraction/proof; admit only actual source gains.
- [x] Connect the path to ordinary cleanup without taking baseline regions' budget.

### Task 5: Source method summaries
- [x] Test a pure source helper in another file, same-class nonprivate helpers,
  initialization effects, unsupported state access and stale-source rejection.
- [x] Reuse bounded source context and typed extraction to summarize static
  helpers. Preserve external calls at their source positions; optimize consumers.
- [x] Verify workspace/sourcepath boundaries and publish truthful help examples.

### Task 6: Qualification and integration
- [x] Complete SDK and headless Sandbox tests, matched BC scans and compiled
  generated-code/full-call correctness checks with unchanged inputs and budgets.
- [x] Update SDK pins, Help, architecture and obsolete TODO statements.
- [x] Independent whole-branch review, regression fixes, native plugin packaging
  and repository-policy qualification.
- [ ] Publish ordinary main-based PRs and run native UI/workspace CI. Publication
  is explicitly authorized; the GitHub connector supplies the publication commits.
- [ ] Fresh JMH timing qualification remains future work; no speedup is claimed.

Final integration results: 2,510 SDK-reactor tests (227 SDK), 568 headless consumer tests
(one existing optional skip), 12 repository-policy tests, and native packaging/
SpotBugs pass. BC strict/mathematical accepted counts are 120/129 versus 117/126.
See [qualification and remaining limits](2026-10-30-math-optimization-qualification.md).

## Ledger

Initial bases: Sandbox 6212f23864e3e2f1be5515d37664df4ee992c1f2;
Regelsuche 7cb836ec3b7ec1f326949ab8b78f2ce3979d572f.
Environment: fresh isolated clones, Temurin 25.0.5+8, pinned Maven 3.9.16.
Baseline started; Maven proxy must be captured per execution because the runtime
proxy endpoint changes between tool invocations.
