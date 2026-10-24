# Release qualification of the mathematics cleanup

The Java-25 host and the target Java contract of edited projects are separate.
These tests use the existing Maven/JUnit modules; finite execution samples
supplement the independent proof gates.

## Target and profile matrix

MathTargetProfileTest groups the 24 numeric-kind/safety-profile combinations
into 18 target-release tests (Java 8 through 25). It invokes real analysis,
proof and editing, checks exact undo, then batch-compiles and executes original
and generated Java with an isolated runtime loader. A separate regression
ensures the test parser actually uses the requested language level.

There are 378 accepted cells and 54 explicit FLOAT/DOUBLE rejections before Java
17. Accepted cells cover both primitive integral widths, narrow-type promotion
and narrowing, bounded factory-created BigInteger receivers, and floating-point
canonical-value observations. Boundary controls distinguish wraparound,
original overflow, narrowing, signed zero, subnormals, non-finite values and
finite rounding mismatch. This checks target syntax/API compatibility on the
Java-25 host, not eighteen separately installed JVM runtimes. It complements
existing operation-specific, receiver/subclass, freshness and corruption tests;
it does not exhaust every expression or every possible input.

## Independent fixed controls

The [frozen selection](../../sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/heldout/SELECTION.md)
and pre-execution source hashes are retained next to the fixtures.
MathHeldoutQualificationTest keeps all nine cases, including negative and
unchanged results, and checks 4,387 input tuples against separate polynomial or
bitwise oracles. Integer algebra is checked modulo the Java word width;
the floating-point counterexample refutes applying that algebra to rounded
double evaluation. Generated code must have production evidence, exact undo
and a stable second pass.

Current outcomes are repeated-expression sharing for the original/renamed
int stencil and original long full-adder; unchanged reduced controls; unchanged
BigInteger Bernstein cases; and an unchanged floating-point negative control.
The optimizer did not derive the hand-reduced polynomial forms. These are fresh
synthetic controls selected without reading optimizer rules, not new upstream
corpus transfer, cryptographic qualification or performance measurements.

## Guarded and checked performance

MathSafetyProfileIntegrationTest exports actual original and generated long
addition/subtraction bodies under READABILITY. Both profiles retain their checks
and are rejected for the lower-estimated-runtime goal when the complete policy
cost is counted. The benchmark includes those checks and all allocations.

The existing JMH benchmark supports explicit safe and overflow datasets, each
with 64 deterministic varying inputs. Setup checks values against the independent
wraparound identity: adding and then subtracting baseSeed returns seed.
For checked overflow it requires the actual MATH_OVERFLOW exception instead.
Missing/wrong exceptions, incorrect values and unknown fixture metadata fail
before measurement. Benchmark methods return the observed array or caught
exception without allocating a separate outcome wrapper.

Generate immutable fixtures through the ordinary mathematics test module:

    xvfb-run --auto-servernum ./mvnw -pl sandbox_math_cleanup_test -am \
      -Dtest=MathSafetyProfileIntegrationTest -DfailIfNoTests=false \
      -Dsurefire.failIfNoSpecifiedTests=false \
      -Dmath.qa.safetyFixtureDirectory=/absolute/fresh-profile-fixtures verify

Verify the benchmark harness through Maven, then reuse the existing QA adapter:

    ./mvnw -Pbenchmark -pl sandbox-benchmarks -am test

    MATH_QA_BENCHMARK_FIXTURES=/absolute/fresh-profile-fixtures \
    MATH_QA_BENCHMARK_CASES=guarded-long,checked-long \
    MATH_QA_BENCHMARK_SCENARIOS=safe,overflow \
      bash qa/math-optimization/run.sh benchmark /absolute/qa.classpath /absolute/fresh-jmh

Use the JDK-25 environment and classpath setup described in [README](README.md).
Both source/profile pairs and both datasets are measured against original Java:
eight combinations, two forks, three warmups, five measurement iterations and
GC profiling, yielding 80 primary samples. Search/proof/emission time is recorded
separately. The measured classpath excludes optimizer classes, SDK and Eclipse
dependencies. Source, metadata, compiled-class and input hashes are checked
before and after measurement.

Checked overflow deliberately changes behavior; exception cost is not an
equivalent-computation speedup. These cancellation fixtures can already be
optimized by the JVM. Publish negative outcomes and raw samples, and do not
generalize them to Bouncy Castle, arbitrary guarded regions or cryptographic
performance. Existing small/wide BigInteger fixtures retain their default
dataset and validation.
