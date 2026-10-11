# BigInteger helpers: broader analysis, unchanged corpus replacement count

Same-class private static helpers with BigInteger parameters, local variables
and return values now participate in mathematical-mode analysis. The existing
Regelsuche SDK searches the original expanded computation; this change adds no
rewrite rules, formula recognizers or search allowance. Explicit mathematical
opt-in remains required for the BigInteger value-semantics contract.

The complete Bouncy Castle comparison found **zero additional accepted
replacements**. This is a verified extension of source analysis, not evidence
of a corpus-wide performance improvement.

## Comparable before and after

The baseline is sandbox `aca02bfb0509653ce5b5c9dedf7685e3f5698eb7`.
Both revisions were compiled into separate, immutable source/class snapshots.
All dependency JAR bytes, the scanner, source archive and search settings match.
The [receipt](qualification.json) identifies the measured production source
bytes, dependencies, test results and scan outcomes.

| Measure | Baseline | With BigInteger helpers | Change |
| --- | ---: | ---: | ---: |
| Strict-mode accepted replacements | 104 | 104 | 0 |
| Mathematical-mode accepted replacements | 114 | 114 | 0 |
| Mathematical-only accepted BigInteger regions | 10 | 10 | 0 |
| Mathematical regions reaching analysis | 2,544 | 2,546 | +2 |
| Generated complete classes, mathematical mode | 55 | 55 | 0 |

All 104 strict replacements and all 114 mathematical replacements are retained
with the same before/after evidence. Every generated source file is byte-identical
to its baseline counterpart: 50 files in strict mode and 55 in mathematical mode.
The unchanged ten mathematical-only locations are recorded in the
[previous integration qualification](../2026-10-10-candidate-quality/README.md#modular-integration-qualification-2026-10-11).
The difference between 104 and 114 compares two modes; it is not this change's
temporal improvement.

Both runs inspect the complete `bcprov-jdk18on:1.85.2:sources` archive without a
text prefilter: 3,126 entries parse successfully and ten have parse errors in
the Java 17 context. Options remain all numeric kinds, `PRESERVE_JAVA`,
`LOWER_ESTIMATED_RUNTIME`, 1,000,000 work units per file and 20,000 states.
The embedded SDK remains Regelsuche `ae1c39ea50cff685fe843b0357c818f38db6ec79`.

## What the real corpus newly exposes

Two calls now reach optimizer search instead of stopping at an unsupported
method boundary:

| Source entry, relative to `org/bouncycastle/` | Caller line | Helper |
| --- | ---: | --- |
| `crypto/agreement/ecjpake/ECJPAKECurve.java` | 85 | `calculateDeterminant(q, a, b)` |
| `crypto/agreement/owl/OwlCurve.java` | 93 | `calculateDeterminant(q, a, b)` |

These helpers calculate the same modular polynomial, `(4*a^3 + 27*b^2) mod q`,
using multiplication, intermediate reductions and a shift. Each search consumes
31,107 work units and reports `NO_IMPROVEMENT_WITH_FULL_POLICY_COST` with
`EXHAUSTED_BOUNDED_SPACE`. This result does not establish that no mathematical
improvement exists; the current bounded search and full cost policy found none.
The scan did not hit its budget at these two calls.

A read-only inspection of the actual ECJPAKE caller graph identifies a further
SDK boundary. Its sole output contains `SHIFT_LEFT` with a primitive `INT`
distance. The pinned SDK's
[ModularBridge](https://github.com/carstenartur/Regelsuche/blob/ae1c39ea50cff685fe843b0357c818f38db6ec79/regelsuche-optimization-sdk/src/main/java/de/regelsuche/sdk/optimization/ModularBridge.java)
tries to translate complete outputs; it cannot translate that shift and excludes
the whole output from modular proposal generation. The installed SDK reports
`MODULAR_BIG_INTEGER_REQUIRED` and zero modular proposals for this original
output. No target expression or different budget was supplied to this probe.
This identifies a translation limitation; it does not show that guard costs
erased a useful candidate, or that supporting the shift alone will produce an
accepted improvement. The SDK is unchanged by this adapter PR.

Eight other previously unsupported call sites now reach a more specific
field, expression/effect or control-flow rejection. Their locations and
diagnostics are recorded in the receipt. Those boundaries have not been relaxed
to increase the accepted count.

## Behavioral qualification

The new `BigIntegerMethodExpansionTest` has fourteen tests. The initial eleven
tests produce seven positive-case failures and four passing rejection cases on
unchanged baseline code. All eleven pass after the adapter extension. Independent
review then identified two admission-policy gaps in the factory-literal shortcut;
the added field-reference and nested-constant regressions fail before their fix.
After that fix all fourteen tests pass, including retained signed-literal support.
The complete local standalone mathematics suite passes **194 tests, zero failures,
errors or skips**, versus 180 tests on the baseline.

Tests compile and execute actual original/generated Java, compare numerical
results and exceptions, check source parsing, second-pass stability and byte-exact
Undo, and exercise nulls, subclass dispatch, signed values, invalid moduli and
oversized inputs. Separate cases check nested/repeated helpers, overload binding,
local reassignment, caller-scoped guards, unused arguments and dead callee
operations, selected numeric kinds, strict opt-out and expansion limits.
Both modular-power simplification and ordinary algebraic cancellation across a
helper boundary produce accepted replacements in these regression fixtures.

The standalone harness excludes the existing OSGi-only
`BouncyCastleRuntimeCorpusTest`; the repository's normal Eclipse/Tycho and native
UI CI gates remain separate. Final local build output uses an isolated temporary
directory after synchronization staging files interfered with test discovery;
source paths, dependencies, test inclusion and exclusion scope are unchanged.
Scan durations measure the analysis process, not
generated-program speed. No whole-library execution or runtime-speedup claim is
made by this comparison.

## Reproduction

Use the existing `org.sandbox.math.qa.BigIntegerCorpusScan` from
`qa/math-optimization/src/main/java` with the matching binary archive, source
archive, a fresh output directory and `strict` or `mathematical` as its four
arguments. Run both modes against the baseline and this revision using the same
classpath and settings. It emits summary and diagnostic data, accepted
before/after regions, complete generated classes, and verifies exact Undo.
The receipt pins the inputs and normalized output manifests; raw copies of the
complete upstream archive and generated sources are not duplicated here.
