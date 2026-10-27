# Mathematics corpus and generated-Java qualification

The scanner uses the production extractor, optimizer, independent checker,
emitter and complete generated-compilation-unit validation without modifying the
input archives. Qualification tests and benchmarks compile and execute separate
copies of the original and generated source. These behavioral comparisons
supplement symbolic proof; finite samples are not the proof.

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
