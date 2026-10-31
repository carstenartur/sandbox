# Pinned Regelsuche optimization SDK

## Current finite-series SDK

Published source revision: `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef` on
[`codex/verified-finite-sums`](https://github.com/carstenartur/regelsuche/tree/codex/verified-finite-sums).
Source tree: `4deea321c5fe2ae6441357f3c1d6c8ac8bdf3aa0`, identical to the
original reviewed local source `f7f73638e8b291a761aaa37dba44f3766f8e3608`.
Standalone JAR SHA-256: `50076c27b7a0c483dd2299ae40cb529e057a58d8324f273f03d15a00f69e2527`.

The existing Maven `MavenOptimizationSdkDistributionTest` passed locally with
Java 25.0.5 and Maven 3.9.16. It ran two clean six-module dependency/SDK builds,
compiled and executed two fresh external consumers with empty dependency caches,
and compared all ten distribution files byte for byte. The receipt is
`reproducibility-2026-10-31-published-series.json`; `qualification.json` records this
exact artifact, its inputs and fresh consumer output. This is a local qualification,
not a GitHub Actions result or an Eclipse product/distribution gate.

The distribution adds independently checked runtime polynomial sums in a binomial
basis, exact nested/periodic/piecewise compositions, a bounded rational telescoping
fragment, and affine/small linear transitions with division-free binary powering.
The external consumer checks the runtime sum 1..50000 against 1250025000, an affine
recurrence against 2047, and rejection of altered initial states and outputs.

The source revision was merged into Regelsuche `main` through [PR #1128](https://github.com/carstenartur/Regelsuche/pull/1128)
at merge commit `f7c7c36c99002ec93818775660de616af848e058`. The standalone
artifact remains a source-bound development distribution, not a public Maven
release; `publiclyPublished: false` in its generated receipt refers to the artifact
distribution. Generated Java remains independent of the SDK at runtime. Review the
Java adapter's admission and runtime fallback contracts separately from these
mathematical proofs.

The original local qualification is retained without relabelling in
`reproducibility-2026-10-31-extended-series.json` and
`qualification-2026-10-31-extended-series.json`. It bound source
`f7f73638e8b291a761aaa37dba44f3766f8e3608` and standalone JAR SHA-256
`8314ebdd784421264bd50c3e98af02a5abf76458630784381c8c6bea5e964a7c`.
The published commit has the same full source tree, but its different revision and
commit timestamp produce a different source-bound artifact; the current receipt
comes from a fresh qualification of the published revision.

## Preserved historical shared-rule qualification

Source revision: `2dbf8e4a11e2aabc78e5d5c51c10cfe23d8a86a3`.
Standalone JAR SHA-256: `5160ddecb4c4d4d521676d37c4dc639ae9b81f17fa37b6eaa3eead70f6a46efa`.

The committed upstream six-module dependency/SDK reactor passed with Java
25.0.5+8 and Maven 3.9.16. The existing deterministic SDK packager produced this
standalone JAR and its original qualification receipt. Its fresh external
consumer verified optimization, selected-path replay and checked overflow. The [native SDK qualification](https://github.com/carstenartur/sandbox/actions/runs/41171211843)
also passed two clean builds, 227 SDK tests per build and two fresh consumers; all
ten distribution files were identical. The unchanged native receipt is retained
in `reproducibility-2026-10-31.json`. Eclipse consumer CI is a separate gate.

This revision adds independent low-word polynomial congruence, disjoint-word
addition proofs, magnitude-aware numeric estimates, metered consumer ranking,
and complete explanation verification/replay/presentation accounting. Original
source execution and emitted candidate costs have separate ranking entry points;
runtime work is distinct from a weighted storage/readability score. The default
SDK optimizer remains compatible. Checker revision v12 and generator revision
v11 reject superseded evidence.

Sandbox uses the shared search and proof engine, with source-specific magnitude
bounds and actual emitted capture/guard/local costs. Generic receiver safety
ceilings are not treated as operand-size measurements. No second proof engine,
mandatory CAS process or measured speedup is introduced. Fresh full corpus and
consumer test results are recorded in the October 30 implementation qualification.
Historical screenshots and benchmark receipts retain their original provenance.

## Preserved historical qualification (previous SDK)

The following results and the unchanged `native-qualification.json` and
`reproducibility.json` refer only to the previous source revision
`8f39dfe5f52fb0610164e18dac624a4bbfaaa49d`, not to the current JAR. They are retained
as historical receipts, not relabelled as a new measurement.

Previous standalone JAR SHA-256:
`2bbb776435a09a0bc61339312ae4f58c3b4f8dde67c2925f7b1c8ee686aee52c`.
The existing native SDK qualification completed two clean dependency-reactor
builds and two freshly compiled external consumers in
[run 38200207089](https://github.com/carstenartur/sandbox/actions/runs/38200207089).
Each build ran all 176 SDK tests in 33 suites with no failures, errors or skips.
The fixed 1,092-work final-preparation regression passed in both builds.
The consumers verified optimization, selected-path replay, checked overflow,
and the public original/candidate domain API. All ten distribution files matched
byte for byte. This qualification used Temurin 25.0.4.1 and Maven 3.10.0.
The historical `native-qualification.json` records its source tree, native run,
artifact hashes, counted test results, toolchain and original EPL-2.0 notices.
These old test counts and budget results must not be attributed to the new SDK.

This is a source-bound development distribution, not a public Maven release.
Generated Java needs no SDK runtime. Cost improvements are estimates, not timings.
