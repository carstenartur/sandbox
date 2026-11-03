# Mathematics runtime qualification and release implementation plan

> **For agentic workers:** Use superpowers:executing-plans or
> superpowers:subagent-driven-development; preserve the recorded evidence and
> continue from completed tasks after a handoff.

**Goal:** Resolve the measured mathematics regressions and finish the existing
1.3.8 release through its normal verification and publication path.

**Architecture:** Independent diagnostics cover SWU BigInteger intermediate
growth and the MethodInfo primitive sum emitter. Integrate only verified,
general corrections. Release evidence uses the existing workflow and PR #1779.

**Tech Stack:** Java 25, JUnit, Maven/Tycho, JMH, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-03-math-runtime-release.md`.

## Global constraints

- Java 25; unchanged SDK unless the demonstrated correction belongs in it.
- No new Python automation or parallel test framework.
- No class/curve/input blacklist, relaxed assertions, or weakened budgets.
- Preserve source, generated-output, compiler and benchmark identities.
- Existing release authorization does not permit bypassing authentication gates.

## Review focus

- BigInteger multiplication and delayed modulo must account for operand growth.
- Primitive count arithmetic must preserve overflow, empty ranges and int/long
  distinctions, including the full signed-int iteration domain.
- Existing constant-reduction support must not disappear just to hide timing.
- JMH setup, class loading and variant ordering must not masquerade as a gain.
- Maven clean must not delete the release evidence; dry-run assets must reflect
  the intended publication notes without initiating publication.

## Task 1: Controlled diagnostics and minimal corrections

**Files:** `JavaCandidateRanking.java`, `JavaRuntimeIterationEmitter.java` and
their existing tests under `sandbox_math_cleanup_test`; complete-call fixtures
under `qa/math-optimization` only as needed by the confirmed diagnosis.

**Interfaces:** Keep existing public interfaces. Diagnostic results identify
the minimal production change; record the concrete ruling before editing it.

- [x] Compare exact SWU original/generated sources, first-region-only reversal,
  and an original/original control with the same complete public call.
- [x] Inspect complete MethodInfo original/current output and test a simplified
  equivalent primitive candidate against the same bytecode and call fixtures.
- [x] Record supported causes and limits; write a regression that fails on main
  for each selected production correction, then implement the minimal fix.
- [x] Run existing relevant suites and fair complete-call measurements; retain
  unsuccessful experiments and any unresolved regression in the evidence.

## Task 2: Release evidence integrity

**Files:** `.github/workflows/deploy-release.yml`, existing release policy tests,
`release_notes.md`, `docs/releases/1.3.8-preparation.md`.

**Interfaces:** Preserve existing release inputs and publication gates. Evidence
and reviewed notes must survive the clean build and be available in rehearsal.

- [x] Reproduce deletion of release-tree-identity.json by clean in the existing
  ReleaseBuildEvidenceTest, preserve/recreate it, and observe the test pass.
- [x] Verify the existing notes format; make the intended curated notes available
  for dry-run review and publication with a meaningful Maven/JUnit regression.
- [x] Refresh the existing preparation with new exact-source checks and timing.

## Task 3: Review, integrate, rehearse and publish

- [ ] Independently review production and release changes; resolve material
  findings and pass applicable CI before ordinary merges to main.
- [ ] Reconfirm current main, stable version plan, release/tag absence and all
  required review/acceptance conditions.
- [ ] Dispatch the canonical workflow with dry_run=true and skip_tests=false;
  inspect the actual stable tree, products, archives, notes and evidence.
- [ ] Publish 1.3.8 only after rehearsal and product acceptance succeed; verify
  release/p2/source identities and report any external follow-up accurately.

## Initial ruling

The user has explicitly requested execution of the previously described work.
Routine implementation and worktree decisions do not require another approval.
Any unavailable canonical workflow dispatch or third-party authentication remains
a real blocker to that final step, not permission to change what rehearsal means.

## Local completion checkpoint

The integrated mathematics source passes 1,222 cases with zero failures/errors;
nine native/upstream opt-in cases remain explicitly skipped locally. Independent
implementation review passes. The release workflow slice passes 27 focused tests
and independent review on its existing preparation branch. Native CI, ordinary
merges, canonical rehearsal and publication remain separate outstanding steps.
