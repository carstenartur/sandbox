# Mathematics Java output quality implementation plan

Spec: [design](../specs/2026-11-03-math-output-quality-design.md).

## Global Constraints

- Sandbox owns Java semantics/emission; Regelsuche owns algebra and numeric proofs.
- Preserve Java widths, side effects, termination and live counter state. Preserve
  independent proof, source binding and exact emission replay, work and size limits.
- No profile/default changes, new cleanup base classes, SDK algebra catalogue,
  Python automation, test framework, warning suppression or weakened verification.
- Tests first for behavioral changes. Use Java 25 and existing Maven/JUnit paths.
- Work directly from main 95bfbddc067d0033a86fb2af97baf6b5cd81e297. Do not merge.
- Aim below 1,500 changed text lines; at 2,000 follow repository split/exception
  policy before further implementation. Keep dated qualification results immutable.

### Task 1: Primitive constant-sum emission

Files: JavaRuntimeIterationEmitter, RuntimeIterationEvidence,
JavaRuntimeIterationOptimizer, and focused existing/new runtime iteration tests.

- Add a deterministic Java emission strategy derived from fresh source context and
  checked PolynomialSumPlan coefficients, not class/method names or raw algebra.
- Admit primitive int/long targets and int induction/start/bound expressions for
  plain constant sums only. Check initial/target correspondence. Keep other models
  on the current template. Recompute strategy during independent verification.
- Use compact primitive code without a crossover/fallback for exclusive unit-stride non-do loops. General int
  headers may use exact long count/after bookkeeping with an int-range guard and
  original fallback. No BigInteger allocations on the selected primitive path.
- Preserve native-width start/bound evaluation, empty/negative ranges, int/long
  modular updates, inclusive/nonunit/descending headers and live counters. Do not
  execute billion-iteration originals in tests; use hand-derived boundary values.
- Update cost/description/threshold to the actual strategy; costs remain estimates.
- Red tests must reproduce needless BigInteger generation and execute original and
  generated source at negative/zero/small/crossover inputs and accumulator extrema.
  Exercise forged strategy/coefficients/template rejection and unchanged long-count
  safety. Run focused runtime tests, then existing standalone math suite once.

### Task 2: Diagnostic and numeric emission quality

Files: MathematicalAnalysis, JavaComputationEmitter, small focused diagnostics
helper if needed, direct source-check call sites, and focused JUnit tests.

- Clean emitted numeric conversions without changing arithmetic or overload kinds.
  Prove narrow-to-int simplification in actual usage; retain int-to-long conversion
  before overflowing arithmetic. Signed-long BigInteger constants use valueOf.
- Extend the generated-source check with baseline-relative diagnostics matched by
  IProblem identity, severity/arguments and concrete source provenance. Source
  offsets may shift. Removed baseline warnings cannot pay for new identical ones.
  Conservative rejection is allowed for ambiguous edited syntax. Enforce both
  candidate and combined output; existing compile errors remain unconditional.
- Preserve project compiler options. If standalone SourceEnvironment gains an
  explicit options field, retain its existing constructor and exact option binding.
- Tests use real JDT diagnostics for new warnings, retained shifted warnings,
  duplicate identities, active NLS, errors, combined edits and necessary widening.
  Keep semantic verifier mutation tests passing. Run focused tests then full
  standalone math suite once. No arbitrary bytecode or source-size score gate.

### Task 3: Runtime explanations and stable reapplication

Files: MathExplanation, runtime emitter/optimizer/analysis as needed, focused tests,
sandbox_math_cleanup README/ARCHITECTURE/JAVA_CONTEXTS and help cleanup explanations section.

- Render runtime explanation from independently checked runtime metadata; do not
  manufacture a scalar ComputationExplanation. Share NONE/NONTRIVIAL/ALL policy.
- Describe actual target names, modulo 2^32/2^64 or exact-value contract, checked sum
  or linear/nested/partition family, and only guards/fallback/crossover actually
  emitted. Primitive strategy from Task 1 must have matching wording.
- Insert bounded comment text inside block/supplier bodies. Both initial emission
  and replay use the same mode. Preserve authored comments and exact Undo.
- Reproduce recursive fallback expansion on second application. If confirmed, add
  a structural generated-envelope exclusion that also works in NONE mode. A
  comment/prefix alone cannot authorize semantic assumptions.
- Tests cover each mode, stream syntax, checked metadata replay, primitive and
  guarded templates, cancellation/bounds where new, and stable second invocation.
  Document behavior and limits. Run focused tests then standalone suite once.

### Task 4: Integration and measured qualification

- Review the complete branch against the spec and repository policy.
- Run normal Sandbox CI including math/converter gates on the new production tree.
- Use existing pinned JDT qualification tooling to apply the real new plugin and
  compile generated sources. Keep the feature PR directly against main; an isolated
  verification branch may combine existing unmerged QA infrastructure with the
  feature solely to run CI, with exact production source hashes recorded.
- Compare warning diagnostics and repeat actual MethodInfo full-reader JMH fixture
  with JAR/class origin verification. Preserve raw evidence and report limitations.
- Re-run relevant upstream native before/after gates where emitted output changed.
  Record exact source/patch/artifact hashes and all test outcomes, including skips.
- Publish the focused feature draft PR against main and durable new evidence.
  Do not claim old successful runs validate new code, or bytecode proves speed.
