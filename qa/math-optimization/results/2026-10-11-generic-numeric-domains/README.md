# Generic numeric domains and ordered array inputs: corpus qualification

The combined integration makes **503 additional Bouncy Castle regions** available
to analysis in each mode. Accepted regions change from **104 to 101** in strict
mode and **114 to 111** in mathematical mode. All 50 strict and 55 mathematical
generated classes remain represented. This establishes broader source analysis;
it supplies no measured generated-program speedup.

## Exact integration and comparable inputs

This report qualifies combined source
[`63cdbd17ed3308ecfaf3643e6d4ee35cd0b3134d`](https://github.com/carstenartur/sandbox/commit/63cdbd17ed3308ecfaf3643e6d4ee35cd0b3134d),
tree `17c20542063e98ea6e57f0bdc5a81e8df4b7c767`.
It contains the ordered-array extension in [#1696](https://github.com/carstenartur/sandbox/pull/1696),
the shared SDK adapter in [#1697](https://github.com/carstenartur/sandbox/pull/1697),
and helper support already merged in main `b7f0bd4b3e945c328b78d45b69d3aa07bf9cb1b1`.

The baseline is the qualified helper R2 snapshot, whose 16 recorded production
source hashes all match that merged main. Its original accepted-output hashes
were rechecked against the [existing receipt](../2026-10-11-biginteger-helpers/qualification.json).
The new SDK is [Regelsuche #1088](https://github.com/carstenartur/Regelsuche/pull/1088),
source `2452f1d679bf9e4de7641759efbe30c88af2588c`,
JAR SHA256 `7e0b684189d1c9e97eacdd41a996aa87d7c84e1c7cb49da0f45fa245831b8a65`.

Both comparisons use every sorted Java entry in `bcprov-jdk18on:1.85.2:sources`,
the matching binary, identical scanner source/bytecode and the same 27 non-SDK
dependency JARs. Strict and mathematical modes run sequentially. Options remain
all numeric kinds, `PRESERVE_JAVA`, `LOWER_ESTIMATED_RUNTIME`, 1,000,000 work
units per file, 20,000 states, 64 candidates and a 5,000 ms request timeout.
Java 17 source compliance and Java 25.0.2+10 / Maven 3.9.16 are unchanged.

## Results and the smaller replacement count

| Measure | Qualified baseline | Final integration |
| --- | ---: | ---: |
| Successfully parsed files | 3,126 | 3,126 |
| Parse failures | 10 | 10 |
| Strict regions reaching analysis | 2,204 | 2,707 |
| Mathematical regions reaching analysis | 2,546 | 3,049 |
| Strict accepted regions | 104 | 101 |
| Mathematical accepted regions | 114 | 111 |
| Strict generated classes | 50 | 50 |
| Mathematical generated classes | 55 | 55 |
| Mathematical-only accepted BigInteger regions | 10 | 10 |

There are 100 unchanged-location strict keys and 110 mathematical keys.
The ten mathematical-only keys are unchanged. Generated sources are byte-identical
for 48 strict and 53 mathematical classes; the two differing classes are
`X25519Field` and `HawkEngine`. The [receipt](qualification.json) and generated
source manifests retain their exact identities.

The new X25519 key at line 596 covers both former keys 608 and 672, plus ten
ordered reads and the connecting scalar updates. Thus two formerly separate
accepted regions become one. Independent inspection of the actual emitted Java
finds 94 original `int`-to-`long` conversions reduced to 28 shared conversions.
Other arithmetic counts and all ten reads remain equal. This kind of sharing
already occurred inside the former smaller regions; the new key alone proves
no further improvement over the previously generated class.

The two other missing keys are `HawkEngine:2471` and `:2575`. Both are attempted
and report `FINAL_PREPARATION_BUDGET_EXCEEDED`, with strict-mode work 8,904 and 9,974.
At 2471 the scalar retry is explicitly present after the larger array attempt;
2575 is directly an array-free region. Additional attempts share the same file
budget and change its allocation. These outcomes concern each request's share,
not proof that the entire file budget was exhausted. The three later Hawk
replacements at 2763, 2952 and 3023 differ only by generated-name numbering;
their before/after computations are identical after that concrete renaming.

## What was implemented

- The public SDK `IntegralDomainAnalysis` owns bounded, one-input affine integral
  range analysis and separate original/candidate checks. Sandbox delegates to it.
- Existing algebra rules can discharge supported premises using established
  typed facts. Independent Java verification still checks accepted proposals.
- Bounded literal `BigInteger.shiftLeft(int)` expressions enter modular
  translation. Java kinds, promotion, overflow behavior and source trace remain
  explicit in the shared calculation model.
- The source adapter imports ordered one-dimensional primitive array reads as
  distinct inputs, joining supported scalar arithmetic around them. It preserves
  every read and its order and charges capture stores, actual emitted references
  and retained values. This adds no Bouncy Castle-specific rule or recognizer.

The real ECJPAKE:85 and Owl:93 determinant callers now transport `shiftLeft(2)`
successfully as factor 4. Their modular generator produces no rewrite. The only
first-round algebra proposal exchanges factors of the product with 27 and keeps
the same modeled work 40 / score 413. A narrow ECJPAKE probe reproduces
`NO_IMPROVEMENT_WITH_FULL_POLICY_COST / EXHAUSTED_BOUNDED_SPACE / work=31163`;
Owl has the same graph, facts and costs. Successful transport does not guarantee
that the available rules contain a cheaper computation.

## Regression found and corrected

The first complete attempt accepted 88 strict and 98 mathematical regions:
rejected larger array regions hid useful former scalar regions. A generic lazy
alternative extraction now treats array statements as boundaries after a failed
connected attempt. Strictly contained scalar alternatives share the remaining
budget; previous search, explanation and reported overshoot costs stay charged.
Pending/accepted overlap checks and array-free alternatives prevent duplicate
edits and recursive array retries. Capture costs and search limits are unchanged.

Three regression tests fail on published pre-fix source `2d927afd` and pass with
the fix. They cover executed original/generated Java, exception order, two
disjoint edits, exact Undo, actual remaining search allowance and budget failure.
The independently reviewed final integration passes **215 standalone tests,
zero failures/errors/skips**, including 19 array behavior tests and two budget
tests. The harness retains its existing OSGi-only `BouncyCastleRuntimeCorpusTest`
exclusion; native Eclipse/Tycho and distribution CI remain separate authorities.

The SDK reactor passes **2,403 tests, including 164 SDK tests**. Two clean release
builds and two fresh external consumers produce ten byte-identical distribution
files, with all required notices in every archive. Exact commands and hashes are
in the [source-bound SDK receipt](https://github.com/carstenartur/sandbox/blob/5d664693896f5859c8497032fd6931491692068a/sandbox_math_cleanup/lib/reproducibility.json).

## Interpretation and reproduction

For X25519, original trace work is 186, prepared scalar work 120 and capture work
38. Qualified work is 158 and the score changes 1947 to 1677, including ten extra
peak-storage units. The existing diagnostic's source-cost field reports the
prepared 120, while its source score uses the original 186; the receipt does not
treat the resulting display `120 -> 158` as a like-for-like operation comparison.
These are declared static costs, including conversions the JIT may already share.

For reproduction, use a clean checkout of the combined commit above, JDK 25.0.2+10
and Maven 3.9.16. Copy the [standalone harness POM](../2026-10-10-candidate-quality/modular-integration-2026-10-11/standalone-harness-pom.xml)
to a fresh scratch directory as `pom.xml`, with these path/version adjustments:

- Replace `/workspace/scratch/ab020fe78947/math-1691-integration` with the absolute
  checkout path in `sourceDirectory`, `testSourceDirectory` and SDK `systemPath`.
- Replace SDK version `0.5.0-issue1657-ae1c39ea50cf` with `0.5.0-issue1657-2452f1d679bf`.
- Point `sandbox.math.bc.jar` and `sandbox.math.bc.sourceArchive` at the matching
  local BC 1.85.2 binary/source JARs. Keep both existing test includes
  (`**/math/tests/*.java`, `**/corext/fix/math/*.java`) and the runtime-corpus exclusion.

Set the paths below to those prepared inputs. Maven's `-o` requires cached
dependencies/plugins; use the POM versions and recorded dependency hashes.

```sh
set -eu
MATH_SOURCE=/absolute/checkout-of-63cdbd17
MATH_RUN=/absolute/prepared-fresh-harness
MATH_BC=/absolute/bcprov-jdk18on-1.85.2
mkdir -p "$MATH_RUN/scan-classes" "$MATH_RUN/tmp"
mvn -o -B -ntp -f "$MATH_RUN/pom.xml" clean test
mvn -o -B -ntp -f "$MATH_RUN/pom.xml" dependency:build-classpath \
  -Dmdep.outputFile="$MATH_RUN/dependencies.classpath"
MATH_CP="$MATH_RUN/target/classes:$(cat "$MATH_RUN/dependencies.classpath")"
javac --release 25 -cp "$MATH_CP" -d "$MATH_RUN/scan-classes" \
  "$MATH_SOURCE/qa/math-optimization/src/main/java/org/sandbox/math/qa/BigIntegerCorpusScan.java"
```

Verify dependency, source/class and scanner hashes against this receipt before
running the two modes sequentially with fresh output directories:

```sh
for MATH_MODE in strict mathematical; do
  java -Xmx512m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m \
    -XX:MaxDirectMemorySize=64m -Xss512k -Djava.io.tmpdir="$MATH_RUN/tmp" \
    -cp "$MATH_RUN/scan-classes:$MATH_CP" org.sandbox.math.qa.BigIntegerCorpusScan \
    "$MATH_BC.jar" "$MATH_BC-sources.jar" "$MATH_RUN/$MATH_MODE" "$MATH_MODE" \
    >"$MATH_RUN/$MATH_MODE.log" 2>&1
done
```

The scanner records accepted source, complete generated classes and exact Undo;
production analysis verifies combined edits. Input source/class hashes were
unchanged throughout both qualified scans.

The included SHA256 manifests bind dependencies, product sources, compiled
classes and generated classes; the receipt also binds every attempt's raw outputs.
No whole-library behavioral execution, JIT benchmark or constant-time property
was established. The extra scalar AST extraction is cached once per analysis;
its traversal has no separate SDK work meter or measured cancellation-latency bound.
