# Extended finite-series integration — qualification record

Date: 2026-10-31. This is a local draft/reference integration of the requested Regelsuche and Sandbox finite-series extensions. Publication and merge status are recorded separately from build/test results.

## Originally reviewed implementation and local qualification

- Regelsuche source: `f7f73638e8b291a761aaa37dba44f3766f8e3608`, branch `codex/verified-finite-sums`.
- Sandbox verified integration: `d056a367df60b87170649b8cf6dca5d21f8dbb89`, branch `codex/verified-sum-loops`.
- SDK version: `0.5.1-issue1657-f7f73638e8b2`.
- Originally qualified SDK SHA-256: `8314ebdd784421264bd50c3e98af02a5abf76458630784381c8c6bea5e964a7c`.
- Regelsuche comparison base: `2dbf8e4a11e2aabc78e5d5c51c10cfe23d8a86a3`.
- Sandbox comparison base: `ab1f24911563efed0eb765b6613b96f3dc4ddc1f`.

## Published-source SDK qualification and current pin

The authorized publication created Regelsuche commit `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef`
on `codex/verified-finite-sums`, with public main `101e504a82375a74882c3a92a8cf9adf0d89069a`
as its parent. Its complete tree is `4deea321c5fe2ae6441357f3c1d6c8ac8bdf3aa0`,
exactly equal to the reviewed local commit `f7f73638e8b291a761aaa37dba44f3766f8e3608`.
Publication changed the commit identity, not the reviewed implementation.

The existing SDK distribution contract was rerun from a clean checkout of this
published revision. The current bundled version is `0.5.1-issue1657-3a9250062f3f`,
with standalone JAR SHA-256 `50076c27b7a0c483dd2299ae40cb529e057a58d8324f273f03d15a00f69e2527`.
The new source-bound artifact has different bytes because the distribution records
the source revision and commit timestamp. Its current generated receipt is
`sandbox_math_cleanup/lib/qualification.json`; the published-revision repeatability
receipt is `sandbox_math_cleanup/lib/reproducibility-2026-10-31-published-series.json`.
Five mathematics screenshot fixture metadata files identify this current artifact.
This repin changes no product Java source, API or Maven POM.

The unchanged `reproducibility-2026-10-31-extended-series.json` and preserved
`qualification-2026-10-31-extended-series.json` record the original local qualification.
Earlier native and historical receipts retain their original revisions and hashes.
The qualification table below records the original reviewed integration; the
published-revision receipt records the fresh SDK distribution gate separately.

## Delivered families

| Family | Regelsuche mathematical API | Sandbox source fragment |
|---|---|---|
| Polynomial sums | Independently verified Newton/binomial plan, index degree at most 8, invariant parameters, symbolic start and runtime count. | Same-width int/long additive reductions, constant or invariant runtime bounds, positive/negative constant strides. |
| Ordered and multiple accumulators | Source-bound initial values and separately verified outputs. | Up to eight states/outputs; bounded local temporaries; ordered updates retain their source semantics. |
| Affine and geometric recurrences | Verified augmented transition matrices and binary powering. | `s = a*s + p(i)`, geometric power plus sum, invariant-factor products, coupled affine states. |
| Nested sums | Exact rectangular/triangular composition with affine inner counts. | Two levels; inner start 0 or 1, positive unit stride, bound a scoped outer counter or invariant local; independent width/termination guards. |
| Periodic sums | Up to 32 interleaved child plans under one budget. | One if/else update in an ascending unit-stride for-loop, predicate `i % m == r`, literal period 1..32. Nonnegative-start fast path; original execution for runtime negative starts. |
| Piecewise sums | Up to 32 consecutive exact ordinal intervals; constant API endpoints. | One invariant split and two polynomial branches, all `<`, `<=`, `>`, `>=` orientations, exact clamped counts. |
| Exact rational telescoping | Adjacent affine denominator factors, independently verified identity and constant-time lattice pole detection. | SDK-only; Java truncating integer division is not treated as exact rational arithmetic. |
| Exact BigInteger | Exact polynomial and linear contracts with explicit growth bounds. | Resolved JDK arithmetic and strictly value-observed accumulators; guarded nonnull exact-class inputs, no enclosing repetition. |
| Loop and stream forms | Caller supplies proved iteration semantics. | Canonical for/while/do, preserved live while/do counter, sequential IntStream/LongStream range sums and admitted pure maps. Partition-specific grammar is narrower. |

These are bounded supported fragments, not arbitrary compositions of every row. Nonlinear state products, infinite series, floating-point reassociation, BigDecimal scale/rounding changes, effects, unknown calls and unsupported mixed-width intermediates remain outside the proof contracts.

The Java runtime templates apply only to `LOWER_ESTIMATED_RUNTIME` under strict `PRESERVE_JAVA` with mathematical opt-in off. Native-width header evaluation is captured before exact count arithmetic. Short ranges and failed runtime guards retain the original loop. The crossover is an explicit operation estimate, not a measured speedup. Generated Java depends only on JDK APIs.

## Independent verification and resource boundaries

Search results, candidate flags and hashes are not proofs. Numerical verifiers independently reconstruct and check the source identity. Java replay re-admits source controls/bindings, checks all mathematical transport, regenerates the complete replacement, compares actual emitted edits, and checks declaration and generated JDK bindings.

Review corrections cover aggregate output work, failed later-child verification/replay accounting, repeated partition constructor validation, nested counter-width guards, BigInteger identity observations across outer-loop backedges, bounded output and intermediate fraction/string construction, and bounded fresh-name scanning. The final wave additionally checks actual package/type bindings for introduced JDK receivers and enforces shared monotonic deadlines across all children and source replay entry work. Meaningful regression tests reproduced the failures before the fixes.

Expression transport is bounded to 2048 nodes/depth 64, evolving states to 8 and temporaries/updates to 32 each. Periodic/split models share decreasing work allowances; failed attempts retain conservative reservations. Generated templates are bounded to 60000 characters. The period-32 positive example needs a configured 5000000-unit request allowance; a configured 1000000-unit qualification allowance may reject it. The actual UI default in `MathCleanUpOptions.defaults(...)` is 100000 work units. Valid mathematics can fail closed when configured resource limits are insufficient.

## Qualification evidence

| Gate | Exact result |
|---|---|
| SDK release distribution | Two clean source builds; 2555 JUnit tests per build, including 266 SDK tests; zero failures/errors/skips. |
| Standalone SDK consumers | Two fresh consumers pass polynomial and affine examples and reject tampered results. |
| Reproducibility | Ten distribution files byte-identical across both builds. |
| Integrated Java focused suite | 150 tests; zero failures/errors/skips, including all six final-review regressions. |
| Full math qualification suite | 870 tests on exact code head d056a36; zero failures/errors; one existing assumption skip. |
| Native Eclipse/Tycho reactor | Six modules successful, including compiled/packaged mathematics cleanup plug-in. |
| Repository policy and pinned Git fixtures | 12 tests; zero failures/errors/skips. |
| Task reviews | All reported task findings fixed and independently re-reviewed. |
| Final whole-branch review | Complete; its two findings were fixed in one wave and independently re-reviewed. F1/P1 and F2/P2 are ADDRESSED; scoped review APPROVED, no open findings. |

The sole skipped test is `BouncyCastleMathReferenceTest#exactPinnedTreesRemainUnchangedWithHonestUnsupportedDiagnostics`. Its existing assumption requires `math.qa.bcMirror` to point to a verified JGit mirror. No failure-ignore switch or new quarantine was introduced. The native gate is compilation/package plus policy tests; it is not a claim of a full SWTBot product/UI qualification or remote CI pass.

The complete headless command was `bash /workspace/scratch/0cab49039492/build-tools/mvn -f qa/math-optimization/pom.xml test`. Native qualification selected `sandbox_math_cleanup,sandbox_common_test -am`, the existing `RepositoryPolicy*Test,PinnedGitRepositoryTest` tests, and the documented draft size exception. SDK qualification used the existing `MavenOptimizationSdkDistributionTest` under `-Psdk-release`.

## Decisions and their practical cost

1. Independent mathematical and Java work ran in isolated branches/worktrees, with explicit integration seams and reviews. A wrong interface assumption costs integration work; isolated ownership prevents one worker overwriting another's edits.
2. The requested broad integration stays a draft/reference branch. Its size needs a documented policy exception and later reviewable merge slices; successful tests do not make the entire integration merge-ready.
3. Exact rational, periodic, piecewise and nested families use bounded contracts. Unsupported inputs remain unchanged or are rejected; the cost is incomplete admission rather than silently changing Java semantics.
4. Java periodic and piecewise admission was completed in addition to the SDK API. This added implementation/review time but closes the practical consumer gap in the approved feature scope.
5. Publication rejection did not stop authorized local implementation. The size exception is prepared for an ordinary main-based draft PR. Publication was later explicitly authorized; the published-source SDK qualification above records the resulting source identity. Remote CI remains a separate gate.
6. Final review boundaries were accepted explicitly: cost is an estimate; unsupported semantic families remain excluded; interactive UI, the absent pinned mirror and remote CI are not claimed qualified; cancellation is cooperative polling rather than hard real-time preemption. This limits the delivered claims to the checked mathematical/Java fragments and recorded gates.

## Publication and recovery

The original finite-iteration foundation was merged independently through Regelsuche PR #1127 on 2026-10-31. That PR is closed/merged; the extensions require a new ordinary draft against main. At the last read-only refresh, Regelsuche main was `101e504a82375a74882c3a92a8cf9adf0d89069a`; Sandbox main was `7ecd5e30cdd3ad93ada97bf978a818d5b3c37f58` with unrelated documentation changes only. These observations do not claim a future merge has been qualified.

Native push was initially rejected by automatic approval review because the exact destination had not been established as authorized/trusted. A subsequent read-only check established both public repositories are owned by the connected user. The user later explicitly authorized publishing both verified branches and draft PRs. Native Git lacked credentials; the authorized GitHub App published the Regelsuche source with the identical full tree under the new commit identity recorded above. This record proves the stated local qualifications and does not assert remote CI or merge readiness; the accompanying delivery receipt records the final Sandbox and PR publication outcomes.

All implementation is committed locally. Git bundles and review/build receipts accompany the completion record, so the work can be resumed even if the conversation ends. A later documentation-only qualification commit does not change the tested code head stated above.
