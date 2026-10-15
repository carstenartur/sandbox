# Final-value projection implementation plan

> **For agentic workers:** Use superpowers:executing-plans for this bounded change.

**Goal:** Expose real shared arithmetic by preserving the final escaping version of a local, without preserving overwritten versions as independent results.

**Architecture:** The Java adapter selects value outputs; the unchanged SDK searches and independently verifies them against the complete original execution trace. Source emission retains declarations needed outside the region and the original fallback calculation.

**Tech Stack:** Java 25, JDT 3.47, existing optimization SDK, Maven/JUnit, Tycho.

**Spec:** User request to improve mathematical refactoring on Bouncy Castle; existing contracts in `sandbox_math_cleanup/ARCHITECTURE.md` and `SOURCE_BACKED_REWRITES.md`.

## Global constraints

- Preserve every original trace occurrence and all numeric/receiver assumptions.
- Preserve comments, binding identity, declaration scope, source receipts and Apply/Undo.
- Keep the file work budget and independent proof/emission gates.
- No corpus-specific recognizers or mathematical rules in the Java adapter.
- Maven/JUnit tests are authoritative; keep this change below 1,500 changed text lines.

## Review focus

- A dead earlier division or checked overflow must remain observable: exercise exception behavior.
- A local declared inside the region and used afterwards must still compile: exercise explicit types and `var`.
- Existing locals assigned in a region must not be redeclared: exercise parameter/local assignments.
- Comments inside an overwritten declaration must survive: retain the conservative binding pin.
- Guard failure must run the original program: exercise nullable, subclassed and oversized BigInteger receivers.

## Task 1: Project local versions and preserve source declarations

**Files:** `JavaRegionLiveness.java`, `JavaRegionSourceRewriter.java`, `MathematicalAnalysis.java` in the math core; `MathVariableRegionsTest.java` and a focused BigInteger regression in the math tests.

**Interfaces:** Keep `JavaComputationRegion` and `OutputBinding` unchanged. `internalBindings` may contain the declaration of an escaping local. All source emission paths must honor that contract.

- [x] Add a test for `int t=x*a; t=t+x*b; consume(t); return observed;`: require one multiplication, compiled before/after equality including wraparound, and exact Undo. Parameterize explicit `int` and `var`.
- [x] Run it against the old adapter; expect no factored rewrite.
- [x] In `JavaRegionLiveness.project`, select the last version per visible binding, retain return values and all versions of comment-pinned bindings; keep the original trace unchanged.
- [x] Preserve internal declarations needed by surviving outputs, without their dead initializer; replace `var` with its resolved numeric type. Guarded emission hoists the original declaration even when its value became internal.
- [x] Add controls for external declarations, embedded comments, dead throwing assignments and checked overflow; compile and execute generated programs.
- [x] Run focused JUnit and the complete native math module suite; expect no failures.
- [x] Commit the reviewed semantic change with its regressions.

## Task 2: Qualify useful real-source output

**Files:** a Bouncy Castle regression test; a compact report under `qa/math-optimization/results/2026-10-15-final-values/`.

**Interfaces:** Consume unchanged `MathematicalAnalysis.analyze` and the pinned BC 1.85.2 binary/source archives used by existing tests and the scanner.

- [x] Analyze the complete unchanged SWU source at the frozen 1,000,000-work file budget. Record actual accepted regions and emitted operations, including guard cost.
- [x] Compare compiled original/transformed public behavior on representative curve points and invalid inputs; require exact Apply/Undo and retained proof evidence.
- [x] Repeat the full BC scan using the same archives, profile and budget as the 112-region baseline. Report count changes, operation improvements and remaining blockers separately.
- [x] The full-file regression fails with six reductions at the equal allocation. Allocate remaining work in proportion to original trace occurrence count, capped at 64 per region and floored at one; keep the selection path unchanged. Reserve half for an alternate mathematical domain or source slice, and one quarter for smaller BigInteger statement retries. Unsupported results lack a work counter, so their entire request allocation is charged; a reserve is necessary even for one selected region. Failed retries consume the same remaining file budget. Verify the new three-modulo regression, existing cancellation/budget tests and a complete corpus comparison before retaining the rule.
- [ ] Independent whole-branch review, required CI, and merge after the original PR is qualified.
