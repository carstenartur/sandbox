# Generic numeric domains on current Main: controlled corpus comparison

The combined implementation exposes **503 additional extracted Bouncy Castle input regions in each mode**. Against current Main, accepted regions change from **104 to 101** in strict mode and **115 to 112** in mathematical mode. The same **50 strict and 56 mathematical generated classes** remain represented; all **11 mathematical-only accepted locations** are retained. These measurements establish broader source admission under unchanged budgets, without establishing faster generated code.

## Qualified source

The baseline is Sandbox Main
[`7e11fd618733a390268d38fbd7e12014b0948f23`](https://github.com/carstenartur/sandbox/commit/7e11fd618733a390268d38fbd7e12014b0948f23),
tree `2ec64b3e4360bf48aca71efc07f79dc4c736784e`.
The combined candidate is
[`193d1e9bb0e889002d633656b4567819aee3bddd`](https://github.com/carstenartur/sandbox/commit/193d1e9bb0e889002d633656b4567819aee3bddd),
tree `bbf12060811496f2b21b7a66fc284b32c769a745`, with these exact parents:

- Ordered-array source admission [#1696](https://github.com/carstenartur/sandbox/pull/1696),
  `f50597c96796cfb9008d61c5e2e0d598bff3a9a7`.
- Shared SDK adapter [#1697](https://github.com/carstenartur/sandbox/pull/1697),
  `2f3d6c7c70eb6eaccbfb7c1679ae250a9ddcabf8`.

The baseline embeds SDK source `6a29154e217ea8748257cfda0340eb463ff157b2`,
JAR SHA-256 `d1efa15f487872ca46fab9b4d8640495f8df19ea9e354ee52d08f885e59afea7`.
The candidate embeds the qualified [Regelsuche #1088](https://github.com/carstenartur/Regelsuche/pull/1088)
source `0bb2e6848693a37d7682da60f98129f5a3debe2a`,
tree `fe5a5ba161006e53d5a7ba51c33959e3b36627d8`,
version `0.5.0-issue1657-0bb2e6848693`,
JAR SHA-256 `4f848eb5f7f6314689cd1c0e22c4b4bd69b5f6f9d8b5d9bea38c2860bf3140aa`.

This is a new comparison against Main's integrated BigInteger magnitude,
modular-domain and retry behavior. The earlier `b7f0bd4` versus `63cdbd17`
[qualification](../2026-10-11-generic-numeric-domains/README.md) remains a separate,
immutable historical result. Its measurements are not substituted for these runs.
The documentation commit containing this report does not change the qualified
runtime source; the commit above is the source to reproduce.

## Comparable inputs and results

All four scans use every sorted Java source entry from
`org.bouncycastle:bcprov-jdk18on:1.85.2:sources`, its matching binary archive,
identical scanner source and both compiled scanner classes, and the same 27
non-SDK dependency JARs. The order is Main strict, Main mathematical, candidate
strict, candidate mathematical, with no concurrent local product builds.
Source, class snapshot, scanner, dependency and SDK/receipt fingerprints are
checked before and after every mode. The SDK found on each runtime classpath
must equal both its checkout's JAR and the SHA-256 in its source-bound receipt.

Options remain all numeric kinds, `PRESERVE_JAVA`, `LOWER_ESTIMATED_RUNTIME`,
**1,000,000 work units per file, 20,000 states, 64 candidates and 5,000 ms per
request**. Mathematical opt-in is selected only for the mathematical runs.
Java source compliance remains 17, with JDK 25.0.2+10 and Maven 3.9.16.

| Measure | Main `7e11fd61` | Combined `193d1e9b` |
| --- | ---: | ---: |
| Successfully parsed source files | 3,126 | 3,126 |
| Source parse failures | 10 | 10 |
| Strict extracted input regions | 2,204 | 2,707 |
| Mathematical extracted input regions | 2,546 | 3,049 |
| Strict accepted regions | 104 | 101 |
| Mathematical accepted regions | 115 | 112 |
| Strict generated classes | 50 | 50 |
| Mathematical generated classes | 56 | 56 |
| Mathematical-only accepted locations | 11 | 11 |

The region counter comes from the scanner's separate `extractRuntime` pass. It
counts extracted input regions available to analysis, rather than verified
improvements or a guarantee that every region receives a complete search.

There are **100 common strict locations** and **111 common mathematical
locations**, using original source file/line keys. The same eleven
mathematical-only locations occur in both profiles. Generated sources are
byte-identical in **48 strict and 54 mathematical classes**. The only differing
classes are `X25519Field` and `HawkEngine`.

The new `X25519Field:596` region contains both complete original fragments
previously accepted at `:608` and `:672`, plus ten ordered reads and connecting
scalar updates. Thus two accepted regions become one, accounting for one of the
three fewer replacements. Its current explanation records all ten reads in
source order, **38 capture-work units**, **10 additional peak-storage values**
and score **1947 to 1677** relative to the original source. The diagnostic's
printed operation comparison `120 -> 158` combines a prepared scalar source
count with a candidate count that includes capture work; it is not a like-for-like
comparison of whole-source operation work. The scalar preparation count itself
is `120 -> 120`. Existing smaller baseline regions already shared conversions,
so the merged region does not establish an additional generated-program speedup.

The other missing locations are `HawkEngine:2471` and `:2575`. In both modes, the
actual current diagnostics report `FINAL_PREPARATION_BUDGET_EXCEEDED`. Their
reported work is **8,904 / 9,974 in strict mode** and **8,884 / 9,960 in mathematical
mode**, respectively. The expanded extraction and its alternatives share the
unchanged file budget, affecting per-request allocation. These messages concern
the preparation allowance of those requests; they do not prove that the complete
file budget was exhausted. The three common Hawk locations `:2763`, `:2952` and
`:3023` differ only by a verified shift of three in the affected generated
temporary-variable names. Original fragments and the explanatory comments about
original values remain unchanged. The exact containment, renaming and class-hash
checks are retained in [interpretation-checks.json](interpretation-checks.json).

The four `*-summary.txt` files are copied from the original scanner outputs.
[qualification.json](qualification.json) binds the exact input versions, all
four run timestamps, original output hashes, per-location differences and test
evidence. [accepted-differences.json](accepted-differences.json) retains the actual
before/after fragments for all changed accepted locations. The SHA-256 manifests
identify every product source, compiled class, dependency and generated class.

## What the implementation contributes

- The shared calculation model retains Java numeric kinds and original evaluation
  traces while exposing established value facts to the existing rule search.
  Supported conditional rules can use established sign, nonzero and bound facts;
  an independent Java checker still verifies each proposed computation. Integer
  division requires its own exact quotient proof where the algebra rule needs it.
- Public `IntegralDomainAnalysis` provides exact local no-overflow ranges for a
  bounded, one-input primitive affine fragment. It reports original and candidate
  ranges separately, verifies equal named outputs, and requires the candidate
  domain to contain the original domain. Sandbox delegates to this SDK API and
  propagates cancellation. These local ranges do not establish caller premises.
- Supported literal `BigInteger.shiftLeft(int)` distances from 0 through 4095
  enter the existing modular translation, subject to its other magnitude and
  search limits. The typed shift argument and source trace remain explicit.
- Ordered one-dimensional primitive array reads become distinct source inputs.
  Every read, including repeated reads, retains its evaluation order. Capture
  stores, actual emitted references and extra live storage are charged. A failed
  connected array attempt can lazily retry eligible scalar subregions under the
  remaining budget. Existing search, presentation and reported overshoot work stay
  charged; overlap checks prevent duplicate replacements.

There is no Bouncy Castle-specific source recognizer or separate search engine.
Array admission, premises and affine analysis are generic. `int`, `long` and
`BigInteger` are not freely interchangeable representations in this change:
their operation semantics remain part of what the adapter and checker preserve.

The BLS result at `org/bouncycastle/crypto/bls/BLS12_381Serialization.java:118`
already belongs to Main `7e11` and SDK `6a291`. It removes two inner `mod(P)`
operations from the `ySquared` calculation while retaining the final modulus,
with declared operation work **24 to 16** and score **251 to 239**, including
the remaining receiver guard. The subsequent shifted-exponent `modPow` expression
at line 121 remains outside the accepted region. The complete generated BLS
class is byte-identical between the two new profiles. It is retained Main behavior,
not an additional result attributable to the Array extension or SDK upgrade.

Main's existing `BouncyCastleModularResidueTest` binds the real BC source by hash,
checks strict behavior, the single mathematical replacement, the proven
384-bit magnitude fact, emitted receiver guards, Undo and idempotence. It
compiles both variants and compares six valid points, infinity and eight invalid
encodings, including exception type and message. That unchanged test is among
the 225 combined standalone tests; it is a targeted public-behavior check.

## Validation and scope

Both independent merge reviews confirm that all 17 Array-exclusive paths equal
the published Array parent and all 11 Adapter-exclusive paths equal the published
Adapter parent. The only common path, `MathematicalAnalysis`, equals the Array
parent plus exactly the two intended cancellation arguments. Main's magnitude,
retry and budget implementation remains intact.

One fresh execution of the existing standalone mathematics suite passes
**225 tests in 32 suites, zero failures, errors or skips**, on the exact combined
tree above. The original Maven exit status, log, all JUnit XML hashes, reviewed
tree and publication binding are checked independently. The existing two test
includes and OSGi-only `BouncyCastleRuntimeCorpusTest` exclusion are unchanged.
Native Eclipse/Tycho, real Help UI and installed-distribution CI qualify their
individual PR sources separately; this report does not relabel those runs as
native execution of the combined source.

The exact new SDK passes its unchanged native
`MavenOptimizationSdkDistributionTest`: **two complete clean reactor builds,
each 2,424 tests with zero failures, errors or skips**, and two fresh external
consumers with empty dependency caches. All ten distribution files are
byte-identical, with the required license/notice entries in all four archive
kinds. The [portable SDK receipt](https://github.com/carstenartur/sandbox/blob/2f3d6c7c70eb6eaccbfb7c1679ae250a9ddcabf8/sandbox_math_cleanup/lib/reproducibility.json)
retains commands, source identities, artifact hashes and original-evidence digests.
The integrated generator/checker use v5/v8; two regression tests reject evidence
from the prior reused v4/v7 identities.

The scanner checks the emitted edits and exact Undo, and production analysis
verifies combined replacements. Region counts, accepted counts and declared
costs establish source-analysis outcomes under these budgets. They do not measure
generated-program runtime, JIT improvements or constant-time behavior. The ten
parse failures remain outside analyzed coverage. The additional cached scalar
AST traversal has no separate SDK work meter or measured cancellation-latency
bound. No whole-library behavioral execution or globally cheapest computation is
claimed.

## Reproduction

Use separate clean checkouts of Main `7e11fd61` and candidate `193d1e9b`. For each,
copy the existing [standalone harness POM](../2026-10-10-candidate-quality/modular-integration-2026-10-11/standalone-harness-pom.xml)
to a fresh directory as `pom.xml`, preserving its plugin/dependency versions,
both test includes (`**/math/tests/*.java`, `**/corext/fix/math/*.java`) and the
existing runtime-corpus exclusion. Adjust only these paths and SDK identity:

- Replace `/workspace/scratch/ab020fe78947/math-1691-integration` in the three
  source/test/SDK paths with the applicable absolute checkout path.
- Use SDK version `0.5.0-issue1657-6a29154e217e` for Main and
  `0.5.0-issue1657-0bb2e6848693` for the candidate, retaining each checkout's JAR.
- Point `sandbox.math.bc.jar` and `sandbox.math.bc.sourceArchive` at the matching
  local BC 1.85.2 binary and source archives.

Prepare both profiles before the first scan. The qualification used `clean
compile` for Main and `clean test` for the candidate; no baseline local test count
is claimed. The following candidate command reproduces the complete standalone
suite. For Main, change that command's `test` goal to `compile`.

```sh
set -eu
MATH_SOURCE=/absolute/checkout
MATH_RUN=/absolute/fresh-harness
MATH_BC=/absolute/bcprov-jdk18on-1.85.2
mkdir -p "$MATH_RUN/scan-classes" "$MATH_RUN/tmp"
mvn -o -B -ntp -f "$MATH_RUN/pom.xml" clean test dependency:build-classpath \
  -Dmdep.outputFile="$MATH_RUN/dependencies.classpath"
MATH_CP="$MATH_RUN/target/classes:$(cat "$MATH_RUN/dependencies.classpath")"
javac --release 25 -cp "$MATH_CP" -d "$MATH_RUN/scan-classes" \
  "$MATH_SOURCE/qa/math-optimization/src/main/java/org/sandbox/math/qa/BigIntegerCorpusScan.java"
```

Offline Maven requires the pinned dependencies/plugins to be cached. Compare the
prepared source/class, scanner, BC and dependency hashes with the included
manifests. Reproduce the modes sequentially for Main first, then for the
candidate, without compiling or changing either profile during the scans:

```sh
for MATH_MODE in strict mathematical; do
  java -Xmx512m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m \
    -XX:MaxDirectMemorySize=64m -Xss512k -Djava.io.tmpdir="$MATH_RUN/tmp" \
    -cp "$MATH_RUN/scan-classes:$MATH_CP" org.sandbox.math.qa.BigIntegerCorpusScan \
    "$MATH_BC.jar" "$MATH_BC-sources.jar" "$MATH_RUN/$MATH_MODE" "$MATH_MODE" \
    >"$MATH_RUN/$MATH_MODE.log" 2>&1
done
```

The qualification additionally used immutable copies of the compiled product
classes. Scanning times in the receipt describe the optimizer execution on its
host, not the speed of generated Bouncy Castle code. Fixed timeout/work limits
are part of the measured behavior; changing them creates a different comparison.

The recorded optimizer wall times are observational. Small unrelated local file
operations also experienced delays during the candidate runs, so these executions
are not an isolated optimizer-throughput benchmark. Fixed deadlines can make
bounded-search outcomes sensitive to host load; all source, budget and artifact
identities for these particular executions are retained.
