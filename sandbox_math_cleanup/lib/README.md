# Pinned Regelsuche optimization SDK

Source revision: `8f39dfe5f52fb0610164e18dac624a4bbfaaa49d`.
Standalone JAR SHA-256: `2bbb776435a09a0bc61339312ae4f58c3b4f8dde67c2925f7b1c8ee686aee52c`.

The existing native SDK qualification completed two clean dependency-reactor
builds and two freshly compiled external consumers in
[run 38200207089](https://github.com/carstenartur/sandbox/actions/runs/38200207089).
Each build ran all 176 SDK tests in 33 suites with no failures, errors or skips.
The fixed 1,092-work final-preparation regression passed in both builds.
The consumers verified optimization, selected-path replay, checked overflow,
and the public original/candidate domain API. All ten distribution files matched
byte for byte. This qualification used Temurin 25.0.4.1 and Maven 3.10.0.

`qualification.json` is the original packager receipt; `reproducibility.json`
is the original Maven/JUnit receipt. `native-qualification.json` records the
source tree, native run, artifact hashes, counted test results and toolchain.
The downloaded JAR and qualification receipt match the Git blobs staged by the
existing SDK transfer workflow. All four distribution archives retain the
original EPL-2.0 notice for the affine analysis migrated from Sandbox, alongside
the Regelsuche license and third-party notices.

This revision reuses the immutable source and candidate costs already prepared
and charged within an optimizer request. The independent final proof and the
trusted public Candidate constructor's preparation remain fully charged.
Source-trace multiplicity, Java arithmetic semantics and request limits are
preserved. Shared affine integral domains, established core-rule premises,
bounded typed BigInteger modular shifts and same-modulus inner-reduction
proposals remain available through the existing search.

This is a source-bound development distribution, not a public Maven release.
Generated Java needs no SDK runtime. Cost improvements are estimates, not timings.
