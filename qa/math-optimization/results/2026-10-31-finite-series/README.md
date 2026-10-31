# Finite-series source qualification and measured Java performance — 2026-10-31

The actual generated Java was faster for all five measured large reductions on
this host. At small counts, the polynomial-loop pair had separated intervals;
the other four pairs were inconclusive. The matched Bouncy Castle scan retained
every accepted span and generated file. The existing complete-call BC benchmark
showed **no demonstrated speedup**: all eight pairs had overlapping intervals.

These results belong to public source
[`ad17e5b3617ba8225a47c8712b5a14d5162b73b7`](https://github.com/carstenartur/sandbox/commit/ad17e5b3617ba8225a47c8712b5a14d5162b73b7)
in [PR #1777](https://github.com/carstenartur/sandbox/pull/1777). Preparation
recorded a clean source tree. This result commit only adds evidence and documentation.
The SDK SHA-256 is
`50076c27b7a0c483dd2299ae40cb529e057a58d8324f273f03d15a00f69e2527`.

## What was measured

`FiniteSeriesQualification` ran production source analysis, proof/source replay,
ordinary Apply and exact Undo for five strict `PRESERVE_JAVA` int computations.
The archive retains their complete original and generated Java, rather than
substituting handwritten closed forms. The fixtures cover:

| Case | Original computation | Recorded minimum iterations |
| --- | --- | ---: |
| Polynomial loop | `sum = seed; sum += i * i` for `i = 0 .. n-1` | 4,096 |
| Range stream | `IntStream.range(0, n).map(i -> i * i + seed).sum()` | 4,096 |
| Affine recurrence | `sum = seed; sum = 3 * sum + i` | 8,192 |
| Geometric accumulation | Accumulate powers of 3; return final sum plus power | 8,192 |
| Product | `sum = seed; sum *= 3` | 4,096 |

Separate original/generated classes were compiled with Java target 17 and invoked
through the same primitive bridge. Each trial independently checked 64 varied
dataset inputs plus 32 boundary/seed combinations before timing. Those include
negative/empty counts, the heuristic threshold and its neighbours, large counts,
and extreme incoming int values. These finite comparisons supplement the existing
symbolic proof; they do not prove equivalence over every possible input.

The timed call includes the complete generated guards and fallback. The reviewed
primitive int/unit-stride guard lets small ranges choose the unchanged original
code before BigInteger setup. Large ranges retain all existing checks. No SDK
change, heuristic-weight adjustment or crossover fitting was made for this run.

## Final latency results

JMH 1.37 ran one thread, two independent JVM forks, three 500 ms warmups and five
500 ms measurement iterations per fork. The runtime was OpenJDK 25.0.5+8-LTS;
actual JVM arguments were `-Xmx512m -Xms128m -Xmx512m`. Each dataset rotates 64
inputs with varied incoming values. Small counts range from 16 to 31; large counts
range from 1,000,000 to 1,000,015. Timing finished at **18:52:48 UTC**.

Values below are means **± JMH 99.9% confidence half-width**, reconstructed from
all ten raw samples by the v2 reporter. `LOWER_TIME` requires separated intervals;
overlap is `INCONCLUSIVE`, and does not establish equal performance.

### Small counts — nanoseconds per call

| Case | Original ns/op | Generated ns/op | Generated/original | Evidence |
| --- | ---: | ---: | ---: | --- |
| Polynomial loop | 15.063 ± 0.862 | 10.985 ± 2.152 | 0.729 | LOWER_TIME |
| Range stream | 42.639 ± 3.270 | 47.967 ± 7.306 | 1.125 | INCONCLUSIVE |
| Affine recurrence | 10.601 ± 1.725 | 11.947 ± 0.992 | 1.127 | INCONCLUSIVE |
| Geometric accumulation | 10.584 ± 3.549 | 10.547 ± 1.628 | 0.997 | INCONCLUSIVE |
| Product | 8.563 ± 0.599 | 8.573 ± 1.518 | 1.001 | INCONCLUSIVE |

### Large counts — microseconds per call

| Case | Original μs/op | Generated μs/op | Generated/original | Evidence |
| --- | ---: | ---: | ---: | --- |
| Polynomial loop | 296.556 ± 19.864 | 0.771 ± 0.144 | 0.00260 | LOWER_TIME |
| Range stream | 530.084 ± 51.265 | 0.589 ± 0.081 | 0.00111 | LOWER_TIME |
| Affine recurrence | 588.395 ± 25.584 | 22.167 ± 1.412 | 0.03767 | LOWER_TIME |
| Geometric accumulation | 305.365 ± 19.626 | 15.830 ± 1.735 | 0.05184 | LOWER_TIME |
| Product | 286.319 ± 6.683 | 6.304 ± 0.648 | 0.02202 | LOWER_TIME |

These are local observations for the pinned fixtures and inputs. They do not
establish a universal speedup, a tuned crossover, or improvements for nested,
periodic, piecewise, BigInteger or every multiple-state computation. The host
reported an AMD EPYC 9V74 CPU under KVM, nine visible CPUs and an eight-CPU quota.
Other participating agents paused heavy builds during timing; this was not a
dedicated or frequency-pinned machine. The archive records the actual host view.

**Allocation limitation:** the GC profiler includes JVM/harness work and final
trial hash checks/cleanup outside primary latency timing. Its allocation samples
cannot isolate candidate allocations or support allocation improvement/regression
claims. All original latency and allocation samples, including final iterations,
are retained without filtering.

The v2 reporter binds the manifest SHA recorded as a real JMH parameter to the
source/implementation receipts. It requires the declared durations, all 20 rows,
the same JVM/JDK/flags across all pairs, and summary statistics consistent with
the ten raw samples. The pre-fix exploratory v1 archive is retained as history
only; it lacks this measured-manifest contract and supplies none of the table above.

## Matched Bouncy Castle qualification

The baseline is the completed
[Actions run 41222781112](https://github.com/carstenartur/sandbox/actions/runs/41222781112)
for head `04955e7cd00bc83525ecd757ec43f1f95720f249`. Its actual PR checkout was
`a8f3b9b2274fd5f8dc90674a0317162f63e2ee27`, with the same complete tree as that
head. Artifact `bc-math-qualification`, ID **13052892325**, is **25,477,494 bytes**,
SHA-256 `a03b8f8d1dca5d813af9da5ba628d858de341c9309df19bc0d32c1d0b76f8ff4`.
The full artifact is referenced, not duplicated in this repository.

After the primitive-guard change, both profiles were scanned again on `ad17e5b`;
the matched local run completed at 18:43:11 UTC. BC 1.85.2 source/binary archives,
profile hashes, SDK, Java target 17, selected entries, options and Java runtime
match. Both use 1,000,000 work units, 20,000 states and the five-second timeout.

| Profile | Parsed files, before → after | Parse errors | Extracted regions, before → after | Accepted spans, before → after | Complete generated files |
| --- | ---: | ---: | ---: | ---: | --- |
| Strict Java | 3,126 → 3,126 | 10 → 10 | 17,007 → 17,007 | 146 → 146 | All 58 byte-identical |
| Mathematical opt-in | 3,126 → 3,126 | 10 → 10 | 17,348 → 17,348 | 147 → 147 | All 62 byte-identical |

The comparison uses each accepted path/source SHA/offset/length, with **zero
removed and zero added spans**. The same ten multi-release/module entries retain
the same parse errors. Both reports say `COMPLETED_WITH_PARSE_ERRORS`; this is
source analysis and exact Apply/Undo evidence, not whole-library behavioral or
constant-time qualification. Full error details and input identities are archived.

The baseline's real `hashToCurve` plus point-encoding JMH covered P256, P384, P521
and BLS12-381 G1, each with 32- and 256-byte messages. **All eight comparisons are
INCONCLUSIVE.** Raw measurements and its original performance report are included.
No new BC JMH run is claimed. The complete generated SWU override remains
byte-identical, SHA-256
`3c3fe40670a3144489b067f1bc9c330ef3d2829e6144c087f68302f8446d4b45`;
the baseline timing is retained under its own original head.

## Verification and reproduction

The final corrected source tree passed **29 focused tests, zero failures/errors/
skips**: 9 qualification/report contracts, 6 final-review contracts and 14
primitive-guard contracts. The latter compile actual Apply output, compare
original behavior at small/threshold/extreme int inputs and instrument emitted
BigInteger factories to prove that small primitive ranges avoid setup while large
controls still reach it. Non-unit strides and wider types keep the previous path.

The preceding full standalone run recorded 893 tests, one failure and one existing
historical-mirror assumption skip. Its failure was an obsolete expectation that
an intentionally unchecked, JDK-shadowed small-range template would throw an NPE.
The test now requires original behavior for n=0/1 and still requires the large-case
NPE; all production source-rejection assertions are unchanged. Only that test
expectation changed afterward, followed by the 29-test clean run. A separate
attempt failed test discovery on a transient `.rsync-tmp` class and ran zero tests.
These logs are retained; this report does not label the final full 893-test suite
green. No new exclusions or skip switches were introduced. The absent optional
historical Git mirror does not skip the pinned-archive qualification above.

On a clean checkout of the recorded source, using Java 25 and Maven, reproduce
tests, both archive scans and finite-series measurements with:

```sh
mvn -f qa/math-optimization/pom.xml clean verify -Pbc-corpus,finite-series-benchmark
```

The observed run used the same maintained Maven goals in stages: the clean
targeted test gate, `bc-strict`/`bc-mathematical` execution, then
`prepare-finite-series`, `measure-finite-series` and `report-finite-series`.
The local wrapper selected Maven 3.9.16, Java 25 and a previously populated offline
dependency cache. No test-ignore flags, handwritten timing runner or replacement
test framework was used. Fresh runs write new receipts; retained results are never
overwritten or silently reused as a new measurement.

## Evidence files

[`qualification-data.zip`](qualification-data.zip) contains exact finite-series
fixtures and receipts, unmodified final JMH JSON, its v2 report, host data, matched
BC raw current reviews/generated sources, baseline manifests/raw JMH, derived
retention indexes, regression/run logs and clearly separated preliminary history.
The archive is 3,657,994 bytes.
`CONTENTS.md` explains the scope; `SHA256SUMS` covers every other archived file.
Original BC archives and separate nested proof transports are not duplicated;
the pinned Maven inputs and baseline Actions artifact identify them.

| Item | SHA-256 |
| --- | --- |
| Data ZIP | `19f060d6dc1c32223fef7b8699f5393cbcae307c7b5fb2f19b2aca745f53b1d5` |
| Final fixture manifest | `2a45a45a28853673f89d33608ca0ef01f439189848d9d6f9bde6e8b804df4581` |
| Final raw JMH JSON | `a62ffb1b344bd6dcc33f5d4ed0ac05702b1d85720697571390ca499abe2f0051` |
| Final v2 performance report | `b41f628fc1f1f0565207106749fa006f6c54d040c92250e5c3e7ab7ea53ea055` |

All retained generated Java files are measurement/corpus data. The maintained
implementation remains in the reviewed source tree identified above.
