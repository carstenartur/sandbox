# Mathematics corpus and generated-Java qualification

This reconstructed harness analyzes source without applying proposed edits to
Bouncy Castle or executing upstream source. It uses the production extractor,
optimizer, independent checker, emitter and complete generated-compilation-unit
validation. The local execution tests supplement symbolic proof; finite samples
are not the proof.

## Recovery and current evidence

The workspace maintenance incident deleted the earlier checkout, full corpus
reports and JMH raw samples. The literal recovered files remain verbatim under
[`history/pre-reset/`](history/pre-reset/RECOVERY-NOTICE.md), with recovery
provenance and checksums. Their reports describe the earlier build and are not
fresh qualification of this reconstruction. No missing samples are fabricated.

`MathematicsOptimizationBenchmark.java` is restored from a complete recovered
source read. The scanner and test sources are new reconstructions. Fresh corpus,
test and measurement artifacts must record their own current hashes and output
directories. At this checkpoint the complete reconstructed pipeline has not yet
been qualified. No historical throughput or allocation result is a current claim.

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

## Reports and performance

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
