# Mathematics cleanup qualification corpus

This is an analysis-only qualification harness for the mathematics cleanup. It
does not apply changes to Bouncy Castle. It does not execute corpus source while
analyzing it. The original and candidate for
[bcgit/bc-java#2455](https://github.com/bcgit/bc-java/pull/2455) are known development
references, not an unseen transfer task.

See [the recorded observations](RESULTS-2026-10-04.md) and the raw artifacts under
`results/2026-10-04/`. The sandbox harness and local fixtures use EPL-2.0; the
upstream snapshots retain their separate MIT license.

The full corpus receipts retain the exact SDK used for that scan. Later SDK
packaging and build-wiring repairs are qualified separately under
`results/2026-10-04/distribution-9c1905/` and `distribution-6ba237/`: every SDK
runtime class is unchanged, metadata differences are recorded, and fresh tests
reproduce the measured Java bodies. The final qualification also records later
sandbox source/class changes explicitly. It does not claim another full corpus
run or replace any JMH measurements.

The later source-formatting fix is qualified under
`results/2026-10-04/formatting-0f0722/`. Its separately archived generated files
have corrected indentation. The saved `CompareFixtureBytecode.java` helper uses
the unchanged actual benchmark setup/compiler and verifies all four old/new
class-file pairs are byte-identical, including debug metadata. Historical
measured sources, source hashes and JMH samples remain unchanged.

`upstream/provenance.properties` pins both commits, the production file blobs,
and the upstream MIT license. The two checked-in source snapshots are byte exact;
the full upstream repository is fetched only into a disposable QA cache. The
upstream copyright and permission notice are in `upstream/LICENSE.html`.

## Local proof and generated Java

`MathCorpusIntegrationTest` runs the production extractor, optimizer, independent
checker, emitter, and emitted-Java re-extraction. It then compiles original and
generated Java with `javac --release 17` and compares both live outputs using
boundary inputs, deterministic random inputs, and literal expected results.
These finite execution checks supplement the symbolic proof; they are not the
proof. The generated Java has no Regelsuche runtime dependency.

The local fixture explicitly establishes an affine exponent relationship and a
nonnegative exponent through Java's sign-clearing mask. It uses a positive literal
modulus and exact `BigInteger.valueOf` receivers. The original primitive mask and
factory calls remain outside the optimized region. Renaming, a new affine
combination, unknown signed exponents, zero modulus, and a nonlinear exponent are
independent controls. Names and comments never supply numeric assumptions.

`fixtures/local/{small,wide}` contains the actual source and emitted Java for
17-bit and 256-bit positive moduli. `analysis.txt` records a single observed
development-time analysis duration and the proof/cost description. It is not a
latency distribution or a measured runtime improvement. Emit fresh fixtures to a
separate directory with the test property described below; preserve the measured
snapshots and do not hand-edit the generated method.

The emitted plan retains `modPow(ONE, modulus)` to normalize a potentially
negative or unreduced base. It therefore has one general modular exponentiation
and one exponent-one call. It is not claimed to reproduce the upstream patch's
literal one-call implementation.

## Reproduction

Use JDK 25 and an absolute, colon-separated classpath file containing the current
compiled mathematics implementation or its source dependencies, the qualified
Regelsuche SDK, JDT Core/ECJ and platform dependencies, Eclipse Text/JFace Text,
JUnit Platform Console, JGit 7.8.0.202609011348-r and its dependencies, and JMH 1.37
(`jmh-core`, `jmh-generator-annprocess`, `jopt-simple` 5.0.4,
`commons-math3` 3.6.1). This deliberately reuses the repository's product/QA
classpath; it does not invent a second optimizer build or resolve a floating SDK.
The source runner compiles the existing `PinnedGitRepository` fixture directly.
The benchmark Maven module also configures its JMH annotation processor explicitly
for JDK 25.

From the repository root:

```sh
export MATH_QA_JAVA=/absolute/jdk-25/bin/java
export MATH_QA_JAVAC=/absolute/jdk-25/bin/javac
bash qa/math-optimization/run.sh test /absolute/math-qa.classpath /absolute/math-qa-output
bash qa/math-optimization/run.sh corpus /absolute/math-qa.classpath /absolute/math-qa-output
MATH_QA_BC_MIRROR=/absolute/math-qa-output/cache/bc-java.git \
  bash qa/math-optimization/run.sh test /absolute/math-qa.classpath /absolute/math-qa-output
bash qa/math-optimization/run.sh benchmark /absolute/math-qa.classpath /absolute/math-qa-output
```

The corpus command fetches exact objects with JGit, verifies local pinned refs,
and uses `PinnedGitRepository.cloneAt` for checkout and cleanup. It scans every
`.java` file in the original tree, including tests and alternate-JDK source sets.
It then checks the candidate's reference file against the candidate's complete
source environment. Missing dependencies and source-level incompatibilities are
reported; a file with any compiler error cannot produce a trusted candidate.
The common Java 8 parser baseline is conservative for the broad corpus and does
not replace Bouncy Castle's Gradle build or each source set's real target.
Bindings use the runner's JDK 25 boot classes; this is not a `--release 8` API check.

The test's optional real-tree check is skipped unless `MATH_QA_BC_MIRROR` is set.
To emit fresh local fixtures, set
`MATH_QA_WRITE_FIXTURES=/absolute/math-qa-output/regenerated` for the `test` command.
Keep this output separate from the checked-in measured snapshots. The recorded
formatting qualification includes a byte-comparison helper and its exact compiler
classpath in `compiler.properties`; it invokes benchmark setup without running
JMH measurements.

## Report meanings

`files.tsv` records a SHA-256 for each source, compiler errors, resolved
BigInteger-call count, extracted regions, supported regions, candidates, and
diagnostics. These counts have different units:

| Field | Meaning |
| --- | --- |
| BigInteger calls | Resolved method invocations, not proven regions |
| Extracted regions | Local plans emitted by the Java extractor |
| Supported regions | The checker returned a candidate or supported no-improvement result |
| Candidate regions | Verified proposals; their runtime cost is still an estimate |
| Rejected regions | Extracted plans that did not reach either supported result |
| Compiler-error files | Scanned and reported, excluded from candidate qualification |
| Applied changes | Always zero in this harness |
| Measured runtime improvements | Always zero in a corpus analysis report |

Extraction diagnostics also cover unsupported statements that never form a
region. They are counted separately and must not be equated with rejected-plan
count. `implementation.properties` fingerprints every class in the math core
package, including inner/helper classes, the QA classes, and the entire SDK jar.
The corpus command requires a jar and refuses to qualify a run with development
class directories. It checks that the bytecode hashes remain unchanged during
analysis. `sandbox-source.properties` records the sandbox commit, a JGit
HEAD-to-working-tree diff digest, and byte hashes for every changed or untracked
file at analysis start. `commit.txt` pins the upstream input. `summary.properties`
and `diagnostics.tsv` summarize the raw per-file report. Both original parsing
and final emitted-compilation-unit validation receive the identical unit name,
source paths, class paths, and encodings; a packaged two-file test verifies this
contract.

## Runtime measurement and security scope

`MathematicsOptimizationBenchmark` compiles both frozen Java fixtures during
JMH setup and compares their results before measurement. JMH measures ordinary
Java calls with the same 64 rotating deterministic inputs. It runs two forks,
three 500 ms warmups and five 500 ms measurements, returns both outputs to JMH,
and uses the GC profiler. Keep the JSON raw samples and the console log.
Compilation, search, proof and code emission are outside the measured region.
The generated body, including normalization, allocations, and any future guards
or checks, is inside it. The runtime classpath excludes Eclipse and Regelsuche.

These are local numeric-call fixtures. They do not measure Bouncy Castle's public
API, curve construction, cold start, steady-state hashing, or a whole application.
A slower or inconclusive result must be retained. The historical PR's published
performance measurements are not measurements of this cleanup.

The generic sqrt-ratio constructor is prioritized for **parameter review**.
Constructor arguments are not automatically public. The harness assigns every
other region `UNCLASSIFIED_NO_APPLY`. Review data classification, new guards,
memory accesses, variable-time operations and temporary storage before proposing
any separate cryptographic source change. Algebraic equality is not a constant-time
proof; see [RFC 9380, section 10.3](https://datatracker.ietf.org/doc/html/rfc9380#section-10.3).
The real upstream class currently needs field/control-flow and curve-metadata
reasoning beyond the extractor's supported local fragment. Its rejection is a
qualification limit, not a successful automatic rediscovery.
