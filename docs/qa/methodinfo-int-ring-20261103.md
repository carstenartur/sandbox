# MethodInfo int-ring diagnostic, 2026-11-03

## Finding and scope

The production lowering introduced a long count, a negative-count clamp and a long
product for an int-only constant reduction whose counter is dead. For exclusive
unit-stride int headers, compare the captured signed endpoints before subtraction;
then the count modulo 2^32 suffices for every int accumulator update. This preserves
positive mathematical distances up to 2^32-1 without widening the emitted count.
Live counters, long accumulators, inclusive/once headers and general strides keep
their previous templates. No class name or handler-count filter was introduced.
The SDK, strategy admission, independent proof/replay and advisory estimates are
unchanged. Structural reapplication barriers recognize the additional int form.

This is a verified output simplification. These measurements do not establish a
speedup or identify a particular JIT optimization. The historical native result
(current primitive output 402.219 vs original 332.144 ns/op at 65,535 handlers)
remains historical evidence. Its regression was not reproduced by this diagnostic.
Neither overlapping intervals nor a lower point estimate establishes equivalence
or a speedup.

Production change: 1bb66e41b, based on main ef946b6fa plus documentation commit 90fccfcc7.
Pinned JDT source: eclipse-jdt/eclipse.jdt.core commit
6725c16c24d94c83302346dc384bb915a0f2fe1a. Source fetched by that exact commit was
compared with the R4_41 source used to generate the fixture and matched exactly.

## Executable checks

Baseline focused Maven/JUnit suite: 41 occurrences, all passed. New regression
failed on the baseline because actual emitted Java declared a long count. The
new regression then passed with int-domain empty/reversed/extreme ranges and
counts beyond 2^31, including 2^32-1; generated source also remains unchanged on
reapplication. Existing accumulator-wrap, multiple-output, live-counter, long,
nonunit-stride and forged-proof checks remain in the focused suites.

Final focused Maven/JUnit suite: 42 occurrences, zero failures/errors/skips.
Command, from sandbox-performance:

```
/workspace/scratch/330c17e56514/toolchains/mvn-runtime -B --no-transfer-progress -f qa/math-optimization/pom.xml -Dmaven.repo.local=/workspace/scratch/330c17e56514/m2-integration -Dtest=JavaRuntimeConstantSumTest,JavaRuntimePrimitiveGuardTest,JavaRuntimeIterationReviewTest,JavaRuntimeExplanationTest test
```

Complete standalone suite/native CI belongs to the combined branch qualification;
it is not claimed here. Maven output contains existing javac unchecked-operation
notes; no test failure was suppressed.

Temporary Java diagnostic checked 45 selected/reference complete-reader pairs
(five variants x nine inputs), including lazy first access and populated-cache
identity. Eight synthetic class fixtures were independently loaded by the JVM
and invoked, returning the expected value. The real ClassFileReader fixture bytes
were compared with the exact retained qualified compiler JAR entry and matched.
The first GREEN attempt exposed a test-helper misuse:changed(source) requires an
edit, so reapplication was corrected to assert that analyze(generated).changed()
is false. The final passing test log and original RED log are retained.

## Complete-method experiment

Original, current and new files contain the complete upstream MethodInfo class.
Generate.java obtains both generated forms through actual production analysis,
source checking and edit application for the selected original loop, with target 17
and the unchanged 1,000,000-work/20,000-state request. Current was generated from
the compiled baseline before the production fix; new was generated after it.
This is a selected-source headless Apply diagnostic, not an installed-product or
complete upstream native-build qualification.

All three classes are compiled using the same javac 25.0.5+8, --release 17,
-proc:none and -g options. Each trial loads the chosen complete MethodInfo class
from its own directory, the same analysis-only bridge, and all remaining JDT
classes from the exact historical qualified original compiler.batch JAR. Code
sources and loader parents are checked. OriginalControl is a byte-identical copy
of the recompiled original class, loaded from a distinct directory. Native is the
unchanged MethodInfo entry extracted from the original qualified JAR. It exposes
compiler differences separately rather than attributing them to the emitter.

Timed operation: fresh ClassFileReader(bytes,name,false), fresh MethodInfo objects,
and first getArgumentNames for every method. Each trial performs 64 interleaved
reference/selected equality checks outside timing; every fork has the same two-loader
preparation pattern. Reused input bytes and internal name caches are warm. This
does not model cold process startup, disk IO, an overall compiler or an IDE.

JMH 1.37, two forks, three 500ms warmup iterations and five 500ms measured iterations,
one thread, average time in ns/op; JDK flags -Xms1g -Xmx1g -XX:+UseG1GC -Xverify:all.
The GC profiler was enabled. Its values are retained, but setup/compiler/classloader
work and profiling boundaries do not establish an isolated allocation change.

Each cohort has 15 cases/30 forks: 0 and 65,535 legal synthetic handlers plus the
real ClassFileReader control, across original/originalControl/current/new/native.
The synthetic maximum is a legal extremal class with a three-byte method body,
not representative evidence about real-world handler frequency.

Cohort1 began while another agent's already-started Maven compile was finishing
(the competing Maven process finished at 21:56:21 UTC). It is preserved as
cohort1-known-initial-shared-load. Before inspecting comparative scores, one
identical complete clean repeat was declared and authorized to resolve that
specific baseline confound. All other Java/Maven work was held during cohort2.
No further timing repeats or favorable-score selection occurred.

## Quiet cohort results

Values are mean +/- JMH 99.9% confidence error, ns/op. Every original comparison's
interval overlaps, including all controls. No pair supports a resolved timing
claim; these short local measurements also have visible A/A point-estimate drift.

| Input | Original | A/A control | Current primitive | New int ring | Native original |
|---|---:|---:|---:|---:|---:|
|0 handlers|298.781 +/- 44.881|295.210 +/- 26.693|292.983 +/- 19.158|293.333 +/- 7.376|304.651 +/- 36.339|
|65,535 handlers|317.225 +/- 37.447|309.650 +/- 20.839|312.253 +/- 29.528|301.973 +/- 14.564|319.145 +/- 40.140|
|Real ClassFileReader|17725.907 +/- 2389.678|16240.112 +/- 670.843|16430.402 +/- 835.732|17868.213 +/- 4593.813|16827.901 +/- 1077.080|

The native/recompiled originals also overlap, so this diagnostic does not resolve
compiler effects. Cohort1's original 0-handler mean was 539.816 +/- 963.346 ns/op;
that unstable baseline remains visible in its raw output. Cohort1 and cohort2
comparisons are both retained in full TSV and JSON. JIT loop collapse and whether
this simplification removes the historical regression remain unconfirmed.

## Reproduction and retained evidence

prepare-classes.sh compiles all complete sources with identical flags and copies
the original A/A class. DiagnosticJmh.java and the generated JMH classes are kept;
run-diagnostic-jmh.sh contains the exact measurement command. Its output directory
was renamed after each cohort; there was no parameter or source change between
runs. Generate.java recreates production output against the matching repository
revision and production classpath. Paths identify this exact diagnostic workspace;
adapt only the root paths when replaying elsewhere.

The archive contains full sources/classes, original qualified JAR, bridge,
fixtures, scripts, benchmark sources/classes, both raw cohorts, comparison TSVs,
source/class hashes, bytecode listings and test logs. Static bytecode listings
support inspection and identity only; they are not timing evidence.

The original retained archive estimated this overlap as approximately 15 seconds.
That estimate is superseded by the recorded timestamps: the launcher output is
21:56:15.613 UTC, the first fork receipt 21:56:16.508 UTC, and the competing Maven
completion 21:56:21 UTC. Initial overlap was approximately four to six seconds;
the raw archive remains unchanged and its original digest is retained.
