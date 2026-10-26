# Pinned Regelsuche optimization SDK

## Current shared-rule integration candidate

Source revision: `42caf2f862b5c606d6b516ff8f99279f9928d064`.
Standalone JAR SHA-256: `aef1b7c4002bb76cd122e7a15e45692215f56cc4c56c0c2f3b4545289da709f4`.

The existing native Maven/JUnit SDK qualification and staging jobs both passed
in [run 40422423698](https://github.com/carstenartur/sandbox/actions/runs/40422423698).
`qualification.json` is the unchanged packager receipt from that run. The JAR
and receipt are copied from the exact Git blobs staged on
`sdk-transfer/42caf2f862b5c606d6b516ff8f99279f9928d064`; they are not rebuilt or
edited in the consumer repository. The receipt records a fresh external consumer
verifying optimization, selected-path replay, checked overflow, and the public
original/candidate domain API.

This revision adds independently checked high/low word reconstruction and restores
shared computation graphs after cold transport in the common Regelsuche resolver.
Resolver traversal and shallow structural keys remain charged to the work budget;
the affine domain API uses the same metered traversal. No second CAS or proof
implementation is introduced in Sandbox. The complete upstream dependency/SDK
verification passed, including 201 SDK tests. Preparation and derivation capture
reuse already resolved producer data; external proof replay remains independent.

The existing signed-additive capability belongs to Regelsuche's ordinary core rule
inventory and its existing term-collection pack. The existing Java adapter lifts
that inventory; there is no Paeth-specific recognition or separate mathematical
search in Sandbox. The independently checked Java semantics and cost gates
remain mandatory.

`NewUpstreamMathPilotTest` now requires the real Commons Imaging predictor to
reduce its five additions/subtractions to at most three, in addition to proof,
complete-class compilation, exact Apply/Undo and execution comparisons. This is
an acceptance requirement. Native Sandbox CI and corpus execution are separate
from SDK packaging.
The JCodec Java-8-adapted corpus remains a historical algorithm test, not a Java-6
compatibility qualification or the primary current-usefulness evidence.

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
