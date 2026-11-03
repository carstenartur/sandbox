# Mathematics: bytecode and upstream qualification, 2026-11-03

## What is being qualified

The starting Sandbox revision is `95bfbddc067d0033a86fb2af97baf6b5cd81e297`.
The numerical optimizer and ordinary Apply implementation are unchanged by this
qualification work. The additions close specific SWT execution gaps and connect
the existing source gate to official upstream build/test commands.

| Input | Exact identity |
| --- | --- |
| Bouncy Castle | `r1rv85v2`, `854bd5c4286e1530132418906bf6175da7d9d83c` |
| SWT | `221d1de66c33292400c91cbd17e3121f72b04ea3`, P2 `3.136.0.v20261029-1803` |
| JDT Core | `R4_41`, `6725c16c24d94c83302346dc384bb915a0f2fe1a` |
| JDT UI | `R4_41`, `54bd63e6453a7eb5db0bfc1d60f17e9568095619` |
| JDT release releng | `R4_41`, `10be605e8924c917d5f0013930278a2b1cb756f5` |
| Local JDK | Temurin `25.0.5+8` |

The actual Sandbox installation was obtained from Linux distribution artifact
`13197211842` in [run 41558508599](https://github.com/carstenartur/sandbox/actions/runs/41558508599).
Its downloaded archive SHA-256 was verified as
`d9e561d83ffe0890dabc811b298bdd6da967936f2204d1d0371da08b25a9cb90`.
This installation contains the unmodified mathematics bundle built from the
starting Sandbox revision. JDT qualification uses the real native application
`sandbox_math_cleanup.analysis`, with real imported Java/PDE projects.

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

## Source comments already available

`cleanup.mathematics.explanations` supports `NONE`, `NONTRIVIAL` and `ALL`.
The default `NONTRIVIAL` deliberately omits small, uncomplicated bit identities;
`ALL` includes explanations for those accepted changes too. Full explanations
remain in diagnostics/reports independently of the source-comment mode.

`MathExplanation` records the actual verified numerical contract, premises,
original/replacement value expressions and independent proof methods. Its work
estimate explicitly says `not a timing measurement`. This is suitable information
to retain in the source. For example, a review of the renderer simplification
can explain the Java integral identity `(j - a) - (b - a) = j - b`, including
wraparound, without claiming a measured speedup.

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
with no allowed renames/additions/removals. The CI run must finish before these
upstream tests can be called green.
The generated build runs offline against the baseline's resolved cache, so a
moving P2 composite cannot supply different dependencies for the second build.

An additional JUnit gate requires actual passing tests in both the main SWT test
module and the separate Linux GTK test module. Equal inventories containing only
skips, or equal inventories that both omit a module, are insufficient. Regression
fixtures first reproduced both false-green cases and then verified their rejection.

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

## Remaining upstream experiments

The official BC `:core:test :prov:test` before/after runs and the JDT Core/UI
build/test comparisons are being executed separately. No all-tests claim is made
from an in-progress run. BC scope is the changed bcprov artifact, not the separate
PKIX, TLS, PGP or mail products. Its ordinary upstream test mode samples some PQC
vectors; the changed classical digest suites are separate from that sampler.

Native JDT Apply has already completed for all 1,626 Java units in the compiler
and Core bundles: five changes in four files. Every recorded original was checked
against the untouched checkout and every replacement against the written file,
with zero mismatches. The patch contains positional arithmetic in
`AbstractCommentParser`, two hexadecimal-character calculations in `ScannerHelper`,
constant addition in `CompletionEngine`, and a guarded finite-sum replacement in
`MethodInfo`. The latter substantially increases source size and introduces a
BigInteger path for at least 4,096 iterations; it is not yet a demonstrated
performance or readability improvement.

The unchanged UI reactor compiled its reachable production bundles and UI test
sources, then its test harness failed before executing a test because
`gtk_init_check()` could not open a display. That is a baseline environment failure.
A Core test attempt was killed by the shared 8 GiB memory limit; subsequent large
JVM runs are serialized and test heaps bounded while preserving the official suite.
Neither attempt is a passing regression comparison.
