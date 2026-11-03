# Mathematics: bytecode and upstream qualification, 2026-11-03

## Current assessment

The qualified SWT, Bouncy Castle and JDT Core patches preserve the complete
agreed test inventories. JDT UI is a complete, successful zero-change result.
The native Core comparison has now also completed: all 290,650 reported test
executions pass both before and after ordinary Apply, with no skipped cases.

| Target | Actual Apply result | Completed before/after evidence | Usefulness assessment |
| --- | --- | --- | --- |
| Bouncy Castle bcprov | 9 digest replacements in 5 files | 1,355 standard test executions per variant, plus 8 separate official digest self-test classes against each actual MR-JAR; all pass | Smaller digest-helper bytecode; runtime improvement not measured for this patch |
| SWT Linux/GTK | 6 whole-file replacements plus 1 selected-region replacement in 5 files | 5,012 cases per variant: 4,550 pass, 462 identical skips, no failures/errors; all 4 changed-method gates pass | Concrete arithmetic simplifications; native behavior preserved within this platform scope |
| JDT UI/manipulation | 0 replacements in 2,661 source units | 9,577 cases per variant: 9,423 pass, 154 identical skips, no failures/errors | No accepted cleanup opportunity in this pinned scan |
| JDT Core | 5 replacements in 4 files | Both official 20-module builds pass; 290,650 passed test executions per variant, no skips/failures/errors | Several small simplifications, but 13 new warnings and a measured negative MethodInfo result |

The targeted MethodInfo experiment finds **2.16–2.49 times the complete decode
time and 2.82–3.05 times the allocated bytes** for four valid synthetic inputs
that activate the generated BigInteger path. It provides no demonstrated runtime
benefit below that threshold or for its one real-class control. This particular
replacement should not be recommended as a useful optimization in its current
form. The separate SWU opt-in benchmark finds lower allocation in six cases,
but no established time improvement; it measures a different patch from the
nine digest changes.

## What is being qualified

The starting Sandbox revision is `95bfbddc067d0033a86fb2af97baf6b5cd81e297`.
The numerical optimizer and ordinary Apply implementation are unchanged by this
qualification work. The additions close specific SWT execution gaps, connect the
existing source gate to official upstream build/test commands, and qualify real
JDT production projects with the same native analysis and Apply application.

| Input | Exact identity |
| --- | --- |
| Bouncy Castle | `r1rv85v2`, `854bd5c4286e1530132418906bf6175da7d9d83c` |
| SWT | `221d1de66c33292400c91cbd17e3121f72b04ea3`, P2 `3.136.0.v20261029-1803` |
| JDT Core | `R4_41`, `6725c16c24d94c83302346dc384bb915a0f2fe1a` |
| JDT UI | `R4_41`, `54bd63e6453a7eb5db0bfc1d60f17e9568095619` |
| JDT release releng | `R4_41`, `10be605e8924c917d5f0013930278a2b1cb756f5` |
| Local JDK | Temurin `25.0.5+8` |

The local JDT experiment used the Sandbox installation from Linux distribution artifact
`13197211842` in [run 41558508599](https://github.com/carstenartur/sandbox/actions/runs/41558508599).
Its downloaded archive SHA-256 was verified as
`d9e561d83ffe0890dabc811b298bdd6da967936f2204d1d0371da08b25a9cb90`.
This installation contains the unmodified mathematics bundle built from the
starting Sandbox revision. The native CI comparisons build a separate product
from their own workflow checkout and record that product's identities in their
receipts. Both paths use the real native application `sandbox_math_cleanup.analysis`,
with real imported Java/PDE projects.

## Bytecode is a structural measurement

Complete original and ordinary Apply output classes were compiled with the same
`javac`, dependency archive and options. BC used `--release 17 -g:none`; SWT used
`--release 21 -g:none`. The retained
[receipt](../../qa/math-optimization/results/2026-11-03-bytecode/summary.json)
records compiler, runtime, dependency, source and class hashes. The
[method metrics](../../qa/math-optimization/results/2026-11-03-bytecode/classfile-metrics.tsv)
were obtained with `javap -c -p` and independently checked using the JDK Class-File
API in the small retained
[inspection program](../../qa/math-optimization/results/2026-11-03-bytecode/InstructionMetrics.java).

| Method | Static JVM instructions | Code bytes | Additional observation |
| --- | ---: | ---: | --- |
| `SHA1Digest.f` | 10 → 8 | 10 → 8 | `maxStack` 3 → 4 |
| `SHA1Digest.g` | 12 → 10 | 12 → 10 | No calls, branches or allocations |
| `SHA256Digest.Ch` | 10 → 8 | 10 → 8 | Choice bit identity |
| `MD4Digest.G` | 12 → 10 | 12 → 10 | Majority bit identity |
| `LongDigest.Ch` | 10 → 8 | 13 → 10 | `maxStack` 6 → 8 |
| `LongDigest.Maj` | 12 → 10 | 14 → 12 | No calls, branches or allocations |
| SWT `StyledTextRenderer.addMerge` | 334 → 330 | 562 → 556 | Two subtractions and two loads removed |
| SWT `getTextLayout(int,int,int,int)` | 1,352 → 1,339 | 2,883 → 2,860 | Branch instructions 140 → 138 |

These are counts of static instructions in complete method bodies, not counts
of executed instructions. `maxStack` is the JVM operand-stack requirement, not
a native register/stack cost. Explicit allocation opcodes do not count objects
allocated by callees. The SWT call sequences are unchanged.

Raw class hashes prove identity, not quality. In this inspection, removing a
`LongDigest` constant renumbered constant-pool references in otherwise unchanged
methods. Resolve references symbolically and keep debug metadata separate from
executable code before interpreting a diff. Exactly nine digest methods differ
after this normalization.

The same comparison was subsequently repeated on the actual BC upstream Gradle
outputs (`--release 8`, classfile major 52), without recompiling the product for
inspection. Of 108 executable method bodies, exactly the same nine digest helpers
changed. All nine lose two JVM instructions; `LongDigest.Ch` loses three encoded
bytes and the other eight lose two. Branch, call and explicit allocation counts
remain zero. The upstream compilation therefore confirms the structural result
under its actual build settings, independently of the earlier inspection build.

The SWT bytecode inspection initially used a retained, hash-verified ordinary
Apply export. The fresh live-source gate subsequently regenerated the same
`StyledTextRenderer` SHA-256,
`0ecb8a4f21b5db5264af40cac0abc276543bef97e5bd7533981a30c9a7123989`.

HotSpot compiles using runtime profiles, can inline calls and eliminate objects,
and can produce different machine code for different calling contexts. Thus a
shorter bytecode method is useful evidence of structural simplification, but
does not establish lower latency or higher throughput. Use a representative JMH
measurement of the complete call path, including guards/fallbacks and consumed
results, when performance is the acceptance criterion. JIT disassembly and
`perfasm` help explain a measured result.

Primary references: [javap](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html),
[HotSpot profiling and escape analysis](https://docs.oracle.com/en/java/javase/25/vm/java-hotspot-virtual-machine-performance-enhancements.html),
[JMH](https://github.com/openjdk/jmh), and its
[profiler example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_35_Profilers.java).

### Fresh retained full-call benchmark

The existing benchmark job on this PR also completed a fresh, source-bound JMH
comparison of `hashToCurve(message).getEncoded(false)`. This is a **different
patch**: three mathematical opt-in regions in the complete
`SimplifiedShallueVanDeWoestijneMapToCurve` class. The generated class retains all
guards and fallbacks; all other BC classes, including the digests, come unchanged
from the pinned binary JAR. It therefore does not measure the nine digest edits,
SWT, or the JDT `MethodInfo` transformation.

The [receipt](../../qa/math-optimization/results/2026-11-03-bc-benchmark/receipt.json)
binds the actual benchmark source, source variants, profile, JDK and raw artifacts.
There are eight curve/message-size pairs: P256, P384, P521 and BLS12-381 G1,
each with 32- and 256-byte messages. Each variant/case uses two forks, three
500 ms warmups and five 500 ms measurements per fork, with the GC profiler.
The complete encoded outputs are checked for equality for 64 fixed messages
per pair before timing, and the measured calls return their result to JMH.

All eight time comparisons have overlapping 99.9% JMH confidence intervals and
remain `INCONCLUSIVE` under the existing conservative report rule. Generated
mean times are 0.5–3.6% higher, which does not establish a slowdown, speedup or
equivalent runtime. Six P256/P384/P521 pairs show **4.03–5.19% fewer allocated
bytes per operation**, with non-overlapping intervals. Both BLS G1 allocation
comparisons remain inconclusive. Allocated bytes are not peak heap usage.
These are measurements for those inputs on that runtime, not a universal claim;
forced fallback scenarios were not measured.

The larger mathematical scan exported 153 regions in 65 changed source files
and reported ten parse-error files. That scan is not a full-library compile/test
qualification. Only the one selected three-region SWU class is an override in
this benchmark. The separate generic `MethodInfoBenchmark` in `jmh-result.json`
measures Sandbox's AST API wrapper, not JDT's `decodeCodeAttribute` method.

## Source comments already available

`cleanup.mathematics.explanations` supports `NONE`, `NONTRIVIAL` and `ALL`.
The default `NONTRIVIAL` deliberately omits small, uncomplicated bit identities;
`ALL` includes explanations for those accepted changes too. Full explanations
remain in diagnostics/reports independently of the source-comment mode.

This mechanism covers the scalar value-plan path. The actual `MethodInfo`
runtime-iteration replacement has no generated explanation comment even though
the exercised profile selects `NONTRIVIAL`: its separate emitter does not call
`MathExplanation`. The observed output therefore exposes an explanation gap for
that more complex transformation; the option is not a guarantee that every
transformation family emits a source comment.

`MathExplanation` records the actual verified numerical contract, premises,
original/replacement value expressions and independent proof methods. Its work
estimate explicitly says `not a timing measurement`. This is suitable information
to retain in the source. For example, a review of the renderer simplification
can explain the Java integral identity `(j - a) - (b - a) = j - b`, including
wraparound, without claiming a measured speedup.

Suitable human-facing comment examples are shown below; they are suggestions,
not additional source edits in the qualified patches:

```java
// Equivalent Java int arithmetic, including wraparound:
// (j - modifyStart) - (modifyEnd - modifyStart) == j - modifyEnd.
```

```java
// x is non-negative after the bounds check, so x % 4 == (x & 3).
```

The second premise is essential: Java remainder and a bit mask are not equal for
all negative integers. In the pinned ImageData.getPixel source, the existing
bounds check rejects negative x before the changed calculation. For a digest
choice helper, a concise description such as `Select y where x has set bits,
otherwise z` explains the bit operation's meaning. Keep proof premises and the
actual integer width when they are necessary; avoid adding a performance claim
from an operation-count estimate.

Compilation outcomes, test inventories and runtime measurements belong in
separate evidence tied to the exact source/compiler/runtime identities. Ordinary
Apply does not run those qualifications. Embedding a whole-file hash inside that
same file would also create a self-reference. See the existing
[explanation design](verified-mathematics-explanations.md).

## SWT: compilation and changed-method coverage

The fresh existing `SwtCurrentSourceRefinementTest` passed: **1 test, zero failures,
errors or skips**, 68.08 seconds. It verifies the live Git repository/ref/commit,
matches six whole source files to the hashed P2 archive, performs ordinary Apply,
compiles full classes, executes its behavior checks and checks exact Undo.
The result is six whole-file replacements plus one explicitly selected
`ImageData.getPixel` replacement: seven edits in five files.

The real SWT reactor compiled successfully before and after this generated patch:
**all 22 modules**, including test sources, using the official `clean package`
path with `-DskipTests -Dtycho.baseline.replace=none`. The generated build reused
the resolved dependency cache offline. These are compilation results, not
executed upstream test results.

`SwtChangedMethodsTest` adds direct execution of the previously uncovered changed
methods. Original and generated complete classes are compiled separately from the
hash-pinned archives and receive identical inputs. JPEG `extractData` and
`storeData` each exercise 300 cases, including partial writes and aliases; TIFF
`getColorMap` exercises 112 cases including byte order and truncated reads.
The three headless tests passed with zero failures/errors/skips. Deliberately
wrong generated calculations produced three assertion failures, demonstrating
that the tested paths can detect wrong results.

The fourth test uses a real `StyledText`, renderer and scrolling callback in
separate native JVMs. It retains real widget side effects and checks negative,
zero and positive scroll offsets. It could not execute locally: the container
rejects Xvfb/D-Bus Unix listening sockets, and unchanged SWT cannot create a
Display. The test is retained as a required native check when this gate is
explicitly enabled; it is not replaced by a mock or silently skipped.

The existing Benchmark workflow now has a dedicated Linux/GTK job. It first
verifies the live sources, then executes all four changed-method tests under
Xvfb, then runs the entire official SWT reactor before and after the exact patch.
The thin `run-swt-before-after.sh` process adapter preserves command exit codes
and reports. Maven/JUnit compares exact test identities, multiplicities and states
with no allowed renames/additions/removals.
The generated build runs offline against the baseline's resolved cache, so a
moving P2 composite cannot supply different dependencies for the second build.

An additional JUnit gate requires actual passing tests in both the main SWT test
module and the separate Linux GTK test module. Equal inventories containing only
skips, or equal inventories that both omit a module, are insufficient. Regression
fixtures first reproduced both false-green cases and then verified their rejection.

### Completed Linux/GTK upstream result

[Job 125691314994](https://github.com/carstenartur/sandbox/actions/runs/41567162274/job/125691314994)
completed successfully on the exact PR tree. Both official 22-module builds
returned zero, and all test identities, multiplicities and states matched:

| Executed reactor scope | Tests before/after | Passed before/after | Skipped before/after | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| Whole reactor, 175 JUnit XML reports | 5,012 / 5,012 | 4,550 / 4,550 | 462 / 462 | 0 / 0 |
| Main SWT test module | 4,680 / 4,680 | 4,444 / 4,444 | 236 / 236 | 0 / 0 |
| GTK test module | 53 / 53 | 43 / 43 | 10 / 10 | 0 / 0 |

The whole reactor additionally executes SVG tests and platform-independent tests
from the two Windows fragment modules. These do not establish Windows GUI or
native behavior. The 462 skips were already present before the patch and remain
the exact same test states afterwards.

The separate changed-method gate also passed all **four tests with no skips** in
CI, including the real renderer and all four deliberate wrong-result mutations.
The live source gate passed independently. The retained
[receipt](../../qa/math-optimization/results/2026-11-03-swt-upstream/receipt.json)
binds the test tree, source commit, complete evidence archive and patch hashes.
The archive contains the exact patch; its hash is identical to the separately
regenerated local Apply output. The full gate subsequently passed again on head
`55a98f69cd59fbcdb67813091ea2af3a3cc92374` in
[job 125709486876](https://github.com/carstenartur/sandbox/actions/runs/41572836149/job/125709486876),
with the same patch and again 5,012 tests per phase, with exact before/after
inventory equality. Both archives are retained.

## Oomph scope

The repository's `sandbox_oomph/sandboxproject.setup` clones Sandbox; it does not
clone SWT. `jdt-migration-qa.setup` provisions a pinned JDT Core/UI/Debug workspace
at **R4_40** for the existing JUnit migration qualification. That is a different
target from this new R4_41 mathematics experiment.

SWT's own pinned upstream tree provides
`bundles/org.eclipse.swt.tools/Oomph/platformSwt.setup` and
`PlatformSWTConfiguration.setup`. They provision SWT and its test projects. Their
moving master/latest references need explicit pinning for a reproducible
qualification. Oomph can provision an IDE/workspace; compilation and running the
same official tests before/after remain separate checks. Linux GTK results do not
qualify Windows, Cocoa, GTK4 or Wayland behavior.

## Bouncy Castle: official upstream qualification

The full agreed standard Gradle scope is complete before and after the patch:
**1,355 successful test executions and 198 XML reports per variant, with zero
failures, errors or skips**. The unchanged shared Java inventory comparator
requires exact test identities, multiplicities and states separately for each
task; there are no additions or omissions.

| Official task | Before/after executions | XML reports per phase | Result |
| --- | ---: | ---: | --- |
| `:core:test` | 662 / 662 | 121 | PASS |
| `:prov:test` | 682 / 682 | 74 | PASS |
| `:prov:test25` | 11 / 11 | 3 | PASS |

The baseline completed in one invocation. The after invocation completed Core and
Java-25 KDF but was interrupted during Provider, without a known final process
exit. Those completed task reports were retained. A separate complete
`:prov:test` continuation, offline with the same sources, cache and JDK, returned
zero; product compilation, JARs and KDF remained up to date, and Core tests were
not repeated. The final
[receipt](../../qa/math-optimization/results/2026-11-03-bc-upstream/receipt.json)
explicitly distinguishes the interrupted attempt from the successful aggregate
qualification. All detailed files referenced by it are in the retained raw
archive, available as the exact 2,200,762-byte
[download](../../qa/math-optimization/results/2026-11-03-bc-upstream/bc-strict-digest-upstream-evidence.tar.gz?raw=true), SHA-256
`1b4d5d68d04734c43fdc2b112cf36bab21bf319717bb66f80ec7fbfc26cdb3f8`.
This is the local upstream Gradle qualification evidence, including the retained
reports, commands and continuation receipts. The workflow's separate
`bc-math-qualification` artifact records its corpus and opt-in benchmark scope.

The patch consists of nine ordinary digest replacements in five files. Source,
test-data, JDK and resolved dependency identities are retained; the two trees use
physically separate caches with the same 82 dependency JAR hashes. No test
filters, production build changes or alterations to upstream test sources were
introduced. Scope is the changed bcprov product, not the separate PKIX, TLS, PGP,
MLS or mail products, and not all historical mathematical candidate rewrites.

The standard upstream PQC mode includes vector zero and samples approximately
every ninth later vector using a time-based random offset. Equal test identities
therefore do not establish identical sampled PQC vectors. The separate exhaustive
`testFull` mode was not selected. The changed classical digest suites execute
independently of that sampler, including fixed expected digest values. Actual
Core JaCoCo reports show 100% instruction coverage in all nine changed digest
helpers both before and after; execution coverage does not prove correctness
over every possible input.

The finished Provider JAR also contains effective Java-9 overrides for
`SHA256Digest` and `LongDigest`. Source-file names alone did not reveal these
implicitly compiled classes. All 108 executable bodies in the effective Java-25
view were compared: only the intended nine helper bodies change, again losing
two instructions each. Replay of the captured standard test classpaths shows
that those tasks resolve ordinary build classes or bccore before the Provider
JAR. This is a recorded classpath replay, not live instrumentation of the
original Gradle JVMs.

An additional product-JAR check therefore runs eight unchanged official digest
self-test classes against each actual bcprov JAR in isolation. All **8/8 pass
before and after**, with both processes returning zero. The actual loaded digest
classes, including the version-9 implementations, match the effective JAR bytes;
all original expected values and test logic are retained. These are eight
official self-test class invocations, not a count of nested assertions, and are
reported separately from the 1,355 standard JUnit executions. The Provider JAR
hashes were checked again after the resumed standard task completed.

## JDT: actual native Apply and build findings

Native JDT Apply completed for all **4,287 Java units** in the four production
bundles, using the strict numerical profile and the ordinary proof/Apply gates:

| Bundle | Java units | Accepted replacements |
| --- | ---: | ---: |
| `org.eclipse.jdt.core.compiler.batch` | 553 | 4 in 3 files |
| `org.eclipse.jdt.core` | 1,073 | 1 in 1 file |
| `org.eclipse.jdt.core.manipulation` | 945 | 0 |
| `org.eclipse.jdt.ui` | 1,716 | 0 |

Every recorded original was checked
against the untouched checkout and every replacement against the written file,
with zero mismatches. The compact
[local receipt](../../qa/math-optimization/results/2026-11-03-jdt-local/receipt.json)
records product/source identities, patches, warnings and the bounded build scope.
The patch contains positional arithmetic in
`AbstractCommentParser`, two hexadecimal-character calculations in `ScannerHelper`,
constant addition in `CompletionEngine`, and a guarded finite-sum replacement in
`MethodInfo`. The latter substantially increases source size and introduces a
BigInteger path for at least 4,096 iterations. The targeted measurement below
finds a runtime and allocation regression for its measured large-table inputs.

The transformed Core production bundles compile successfully. Their emitted
source adds **13 compiler warnings**: MethodInfo has three unnecessary casts and
eight missing NON-NLS markers; ScannerHelper has two unnecessary casts. These are
real quality regressions, despite successful compilation. The generated output
is preserved exactly as Apply wrote it.

The already-built, qualified before/after JARs also show why accepted mathematical
changes need a separate usefulness decision. The complete method metrics are:

| Method | JVM instructions | Code bytes | Branch sites | Call sites | Explicit allocation sites |
| --- | ---: | ---: | ---: | ---: | ---: |
| `MethodInfo.decodeCodeAttribute` | 81 → 189 | 143 → 404 | 6 → 16 | 9 → 38 | 0 → 8 |
| `ScannerHelper.digit` | 69 → 65 | 147 → 141 | 12 → 12 | 1 → 1 | 0 → 0 |
| `CompletionEngine.inlineTagCompletion` | 55 → 51 | 78 → 74 | 0 → 0 | 2 → 2 | 1 → 1 |

All six inspected class snapshots match the actual compiler outputs and packaged
JAR entries. No JDT product was recompiled for this inspection. The
[metric receipt](../../qa/math-optimization/results/2026-11-03-jdt-bytecode/summary.json)
records the input and analysis hashes. Raw classes, the reader and commands are
retained in the evidence archive. These are static whole-method counts across
all branches. In particular, the MethodInfo result includes the threshold guard,
closed-form path and original-loop fallback. It is neither a measured slowdown
nor a speedup by itself; allocations inside `BigInteger` callees are not included in the
eight explicit allocation sites.

The threshold counts exception-table entries, not method bytecode length. The
concrete `u2At` implementation returns 0 through 65,535, and each skipped entry
occupies eight bytes. A representative benchmark must create a fresh `MethodInfo`
and actually reach `getArgumentNames()` without already populated parameter
names; `MethodParameters` and cached results can bypass the changed decoder.
Include ordinary small tables, the 4,095/4,096 boundary and large valid tables,
consume decoded results, and retain allocations as well as time measurements.

A possible future emitter improvement for this exact loop is the direct Java
integer sum `if (exceptionTableLength > 0) readOffset += 8 * exceptionTableLength;`.
Repeated addition and multiplication have the same modulo-2^32 result; the
positive-count guard also preserves the zero-iteration behavior for negative
counts. This is a reviewed target form, **not** the output produced or tested in
this experiment. The generic guard avoids assuming a range merely from the
`u2At` name: that method is overridable. The current generated BigInteger output
and its warnings remain unchanged in the qualified patch.

### Measured MethodInfo usefulness

The targeted JMH experiment uses the exact already-qualified before/after
compiler.batch JARs. Each operation constructs a new
`ClassFileReader(bytes, name, false)`, calls the first `getArgumentNames()` for
every method and returns all decoded names to JMH for consumption. This includes
the generated guard, original-loop fallback or BigInteger path, plus the common
reader/name-array overhead. It does not time an isolated primitive loop.

Ten JUnit gates pass with zero failures, errors or skips. Complete synthetic
classfiles are defined by the JVM and their `target(7)` method is invoked under
`-Xverify:all`. They contain a valid StackMapTable and repeated legal catch-all
entries, followed by the LocalVariableTable holding the expected name `value`.
Both variants return that name from beyond the entire table. Separate
controls demonstrate that MethodParameters and full initialization populate the
cache; neither bypass is used in the timed cases. Trial setup also requires null
name caches and binds the actually loaded MethodInfo/ClassFileReader to the
qualified JARs through hashes, CodeSource and an isolated class loader.

JMH 1.37 runs on Temurin 25.0.5+8 with G1, a 1 GiB heap, one thread, two forks
per variant/case, three 500 ms warmups and five 500 ms measurements per fork.
All 18 cases and 36 forks complete with exit zero. The full call results are:

| Exception-table entries | Before ns/op | After ns/op | After/before time | Before B/op | After B/op | Time-interval result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 0 | 325.380 | 319.313 | 0.981 | 672 | 672 | Overlap |
| 24 | 343.868 | 341.517 | 0.993 | 672 | 672 | Overlap |
| 79 | 344.018 | 367.618 | 1.069 | 672 | 672 | Overlap |
| 4,095 | 374.987 | 325.180 | 0.867 | 680 | 680 | Overlap |
| 4,096 | 324.084 | 806.532 | 2.489 | 680 | 1,920 | Separate; after slower |
| 4,097 | 346.891 | 767.215 | 2.212 | 680 | 2,072 | Separate; after slower |
| 8,192 | 343.831 | 743.243 | 2.162 | 680 | 1,920 | Separate; after slower |
| 65,535 | 343.127 | 753.780 | 2.197 | 680 | 2,072 | Separate; after slower |
| Real ClassFileReader.class, 67 methods | 18,782.461 | 18,342.635 | 0.977 | 33,744 | 33,744 | Overlap |

Intervals are JMH's 99.9% confidence intervals. The four BigInteger-path cases
also have separate allocation intervals; their allocated bytes are
2.82–3.05 times as large. The other five allocation comparisons overlap. Overlapping
intervals establish neither a speedup nor equivalent time. These are allocated
bytes per complete operation, not peak memory or counts of allocations.

Synthetic method code length remains three bytes throughout; 65,535 handlers
occupy 524,280 separate table bytes. This is a valid extreme case, not a typical
classfile distribution. The single real control is unchanged ClassFileReader
bytecode with 67 methods and a maximum observed handler count of 24. It does not
establish corpus representativeness. Each operation reuses the same immutable
input bytes; file I/O, class loading and fixture verification are outside timing.
This is a steady-state result for one runtime/container configuration. No JIT
assembly inspection was performed, so the nearly flat original timings do not
prove a particular JIT optimization. The ratios do not predict complete compiler
or application throughput.

Fresh reader/method objects do not mean a cold JVM or a cold character cache:
the internal CharDeduplication thread cache is warm after the warmup. The zero
case skips the outer loop block; 24, 79 and 4,095 use the original-loop fallback
behind the size guard. The four large cases execute the BigInteger path and a
successful inner range check. Its second fallback is not exercised: that inner
check cannot fail for the concrete reader's unsigned 16-bit counts. Independent
read-only review confirms the consumed-result path and all 54 retained product
provenance records, without additional tests or measurements.

The result is sufficient to reject a usefulness claim for this current generated
form. It does not invalidate the numerical-equivalence proof, imply that all
mathematics replacements are slower, or measure the proposed primitive emitter
improvement above. The [compact receipt](../../qa/math-optimization/results/2026-11-03-jdt-method-benchmark/receipt.json)
and [comparison table](../../qa/math-optimization/results/2026-11-03-jdt-method-benchmark/comparison.tsv)
bind all scores and intervals to the exact code and runtime. The complete source,
fixture bytes, provenance checks, raw samples and command outcomes are retained
in the exact 119,937-byte
[raw analysis archive](../../qa/math-optimization/results/2026-11-03-jdt-method-benchmark/jdt-method-benchmark-raw.tar.gz?raw=true), SHA-256
`bbb4a1caa153c4d66c2141e8a33a321ef2eb7e1458ebdd9864a6d34a44f0e4f1`.
Download and unpack it to inspect the benchmark harness and recorded experiment.
The qualified compiler JARs remain separate inputs identified by hash and their
original local paths in the receipt.

The UI/manipulation result is a legitimate zero-change result, not an omitted
scan. Candidate search and emission gates rejected proposed changes, including
an unnecessary cast before Apply. Aggregate diagnostic codes combine unresolved
bindings with unsupported types/nonlocal expressions; they do not prove there
were zero unresolved bindings. Five UI source units belong to a prebuilt
jar-in-jar-loader ZIP and are unchanged. The new CI gate rejects a future change
there unless those sources are actually rebuilt.

### Preventing a false green upstream build

The pinned Core POM contains literal `baselineReplace=common`, overriding the
command-line property intended to disable Tycho replacement. A concrete after
build compiled the changed `CompletionEngine.class`, then packaged the original
release class instead. A passing test of that JAR would not qualify the change.
Both isolated comparison phases therefore receive the same one-line build-only
patch, `baselineReplace=none`, retained separately from the mathematics patch.
After that correction, all 1,456 Core and 811 compiler classes, all five classes
in the embedded Ant adapter JAR and all three classes in its separately packaged
artifact match their compiler outputs byte-for-byte. The latter artifact uses
the two exclusions already declared by its official upstream packaging. This proves
the packaged artifact identity; full test execution remains a separate gate.

The opt-in JDT jobs build the Sandbox product from the same workflow checkout,
verify its commit, adapter and embedded SDK hashes, and run Core/UI independently
with the official Java 8/17/21/25 toolchains. Maven/JUnit requires every expected
test module to execute passing tests, compares complete inventories, and checks
the actual reactor paths in the test runtime configuration. The generated phase
uses the baseline dependency cache offline. Enable these long jobs with the PR
label `math-upstream-qa` or workflow-dispatch input `jdt_upstream=true`.
Adding that label starts qualification; subsequent source updates on the labeled
PR qualify the new checkout. Adding an unrelated label skips both JDT jobs and
does not restart an in-progress comparison.

The earlier local UI reactor compiled its reachable production bundles and UI test
sources, then its test harness failed before executing a test because
`gtk_init_check()` could not open a display. That is a baseline environment failure.
A local Core test attempt was killed by the shared 8 GiB memory limit; subsequent large
JVM runs are serialized and test heaps bounded while preserving the official suite.
Neither attempt is a passing regression comparison.

### Completed native JDT Core result

The complete native Core qualification subsequently passed in
[job 125710470044](https://github.com/carstenartur/sandbox/actions/runs/41572836149/job/125710470044)
on tested head `55a98f69cd59fbcdb67813091ea2af3a3cc92374`. Both official
20-module `clean verify` builds returned zero. Each phase contains **290,650
successful reported test executions in 24 XML files**, with zero skips, failures
or errors. Exact identities, multiplicities and states match; no tests are
missing or added. The second complete build runs offline against the first
phase's resolved cache, with the same toolchains and official suite selection.
This is the complete pinned default reactor selection. The performance module's
default is OneTest, one reported testcase; the separate opt-in performance suite
is not included in this regression count.

Native Apply scanned all 553 compiler.batch and 1,073 Core source units and
accepted the same five replacements in the same four files as the local
experiment. The native patch SHA-256 is
`316d2ee4361ff2bfaa3d1b1842c93ae4179c05f8052a33ec65e44c9e303d01ef`.
It differs from the local patch file solely because four Git `index` lines use
ten-character blob abbreviations instead of seven; paths and patch hunks are
identical. The unaltered emitted source introduces the same 13 compiler warnings
in the native build. No compilation or test failure was observed for this patch.

The [receipt](../../qa/math-optimization/results/2026-11-03-jdt-core-upstream/receipt.json)
records the source/product identities, full module inventories and artifact
verification. The 94,936,514-byte raw archive has SHA-256
`4af4c968fd09adb568f9f852cd725ea1ceb80165c8e4532721f189d5c3ae02f1`.
Both before/after MethodInfo class snapshots match the exact classes used by the
targeted JMH experiment, with hashes `bc0956e48f95176ea2223d2a64f404445ea1c39f629ff11da8d59c9367ebf883`
and `2ae8970c5e04e60dcaa1e85c4d962876db14a15abbe3d82e2c083ca8c03fdb90`.
The functional success and the measured large-table performance regression
therefore concern the same generated MethodInfo code.

In this pinned JDT release, MethodInfo belongs to compiler.batch; core's manifest
requires and reexports that bundle. Every one of the eight actual test runtime
configurations in each phase names the corresponding reactor compiler.batch
main JAR; seven also name the reactor core main JAR. All 811 packaged compiler
classes match compiler output and the corresponding local benchmark build.
The native gate verifies these runtime configuration paths and complete JAR
contents; it does not add a per-class ProtectionDomain/CodeSource trace to the
upstream test JVMs. The separate targeted JMH gate does perform that loaded-class
CodeSource check.

### Completed native JDT UI result

The independent native CI UI qualification completed successfully in
[job 125710470055](https://github.com/carstenartur/sandbox/actions/runs/41572836149/job/125710470055)
on tested head `55a98f69cd59fbcdb67813091ea2af3a3cc92374`. Both official 25-module
`clean verify` phases returned zero; the generated phase used the resolved
baseline cache offline. The complete seven-report inventory is identical:
**9,577 test cases per phase, 9,423 passed, 154 skipped, zero failures or errors**.
All three required UI, refactoring and text test modules executed passing tests;
the remaining 58 cases are in the sample project.

Native Apply again scanned all 945 manipulation and 1,716 UI source units and
accepted zero replacements. All recorded source hashes matched the actual files.
The 3,510 UI and 1,723 manipulation compiler-output classes are byte-identical
between phases, and both phases passed the packaged-class and configured
test-runtime reactor-JAR gates. The
[receipt](../../qa/math-optimization/results/2026-11-03-jdt-ui-upstream/receipt.json)
records the complete source/product/archive identities and module inventories.
This is a successful complete zero-change comparison. Core and UI are independent
official reactors: this UI result does not qualify the separately transformed
Core inside a combined UI/Core product. The separately completed Core result is
reported above.

### Sandbox and Oomph checks

The regular full Sandbox Maven verification on tested head
`55a98f69cd59fbcdb67813091ea2af3a3cc92374` completed successfully in
[job 125710159305](https://github.com/carstenartur/sandbox/actions/runs/41572836063/job/125710159305).
The final Maven verification check and CodeQL also passed. These validate the
Sandbox change itself; upstream JDT before/after results are separate evidence.
The hash-verified report archive contains 717 JUnit XML files from 28 modules:
**6,538 reported test cases, 6,526 passed, 12 skipped, zero failures or errors**.
The mathematics module accounts for **1,188 passed cases in 136 reports, with
zero skips**. The unchanged shared inventory reader found no duplicate test
identities or parse errors. The
[receipt](../../qa/math-optimization/results/2026-11-03-sandbox-ci/receipt.json)
binds the exact artifact, parser and source identities; the raw archive and
individual module/skip inventories are retained separately.

The initial Sandbox Maven build in PR 1797 passed. Its separate official Oomph
workspace job failed during Maven import/indexing: JDT reported that its
`org.eclipse.jdt.core-3.47.0.jar` package root was no longer on `sandbox_common`'s
build path (Java model error 1006). No mathematics transformation was applied in
that provisioning test. The affected JARs were present and readable; the exact
cause of that transient workspace failure has not been established.

The unchanged Oomph gate subsequently passed on both later qualification heads.
On current tested head `55a98f69cd59fbcdb67813091ea2af3a3cc92374`,
[job 125710159203](https://github.com/carstenartur/sandbox/actions/runs/41572836063/job/125710159203)
completed fresh provisioning and manual setup after restart: **38 tests, zero
failures, errors or skips**. No Oomph source, test selection or error-log gate was
weakened. The earlier failure remains retained as a separate historical result.
