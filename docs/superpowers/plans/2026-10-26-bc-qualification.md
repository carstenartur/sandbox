# Bouncy Castle qualification and source facts

> **For agentic workers:** Use superpowers:executing-plans. The user authorized all three steps together.

**Goal:** Measure the complete generated SWU call, reproduce comparable full BC scans, and improve proven Java receiver facts.

**Architecture:** Reuse ProjectMathScan and its exact generated sources as the benchmark input. Compile original/generated full classes against the same pinned BC archive in isolated class loaders. Extend the existing source-guard proof without changing mathematical rules or the SDK.

**Tech Stack:** Java 25, JDT, Maven/JUnit, JMH 1.37, existing vendored Regelsuche SDK.

**Spec:** The approved three-step recommendation in the conversation; this document records its concrete implementation scope.

## Global Constraints

- One ordinary PR against main; retain the existing Java 25 baseline and SDK.
- Reuse the existing scanner, profiles, tests and benchmark module; no additional workflow or Python automation.
- Preserve numerical proof, Java evaluation order, null/subclass/oversize fallback and exact Apply/Undo.
- Benchmark exact generated source, including guards; distinguish estimates, behavioral comparisons and timings.
- Keep the review slice below 1,500 changed text lines.

## Review Focus

- Mutable aliases, fields and nested execution frames must not lend facts to an unrelated receiver.
- Missing positive-modulus facts must retain the necessary fallback.
- Original and generated BC classes must really load from distinct compiled variants.
- A modified fixture or mismatched archive/profile must fail qualification.
- Different source versions, Java levels or search settings must not produce an apparently comparable count.

## Tasks

- [x] Establish current-core Maven/JUnit baseline and record toolchain.
- [x] Add failing source-fact regressions; implement bounded binding-based alias propagation; execute positive/negative and generated-code tests.
- [x] Extend existing ProjectMathScan with a verified benchmark fixture manifest; test failure on mismatched inputs.
- [x] Add a full BC Hash-to-Curve benchmark consuming the scanner fixture, with JUnit checks for class isolation, outputs and input drift.
- [x] Run both pinned BC profiles; measure original/generated complete calls with raw JMH JSON and allocation data.
- [x] Update help/QA reproduction instructions and summarize actual results and limitations.
- [x] Review the whole change, run applicable local checks, and create a PR directly against main; native Eclipse checks run in CI.

## Execution ledger

- Base: 6e9e4f0d09a2e93d5c3a2319dc6d983f31a448d5; no open PRs at start.
- Isolated fresh clone on codex/math-bc-qualification-and-facts.
- Ruling: extend existing fact propagation through immutable aliases first; avoid introducing broad interprocedural or mutable-memory analysis in this bounded change.
- Toolchain: OpenJDK 25+36, Maven 3.9.11; the unchanged vendored SDK is used by the standalone build.
- Initial three-step before/after full archive scans: 3,126 parsed files and 10 parse-error files in each run; 111 strict and 121 mathematical accepted regions. All generated source hashes match the baseline. The alias extension does not add BC hits in this archive.
- Regression tests first failed with retained guards; the implementation passes. Review additionally found and fixed direct-receiver regressions, a CI artifact layout issue and missing benchmark/report consistency checks.
- Initial combined Maven command (before the multi-release review fix): BUILD SUCCESS in 6:34, including 201 tests (zero failures/errors, one optional historical Git-reference skip), both full scans and all 16 JMH configurations. Final scan inputs and generated hashes match the before/after comparison.
- PR: https://github.com/carstenartur/sandbox/pull/1767, directly against main. Native Eclipse and distribution checks are tracked there; they are not replaced by this standalone qualification.

## Corrected full-call results

These measurements supersede the earlier base-version-loader run. Dependency lookups now use JarFile.runtimeVersion(), matching ordinary Java 25 multi-release loading, while compiled SWU variants retain precedence. The loader regression covers base/runtime/future entries and compiled overrides.

Complete public hashToCurve plus point encoding; construction and compilation are outside timing. OpenJDK 25+36, two forks, three 500 ms warmups and five 500 ms measurements per fork. All eight timing intervals overlap: no reliable runtime gain is established. The generated means are 0.1–7.8% higher in this run. Allocation changes range from -5.3% to +0.6%; seven are lower and one higher. No optimizer cost weights are fitted from these observations. This measures the existing emitted SWU optimization; the additional GenericSqrtRatio/store rewrites are not the benchmark's compiled override.

| Curve / message bytes | Original µs/op | Generated µs/op | Time change | Allocation change |
|---|---:|---:|---:|---:|
| P256_XMD_SHA_256/32 | 90.85 | 94.04 | +3.5% | -5.3% |
| P256_XMD_SHA_256/256 | 86.53 | 89.55 | +3.5% | -5.2% |
| P384_XMD_SHA_384/32 | 174.82 | 188.43 | +7.8% | -4.0% |
| P384_XMD_SHA_384/256 | 175.49 | 176.73 | +0.7% | -4.0% |
| P521_XMD_SHA_512/32 | 334.10 | 353.04 | +5.7% | -4.6% |
| P521_XMD_SHA_512/256 | 364.81 | 379.42 | +4.0% | -4.6% |
| BLS12_381_G1_XMD_SHA_256_SSWU_RO/32 | 764.17 | 765.83 | +0.2% | -0.5% |
| BLS12_381_G1_XMD_SHA_256_SSWU_RO/256 | 793.18 | 793.79 | +0.1% | +0.6% |

Corrected combined command: BUILD SUCCESS in 7:00, with 210 tests, zero failures/errors and one optional historical Git-reference skip; full scans now yield 114 strict and 124 mathematical regions. The [follow-up ledger](2026-10-26-math-followups.md) records the additional source changes and their limits. Native Eclipse checks remain on PR #1767.

Raw JMH SHA-256: `c1ea2d5932b3c0f177c44ccc2690919a49e8817cd4f572c6f6f917bda5d496f4`. The generated calibration JSON pairs all eight comparisons, retains raw intervals/allocation metrics and marks each timing comparison INCONCLUSIVE. Keep it with raw JMH samples, scan reports, manifests, ordinary generated/original sources and test reports. Do not substitute review-only source for the measured generated source.
