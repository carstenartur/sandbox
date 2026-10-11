# BigInteger coverage: inventory, causes and ten verified changes

This report records the initial qualification of the BigInteger coverage follow-up
with SDK revision `86e86415196063366c5b48089d5d49ac451077d5`. Its compile,
native capture and 126-test results below are historical evidence for that
revision. The current ae1c SDK, repeated corpus scan and 180-test qualification
are recorded in the [current integration report](../2026-10-10-candidate-quality/README.md#modular-integration-qualification-2026-10-11).
A passing native capture from the earlier revision does not qualify the current
SDK/adapter.

This follow-up investigates the concern that five BigInteger changes are too few.
The result is **ten BigInteger computation replacements in five complete classes**,
with 114 total replacements under mathematical opt-in. The default strict profile
still yields 104 replacements. These are accepted source regions, not counts of
method calls, Javadoc edits or search candidates.

## Pinned input and complete inventory

* Bouncy Castle `org.bouncycastle:bcprov-jdk18on:1.85.2:sources`, SHA-256
  `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`.
* All 3,136 Java entries inspected; 3,126 parse without errors against the matching
  binary archive at Java 17. Ten module/multi-release entries need another context.
* **3,568 resolved java.math.BigInteger method calls**, including **1,685 arithmetic
  calls**. Bindings, rather than names or comments, determine ownership.
* Arithmetic counts: multiply 357, mod 335, subtract 244, add 188, modPow 161,
  shiftLeft 110, shiftRight 95, divide 55, negate 43, pow 41, abs 33,
  remainder 8, or 8, xor 5, and 2.
* The initial fast inventory prefiltered files containing the text `BigInteger`.
  It missed 36 resolved calls, including two modular powers in other files. The
  final counts above come from the complete archive without that text filter.

A large call count is not an optimization count. Many expressions are individual,
necessary operations. In particular, 161 modular powers do not imply 161 removable
powers: the ten accepted locations below all have exponent two. No dozens of
additional runtime improvements have been established by this scan.

## Causes addressed

1. Ordinary `mod` was missing from the SDK modular bridge. Computed bases and
   nested reductions were outside the independent proof fragment. Regelsuche
   PR #1086 adds bounded transport and proof composition.
2. Assignments to incoming locals were rejected along with external writes.
   A BigInteger local assignment now forms a standalone region in mathematical
   mode. It never carries an old input binding through a subsequent mutation.
3. One difficult multi-statement result could hide a valid square. Failed
   NoImprovement/Inconclusive regions receive bounded statement retries, sharing
   the file budget. Inconclusive results report consumed work so retries do not
   discard unspent allowances or invent extra budget.
4. A user-defined static final `TWO = BigInteger.valueOf(2)` was an unknown input, so
   the small-power rule could not match it. A resolved literal initializer is now
   used in the proof, retaining an exact-receiver guard on the field. This guard
   matters during recursive initialization, when even a final field may be null.
5. `this.q` was rejected although unqualified final `q` worked. Final fields of the
   enclosing method's own class are supported when no local declaration shadows
   their identifier. Mutable fields, foreign fields and shadowed names stay out.
6. All guarded inputs previously required positivity. Only modulus/exponent
   premises request it now; signed and zero bases can take the optimized branch.
   Identical generated method contract notes are deduplicated.
7. Native preview exposed a Javadoc insertion bug: zero-length replacement edits
   cannot contain the copied stale-source validation child. Documentation edits
   now retain one source character, preserving the generated text and the guard.
8. Independent review found exponential traversal of shared expressions while
   collecting positive-input guards (repeated `e=e.add(e)` before `modPow`).
   An iterative identity-based traversal now visits each node once and rejects
   graphs exceeding 4,096 distinct nodes before optimizer search. The regression
   failed before the fix; a 60-doubling probe now respects a one-unit search budget.

## Exact additional locations

All paths below are relative to `org/bouncycastle/` in the source archive.

| Class | Original lines | Accepted regions |
| --- | --- | ---: |
| crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java | 56, 58, 64, 65 | 4 |
| math/Primes.java | 201, 222, 458 | 3 |
| crypto/generators/CramerShoupParametersGenerator.java | 104 | 1 |
| crypto/hash2curve/impl/GenericSqrtRatioCalculator.java | 87 | 1 |
| pqc/crypto/sqisign/Ibz.java | 552 | 1 |

The five additions beyond the previous local five-hit result are the three
Primes locations, Cramer–Shoup and GenericSqrtRatioCalculator. The complete scan
has 114 replacements in 55 classes, versus 104 in 50 classes in strict mode.
Every strict before/after replacement also appears unchanged in the mathematical
scan. All 55 generated complete classes compile with `javac --release 17`.

## Initial qualification (historical)

At the initial qualification, the exact embedded SDK was built from Regelsuche
`86e86415196063366c5b48089d5d49ac451077d5`, standalone SHA-256
`34975f3f3fbd2096c654a0e7b80679d53cde6cf2b7242d2380f5380a1059c073`.
Its recovered distribution is byte-identical to the previously qualified artifact;
qualification and reproducibility receipts are in `sandbox_math_cleanup/lib/`.

The full scan uses the production extractor, optimizer, independent recheck,
Java emission verification, combined source parsing and exact Undo. Settings:
all numeric kinds, PRESERVE_JAVA, LOWER_ESTIMATED_RUNTIME, 1,000,000 work units per
file, 20,000 states; mathematical opt-in is the only profile difference.

126 local Maven/JUnit tests pass against that exact embedded JAR on Java 25.0.2
and JDT 3.47.0. `BouncyCastleBigIntegerTest` reads hash-pinned complete original
classes, requires all ten changes, compiles original and generated classes,
checks second-pass stability and exact Undo, then compares actual executions:

* 100 curve-map inputs, including zero, negative and 5,001-bit inputs;
* 210 signed sqrt-ratio inputs, comparing residue flags and roots;
* 460 Tonelli–Shanks inputs, including nonresidues;
* 127 inputs through each of three primality APIs, including composite flags
  and returned factors;
* 96 reproducibly seeded Cramer–Shoup generator invocations.

Focused regressions also cover null/recursive initialization, field shadowing,
mutable fields, incoming assignments and unchanged subclass fallback behavior.
The local standalone harness excludes the OSGi-only runtime corpus test; its
pinned bundle requirement is preserved. A separate Tycho run compiles the plugin
and test bundle but cannot launch that test offline because the target's
`junit-jupiter-api.source_6.1.3.jar` is missing from the local cache.
The earlier scoped SpotBugs run reported no findings in the three changed analysis classes.
At the initial qualification, the native MathematicsHelpScreenshotsSWTBotTest
passed through the registered
cleanup, exact preview, Apply, source compilation and byte-exact Undo for all five
fixtures. The new BigInteger PNG and its complete provenance are in the Help
plugin. Unrelated raster differences in the four existing images are not accepted.
The full repository/installation CI gates remain separate from this qualification.

## Remaining coverage and cost limits

Array reads, mutable object holders such as SQIsign's `a.v`, and unknown getters
need explicit evaluation/alias modelling before extraction can be broadened.
Nonliteral divisors, computed exponent signs and bounded shift distances also
need additional premises. A larger search allowance does not supply these facts.
An isolated redundant `x.mod(m).mod(m)` is now provable but still rejected for the
runtime goal when receiver guards erase the predicted saving. Guard costs and
independent checking have not been relaxed to increase the hit count.

The current BigInteger operation cost model is coarse; equal nominal costs for
operations with very different operand sizes limit ranking. Improving it requires
measurements of generated code at representative sizes, including guard and
fallback paths. This report establishes numerical/source qualification, not a
whole-library speedup, constant-time behavior or approval to patch Bouncy Castle.

## Reproduce the diagnostic

`qa/math-optimization/src/main/java/org/sandbox/math/qa/BigIntegerCorpusScan.java`
uses the production analysis API and is compiled with the other QA Java sources
by the existing `qa/math-optimization/run.sh` adapter. With that QA classpath,
invoke `org.sandbox.math.qa.BigIntegerCorpusScan` with four arguments:
matching binary JAR, source JAR, a fresh output directory, and `strict` or
`mathematical`. It writes resolved-call and diagnostic TSV files, accepted
before/after regions, generated complete classes and aggregate counts. Inputs
are not executed during the scan. Class execution belongs to the named JUnit
regressions above. Large raw source inventories are not duplicated in this PR.
