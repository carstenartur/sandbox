# Field Call Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Specialize a verified field-root fast path at a proven Java callsite and remove only independently reconstructed redundant guards.

**Architecture:** Source-bound callsite evidence preserves the original direct helper as fallback. A private tryCandidate uses EXACT_PAIR and the current SDK pin. Source normal-return and dominance facts are replayed independently before the ordinary Apply edit is released.

**Tech Stack:** Java 25, Eclipse JDT AST, existing Regelsuche SDK, Maven/JUnit.

**Spec:** `docs/superpowers/specs/2026-11-27-field-call-context-design.md`

## Global Constraints

- Mathematical proof stays in Regelsuche core; Java source, effects and observations stay in Sandbox.
- Use ordinary MathematicalAnalysis, analysis.newEdit(), source replay and Undo.
- No BC class-name, source-hash or prime-specific optimizations; actual BC is a pinned integration fixture only.
- Keep original helper and direct fallback semantics exact, including subclasses and invalid inputs.
- Never infer an exact BigInteger runtime class from a method return type alone.
- Partial class initialization, argument/LHS effects, changed source and alias writes fail closed.
- Current SDK source pin 6bcddc0931e1e3139edf3a9fca5bc87635b1e6ac remains unchanged in this independent slice.
- Java25 -Xlint:all -Werror applies to changed production and test sources; Maven/JUnit authority.
- Aim below 1,500 changed text lines. Report before crossing 2,000; no policy weakening.

## Review Focus

- Additional helper execution must not move a throwable/effectful argument or array LHS ahead of the original evaluation.
- Source-return facts must respect virtual dispatch and class-initialization state.
- A norm flag with later writes to a role, modulus or flag cannot discharge the guard.
- Candidate proof and guard facts must be reconstructed after combined source edits and at Apply freshness checks.
- Direct/raw helper callers and SDK EXACT_PAIR semantics must remain unchanged.

### Task 1: Callsite-specific exact roots with source-proven guards

**Files:**
- Create in `sandbox_math_cleanup/src/org/sandbox/jdt/internal/corext/fix/math/`: `JavaFieldCallSite.java`, `JavaFieldGuardFacts.java`, and a focused optimizer/emitter helper if separation keeps each responsibility readable.
- Modify there: `JavaGuardedFieldSource.java`, `JavaGuardedFieldOptimizer.java`, `JavaFieldProgramLowering.java`, `MathematicalAnalysis.java` only at the necessary evidence/replay hooks.
- Create: `qa/math-optimization/src/test/java/org/sandbox/math/qa/FieldGuardFactsTest.java`.
- Modify: existing BouncyCastleGuardedRootTest and focused source tests when the old placement assumption needs a behavior-preserving replacement.
- Modify: strict QA compiler source includes, cleanup README and QA README as needed.

**Interfaces:**
- `JavaFieldCallSite.locate(...)` binds the exact original root method and same-unit direct static invocation with three stable local slots; it owns unique names, surgical clone/redirect changes and exact original fallback.
- `JavaFieldGuardFacts.inspect(...)` returns immutable per-input exact-class/nonnegative/below-modulus facts and optional bound NORM_WHEN_TRUE evidence. A failed proof retains its original runtime check.
- `FieldCallEvidence` records root/caller/factory keys and hashes, argument role bindings, facts, actual emitted program and dependency receipts. Combined replay reconstructs it; never accepts an arbitrary guard mask.
- `JavaFieldProgramLowering` binds emitted formal roles and guard facts while retaining its old exact-local API.

- [ ] Write tests for constructor-return facts and a true-only norm witness first; observe failure against current ordinary analysis.
- [ ] Implement bounded source-return inference, dispatch checks and per-call argument/LHS validation with no external writes.
- [ ] Emit private tryCandidate plus a direct caller fallback. Keep the original helper byte-identical when this specialization is chosen; avoid overlapping local-root edits.
- [ ] Decode and replay generated statements, expected facts and original source bindings before exposing the ordinary Apply edit; retain freshness receipts.
- [ ] Add behavioral negative cases: subclass callbacks, null/real/bad-domain values, reassigned norm flag, role writes, different modulus, changed getter source, unsafe LHS and repeated argument effects. Check exact original outputs/exceptions and Undo, not just presence of source strings.
- [ ] Qualify ordinary Apply on pinned BC 6560e91d: exact raw root pairs and public vectors; source-proven guard reductions, remaining upper-bound/norm checks and Jacobi correction; unchanged direct helper source.
- [ ] Run focused Maven/JUnit tests and strict production/test compilation. Run broader affected QA and required repository-policy gate; report real skips and failures.
- [ ] Document exact proof boundary and supported source grammar, self-review and commit. No performance claim without later isolated timed runs.
