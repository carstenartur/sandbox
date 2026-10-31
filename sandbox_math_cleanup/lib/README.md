# Pinned Regelsuche optimization SDK

## Current shared-rule integration candidate

Source revision: `2dbf8e4a11e2aabc78e5d5c51c10cfe23d8a86a3`.
Standalone JAR SHA-256: `5160ddecb4c4d4d521676d37c4dc639ae9b81f17fa37b6eaa3eead70f6a46efa`.

The committed upstream six-module dependency/SDK reactor passed with Java
25.0.5+8 and Maven 3.9.16. The existing deterministic SDK packager produced this
standalone JAR and the unchanged `qualification.json` receipt. Its fresh external
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
