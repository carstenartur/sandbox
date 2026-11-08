# Mathematics corpus and generated-Java qualification

The scanner uses the production extractor, optimizer, independent checker,
emitter and complete generated-compilation-unit validation without modifying the
input archives. Qualification tests and benchmarks compile and execute separate
copies of the original and generated source. These behavioral comparisons
supplement symbolic proof; finite samples are not the proof.

## Explicit compiler diagnostics

Archive scanners use the [shared strict compiler profile](../../sandbox_math_cleanup_test/ARCHITECTURE.md#compiler-diagnostics).
`ProjectMathScan` records the complete options in `inputs.json` and original/generated
warning IDs, arguments, positions and messages in `review.json`. Compare these
settings alongside source, binary and mathematics profile hashes; historical scans
using permissive defaults do not qualify the current strict configuration.

`ProjectMathScan` additionally accepts `--tasks` after its six required arguments.
This writes bounded computation-task summaries and diagnostics to `review.json`,
including typed inputs/outputs, source bindings and the ordered read/commit/store
schedule. The same source snapshot reconstructs the full typed graph through
`JavaComputationExtractor.extractTasks`; the summary does not recursively expand
shared expressions or define a new CAS wire protocol. These exported summaries
are inspection data (`editable: false`). Ordinary cleanup separately attempts
[verified whole-task rewrites](../../sandbox_math_cleanup/README.md#verified-whole-task-rewrites),
which appear in the accepted cleanup results only after source and proof checks.
Neither a task summary nor an accepted rewrite establishes a measured speedup.

The [2026-10-31 qualification](../../docs/superpowers/plans/2026-10-31-java-mathematics-context-qualification.md)
records the exact SDK, 714-test mathematics run and matched full-corpus comparison:
146 strict-Java and 147 mathematical-opt-in accepted regions, retaining all
120/129 baseline spans. These are the dated report's results for its pinned
inputs and profiles, not a new test run or measured speedup. The preceding
[2026-10-30 qualification](../../docs/superpowers/plans/2026-10-30-math-optimization-qualification.md)
records why the earlier 120/129 totals did not retain every older accepted span.

## Current BC qualification (Java 25 / Maven)

The maintained Maven entry point reuses the existing production core, scanner,
JUnit contracts and JMH implementation. From the repository root:

```sh
mvn -f qa/math-optimization/pom.xml clean verify -Pbc-corpus,bc-benchmark
```

`verify` alone runs the core/QA tests and the entire headless mathematics test
package, including Java target, safety-profile, generated-code and array-ordering
contracts reused from the native suite. Its corpus fixtures use the same pinned
archive versions as that suite. Native Eclipse workspace
and UI tests remain in the existing Tycho modules; this build does not replace
them. The optional historical Git-reference test needs its existing mirror and
reports a skip when it is absent. The new full-call BC tests always receive the
pinned archives from Maven.

`bc-corpus` scans the same Maven BC 1.85.2 binary/source archives under both
`bc-strict` and `bc-mathematical`: Java 17, 1,000,000 work units per file,
20,000 states and the existing five-second timeout. The source SHA-256 is pinned
in the POM. Both profiles use all archive sources as context, retain parse errors
explicitly, and write separate fresh `target/bc-*` directories. The older Git-tree
runner below has a different Java-8/BigInteger-only contract and is historical
reference evidence, not a comparable scan of this archive.

`bc-benchmark` consumes `target/bc-mathematical/benchmark.properties` and the
exact ordinary `generated/` SWU source, including every guard and fallback. It
compiles complete original/generated classes separately, verifies that the real
call path loads each override, and compares 64 deterministic inputs before timing.
The timed body is the complete public `hashToCurve` call plus point encoding;
construction, compilation and optimization are outside measurement. Four curve
profiles and two message lengths run with two forks, three warmups, five measured
iterations and JMH's GC profiler. Raw timing/allocation samples are retained in
`target/bc-hash-to-curve.json`. Binary, source, report and manifest hashes are
checked before and after measurement. This measures numerical behavior and
performance on the recorded JVM, not constant-time properties.

Dependency lookups use the running JVM's multi-release JAR view; the separately
compiled SWU override still takes precedence. `target/bc-performance-review.json`
pairs all eight original/generated configurations and records the raw JSON hash,
timing intervals and allocation metrics. Overlapping intervals are explicitly
`INCONCLUSIVE`; directional evidence is reported only for separated intervals.
These complete-call observations inform review and do not automatically fit or
replace optimizer cost weights. Earlier base-version-loader timings are obsolete.

Retain the reports, manifests, ordinary original/generated files and raw JSON
together. Compare runs only with matching source/binary/profile hashes, Java
target, selected entries and parse-error set. Do not reuse the historical
115/124 counts as a baseline for a different SDK or archive. Run `clean` for a
fresh qualification; the scanner deliberately refuses to overwrite prior output.

## Complete BLS source cohort and whole-call measurements

The separate, explicit `bc-bls.properties` profile retains the original BLS
qualification limits: Java 17, 20,000,000 work units, 20,000 states and a
five-second per-file timeout. It opts into
mathematical semantics and the `AUDITED_OPENJDK_25_0_5_8` scalar deployment
contract. The general `bc-mathematical` profile retains its own independent
runtime choice. This contract must be satisfied by the deployed application;
the target Java version alone does not grant it.

```sh
mvn -f qa/math-optimization/pom.xml clean verify -Pbc-bls-benchmark \
  -Dsandbox.math.bc.upstream=true
```

Preparation runs the ordinary production scanner over five complete pinned
BC 1.85.2 classes: `BLS12_381G2HashToCurve`, `BLS12_381Pairing`, `Fp12Element`,
`Fp2Element` and `Fp6Element`. Every original source is checked against its
archive entry. Ordinary Apply/byte-exact Undo and emitted-source checks come
from the scanner. Unchanged Fp12 remains an explicit compiled cohort member.
`cohort.json`, original evidence, source dependencies, profile, implementation
and archive hashes bind the exact effective source. The optional upstream gate
uses the shared JGit fixture to verify tag `r1rv85v2` at
`854bd5c4286e1530132418906bf6175da7d9d83c` and compare all five complete files.

The JMH harness compiles all five originals and all five ordinary generated
sources in separate namespaces. Compiled cohort classes cannot silently fall
back to the original JAR; other dependencies use its current multi-release view.
Each trial compares sixteen varied complete inputs and proves that the exercised
call paths load the compiled classes. Timed operations are complete G2
hash-to-curve plus compression for 32/256-byte messages, or complete pairing
plus serialization of all twelve resulting field components. Pairing input
points, class compilation and source optimization are prepared outside timing.

Two forks, three 500 ms warmups, five 500 ms measurements and GC profiling produce
`target/bc-bls-jmh.json`. The pre-measurement fixture hash is a raw JMH parameter.
`target/bc-bls-performance.json` requires all three pairs, identical settings and
environments, and reconstructs means and 99.9% intervals from all ten samples.
Preparation records the JVM executable, JDK version, VM name and VM version in
the manifest-bound `cohort.json` qualification receipt. Runner, trial setup and
teardown require that same runtime; the reporter checks every raw row against
the persisted identity, including when all rows and the reporting process agree
on a different JDK installation. JVM arguments remain separately recorded because
preparation and measurement intentionally use different heap arguments.
Overlap is `INCONCLUSIVE`; separated intervals give a local direction. All
guards, fallbacks and output encoding remain timed. GC samples also include
trial verification/cleanup and cannot isolate candidate allocations.

For an already compiled, freshly qualified tree, preparation can be invoked
separately with `-Pbc-bls-benchmark compile exec:exec@prepare-bls-source-cohort`,
then timing/reporting with `exec:exec@measure-complete-bls-calls` and
`exec:exec@report-complete-bls-calls`. Existing fixtures/raw results are never
overwritten. Preserve the complete fixture and raw report together. No result
automatically changes optimizer weights or establishes a general speedup or
cryptographic constant-time guarantee.

## Finite-series generated-Java measurements

The [2026-10-31 measured results](results/2026-10-31-finite-series/README.md)
record the public source revision, real original/generated timings, matched BC
retention, raw data and measurement limits. All five large synthetic pairs had
lower measured time; four of five small pairs and all eight BC pairs were
inconclusive. These observations do not establish a general or BC speedup.

The optional profile measures the existing finite-series implementation through
its actual source analysis, proof/source replay and ordinary Apply output:

```sh
mvn -f qa/math-optimization/pom.xml clean verify -Pfinite-series-benchmark
```

It prepares polynomial loops, polynomial range streams, affine recurrences,
geometric accumulation and invariant-factor products in strict `PRESERVE_JAVA`
int arithmetic. The original and generated complete Java classes are compiled
separately with `--release 17`. An identical primitive bridge calls each class;
there is no optimizer or interpreter in the timed body. Every trial compares
outputs on deterministic varied incoming values, negative/empty counts, counts
around the recorded heuristic crossover, and large counts before timing.

The measured datasets rotate 64 inputs: counts 16–31 exercise the original
fallback, and counts 1,000,000–1,000,015 exceed the checked crossover. All emitted
guards, allocations, modulo arithmetic and fallback code remain in the compiled
class. The benchmark uses two JVM forks, three 500 ms warmups, five 500 ms measured
iterations and the GC profiler. Run it without other CPU-heavy builds; host and
JVM differences still limit transfer of the results to other machines. GC profiler
allocation samples also include JVM/harness work and final-trial hash checks and
cleanup, which are outside primary latency timing. Those values cannot isolate
candidate allocations or establish allocation improvements/regressions; all raw
samples are retained without dropping the final iterations.

`target/finite-series/` retains exact source files, preparation observations,
options, implementation/SDK hashes and the source revision/dirty-state receipt.
Each trial checks these hashes before and after measurement. Raw samples remain
in `target/finite-series-jmh.json`; `target/finite-series-performance.json` pairs
all ten configurations, retains allocation measurements and labels overlapping
timing intervals `INCONCLUSIVE`. The runner records the manifest SHA as an actual
JMH parameter. The report requires complete matching metadata and the declared
500 ms durations, then reconstructs means and 99.9% confidence intervals from all
ten raw samples using JMH statistics. Swapped fixtures and invented summaries
are rejected. The profile refuses existing fixture or raw-result output:
use `clean` or retain the earlier run before generating another one. The ordinary
Maven `verify` also runs the short equivalence/tampering contracts without timing.
These synthetic reductions complement the real BC corpus; no BC speedup or
universal crossover improvement follows from them.

## Historical recovery and evidence

The workspace maintenance incident deleted the earlier checkout, full corpus
reports and JMH raw samples. The literal recovered files remain verbatim under
[`history/pre-reset/`](history/pre-reset/RECOVERY-NOTICE.md), with recovery
provenance and checksums. Their reports describe the earlier build and are not
fresh qualification of this reconstruction. No missing samples are fabricated.

`MathematicsOptimizationBenchmark.java` is restored from a complete recovered
source read. The scanner and test sources are new reconstructions. Fresh corpus,
test and measurement artifacts must record their own current hashes and output
directories. The [fresh provisional 14-test run](results/reconstruction-provisional-4fc49eaf/README.md)
passed, including both exact upstream trees. That provisional status is historical;
no historical throughput or allocation result is a current claim. Current
target/profile tests, frozen independent controls and generated safety-profile
measurement procedures are described in [release qualification](RELEASE_QUALIFICATION.md).

## Inputs and contracts

The known development reference is [bcgit/bc-java#2455](https://github.com/bcgit/bc-java/pull/2455),
original `ab16374d37c7e18c4090eb8838ebbd72a92593f2` and candidate
`3d56837c3fe6c4a30fc7639b806958649e143210`. It is not an unseen transfer task.
`references` fetches exact objects with JGit, verifies their advertised local refs,
uses the existing shared `PinnedGitRepository` fixture, checks exact source and
MIT-license Git blobs, and saves the two original files and actual source diff.
Upstream copyright and permission notices are retained with the snapshots.

The synthetic local block explicitly establishes nonnegative exponents through a
Java sign-clearing mask, a positive literal modulus, exact BigInteger factory
receivers and affine relationships. Tests exercise renamed variables, another
affine combination, unknown signed exponents, zero modulus and a nonlinear
exponent. Names and comments supply no assumptions. Actual original and candidate
upstream files are checked separately; unsupported field/control-flow facts are
diagnosed. An unchanged file is not a proof of optimality or rediscovery.

The full corpus includes every original `.java` file, including tests and alternate
source sets. The parser uses a common Java 8 source baseline and the runner's JDK
boot classes, not `--release 8` API checking or the upstream Gradle build. Files
with compiler errors cannot supply qualified candidates. Original parsing and
generated-CU validation receive the same filename and source/classpath context.

## Reproduce

Use JDK 25 and a colon-separated absolute classpath file containing the vendored
SDK jar, JDT/platform/Text dependencies, JUnit Platform Console, JGit and its
dependencies, JMH 1.37 core/annotation processor, jopt-simple and commons-math3.
This harness compiles the repository's core and shared JGit fixture sources. It
does not build or load a sibling optimizer development checkout.

```sh
export MATH_QA_JAVA=/absolute/jdk-25/bin/java
export MATH_QA_JAVAC=/absolute/jdk-25/bin/javac
bash qa/math-optimization/run.sh references /absolute/qa.classpath /absolute/references-run
MATH_QA_BC_MIRROR=/absolute/references-run/cache/bc-java.git \
MATH_QA_WRITE_FIXTURES=/absolute/fresh-fixtures \
  bash qa/math-optimization/run.sh test /absolute/qa.classpath /absolute/test-run
MATH_QA_CACHE=/absolute/references-run/cache \
  bash qa/math-optimization/run.sh corpus /absolute/qa.classpath /absolute/corpus-run
MATH_QA_BENCHMARK_FIXTURES=/absolute/fresh-fixtures \
  bash qa/math-optimization/run.sh benchmark /absolute/qa.classpath /absolute/benchmark-run
```

Each invocation requires a fresh output directory. The default heap is 512 MiB
for tests/corpus (`MATH_QA_HEAP` can override it). The pinned reference test is
explicitly skipped unless `MATH_QA_BC_MIRROR` is set. Fixture export refuses to
overwrite different existing sources. Keep measured fixture sources immutable;
regenerate into a new directory when qualifying a different build.

## Historical Git-tree reports and expression benchmarks

`files.tsv` contains source hashes, compiler errors, resolved BigInteger method
calls, extracted regions, supported regions, verified candidate regions, rejected
regions, classification and diagnostics. These count different things. Statements
that never form a region can still have extraction diagnostics. All candidate
runtime cost changes remain estimates. Applied changes and measured corpus
runtime improvements are always zero.

The reference file is marked `INITIALIZATION_REQUIRES_PARAMETER_REVIEW`; other
files are `UNCLASSIFIED_NO_APPLY`. Constructor arguments are not automatically
public. Numeric equality does not prove constant-time behavior; any future apply
requires individual review of input secrecy, guards, memory accesses, variable-time
operations and cached intermediates (see [RFC 9380 section 10](https://datatracker.ietf.org/doc/html/rfc9380#section-10)).

`implementation.properties` hashes the whole math core class closure (including
inner/helper classes), QA classes and complete SDK jar. Qualification requires a
jar, checks implementation drift, and records the SDK's embedded provenance.
It also fingerprints the reused JGit fixture, benchmark/JMH classes and every
classpath jar. Scoped tests capture receipts before and after execution.
`sandbox-source.properties` records HEAD, dirty status, the HEAD-to-working-tree
diff digest and hashes of changed/untracked files. A dirty build is reported as
dirty. Every scanned source is verified unchanged at the end of analysis.

JMH compiles the exact saved original and emitted Java bodies with `--release 17`,
checks equal outputs before measurement, and measures both over the same rotating
input arrays. The measured runtime contains no optimizer or interpreter. Two
forks, three warmups, five measurement iterations and `-prof gc` capture time and
allocations in raw JSON. All emitted normalization, allocations and guards remain
in the measured body. The fixture's single observed parse/search/proof/emission
time is reported separately. A local result is not a Bouncy Castle public-API or
cryptographic performance claim; a missing gain must be reported as such.
Source and compiled-fixture receipts are captured with the actual benchmark
compiler before and after measurement; a drift mismatch fails qualification.
