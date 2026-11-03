# SWU BigInteger operand-growth diagnosis, 3 November 2026

## Finding and limits

The first of three ordinary Apply regions in the pinned Bouncy Castle SWU class
postpones reductions through a multiplication chain. Its generated arithmetic is
measurably slower in the isolated first-region experiment. Restoring those
reductions while retaining square lowering removes that isolated regression.
The complete public-call experiment is inconclusive: every 99.9% interval,
including the original/original controls, overlaps. The isolated result does not
establish the cause or magnitude of the earlier complete-call CI differences.

This measures the SWU class, not the separately qualified nine digest edits,
whole-library throughput, peak heap, constant-time execution or forced fallbacks.

## Inputs and reproducibility

The source fixtures come from `bc-math-qualification` in run 41697761447, product
source `673c4a5928dfd0a22a3541c38c6fb14fdde5388d` (same tree as main
`ef946b6fad6276694f32389311cb7e71cf7f3955`). The file is
`org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java`.
The restored variant is a diagnostic edit, **not** ordinary Apply output.

| Fixture or raw result | SHA-256 |
| --- | --- |
| Original complete source | `6cd8fbd459615e6936bd8eeea7413ea6a85dc91e6dd558de7d6c118c4d523119` |
| Prior generated complete source | `ccee313d5ac7de33a73ef255358bccec6f9b31ac29e2c82e224c391a1d0507bd` |
| First-region reductions restored | `7ee340ff109859bd9b5a4c1d065ce21a1d3b83b9cd79c6ff172db4c2b7dbb8e4` |
| Complete-call JMH JSON | `c065d5d87786feccf804ee619ff5785c3ba9fcffc3a81cf999e6cdb5a39a6360` |
| Isolated-region JMH JSON | `38113899603c9964240d8c2ee6575957e2d7255bd427f8d9b85269813f4fcbbe` |

The raw evidence directory retains full sources, `SwuDiagnosticBenchmark.java`,
`SwuRegionBenchmark.java`, `OperandProbe.java`, JSON, logs and exact argument
lists in `command.txt` and `region-command.txt`. Both JMH runs use Temurin
25.0.5+8, JMH 1.37, `-Xms512m -Xmx512m`, one thread, two forks per case, three
500 ms warmups and five 500 ms measurements per fork; no profiler is enabled.
These scratch diagnostic programs are distinct from the reusable qualification
harness. Both runs complete all eight cases and sixteen forks.

The complete-call harness separately compiles all four namespaces each trial
with `--release 17` and the same BC 1.85.2 JAR, public bridge and domain string.
It checks encoded outputs for 64 fixed 32-byte messages in all variants before
timing. Original A/B use identical source in separate namespaces. Each operation
returns the complete `hashToCurve(message).getEncoded(false)` result to JMH.
Class loading, compilation and equality checks occur outside primary timing.
The setup invokes original A once additionally as the equality reference; the
A/B controls and ordinary warmup are retained rather than claiming perfect
namespace or environment equivalence.

## Complete-call results

All values are microseconds per complete call, mean plus/minus JMH's 99.9% error.

| Curve | Original A | Prior generated | First-region reductions restored | Original B |
| --- | ---: | ---: | ---: | ---: |
| P384 / 32 bytes | 160.401 ± 15.551 | 161.097 ± 15.009 | 161.931 ± 14.935 | 174.729 ± 24.634 |
| P521 / 32 bytes | 331.566 ± 19.186 | 325.151 ± 22.455 | 310.830 ± 16.437 | 313.985 ± 13.045 |

The broad intervals and A/A spread preclude either a speedup or a regression
claim for this local complete-call cohort. They do not negate the separately
retained CI observations on their own machines and exact runs.

## Operand sizes and isolated arithmetic

The operand probe uses the actual hash-to-field outputs for the same 64 messages
(two field inputs each), pinned curve parameters and domain string. All three
first-region outputs agree between original, generated and restored forms.

| Curve | Modulus bits | z | Largest reduced tv1 | Unreduced tv1 | Unreduced tv2 | Final unreduced B product |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| P256 | 256 | -10 | 256 | 516 | 1,031 | 1,286 |
| P384 | 384 | -12 | 384 | 772 | 1,544 | 1,927 |
| P521 | 521 | -4 | 521 | 1,044 | 2,088 | 2,607 |
| BLS12-381 G1 | 381 | 11 | 381 | 765 | 1,530 | 1,532 |

The isolated benchmark consumes these actual inputs, returns all three results,
and includes the generated receiver/magnitude guards. It changes only the first
region; its numbers are not whole-call performance measurements. Values are
nanoseconds per first-region evaluation, with 99.9% JMH errors.

| Curve | Original A | Prior generated | Reductions restored | Original B |
| --- | ---: | ---: | ---: | ---: |
| P384 | 2,308.691 ± 338.497 | 4,063.594 ± 361.018 | 1,813.767 ± 187.432 | 2,268.276 ± 205.678 |
| P521 | 3,615.100 ± 186.427 | 6,353.907 ± 305.402 | 3,080.342 ± 326.179 | 3,768.537 ± 432.127 |

In both curves the generated interval is entirely above both original controls
and the restored variant. The original controls overlap. A lower scalar call
count (the prior explanation says 104 to 36 operation units) concealed larger
multiplication operands and division dividends. The experiment supports fixing
that cost-selection deficiency without blacklisting a curve or input.

## Java source ranking correction

For unknown BigInteger sizes, retain the existing structural source score and
use the existing SDK estimator only as a **relative upper-envelope** comparison
under the already recorded source/receiver bounds. Only when the SDK candidate
envelope increases **and** the candidate has fewer authored modular boundaries,
raise candidate operation work to at least `ceil(sourceStructuralWork * candidateEnvelope / sourceEnvelope)`.
This is an advisory ranking heuristic; a generic 4,096-bit safety ceiling is
neither a measured nor a typical operand size, and the ratio is not a runtime
or universal-distribution guarantee. Tighter source-specific magnitude ranking
and readability selection retain their established behavior.

The SDK still performs magnitude propagation, algebra, proof and candidate
construction. The Java adapter adds no algebraic rewrite or value-range premise.
SDK inspection work and both boundary-count traversals are charged to the shared
search budget. The source reference and source boundary count are retained once;
initialization invalidates any earlier candidate cache. Source boundaries are
counted at typed BigInteger roots of actual source-trace occurrences; candidate
boundaries at typed prepared DAG nodes. MOD, REMAINDER, MOD_POW, MOD_MULTIPLY and
MOD_INVERSE each contribute one bounded modular result. Nested receiver values,
alias reads and implicit internal modPow reductions are not counted again.

This is a narrow advisory reduction-removal admission check, not a universal
cost bound or a proof that every reduction stays in place. Equal total counts
can conceal moved boundaries or a new reduction elsewhere. DAG counting can
conservatively reject sharing or inlined forms. Direct modular-power-to-reduced-
square lowering retains one boundary and its established admission, including
small constant moduli. Independent value proof and generated-source checks
continue to govern correctness.
Normalization uses bounded exact arithmetic and saturates without signed-long
overflow. Empty SDK estimates preserve the structural fallback. The SDK currently calls the source-ranking callback during final
ranked-cost assessment after search. Consequently this correction can reject the selected
large incumbent and let the existing Java-region decomposition retain smaller
useful forms; it does not steer all SDK search steps. Fresh ordinary Apply
output and complete tests remain separate qualification gates.

## Fresh ordinary Apply

The unchanged mathematical profile scans the complete retained SWU file with
one million work units, 20,000 states and the existing five-second timeout.
It accepts four regions, with zero parse errors. Source offsets and lengths are
`2441:52`, `2545:54`, `2868:43`, `2920:103`. Respectively these lower the square
of `u`, `tv1`, `tv3`, and `tv4`; the last region also retains the adjacent
`A.multiply(tv6).mod(p)` assignment. Each square is immediately reduced modulo
`p`. The original `z.multiply(tv1).mod(p)` and `tv2.add(tv1).mod(p)` barriers
remain outside those edits. No unreduced first-window squaring is emitted.

The fresh complete generated source has SHA-256
`19991dde0a9db01846b10eb142050afdbba690176c95b65f1c72649f7d1dde12`.
It differs from the manual reduction-restored diagnostic variant, including four guarded regions rather than three. Neither local timing
table is a timing result for this final source. Its exact full source, patch,
proof/evaluation receipts and compiler options are retained in `final-apply`.
The pinned binary SHA-256 is
`986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5`; the source JAR SHA-256 is
`b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`.


## Executed checks and rejected intermediate rule

The original main ranking regression failed as intended: the postponed-reduction
candidate scored 129 against source 249 despite the larger squaring operands.
The final five ranking tests cover source-relative growth, a guarded useful
square, source-callback cache invalidation, the unchanged safety-ceiling contract
and overflow-safe normalization. The temporary-style fixture records each
intermediate once through its prepared DAG, rather than falsely counting nested
expressions as repeated authored evaluations.

An intermediate rule also normalized candidates whose SDK envelopes improved.
That erased receiver-guard margins and caused six of seven focused BC tests to
fail, including a zero-change SWU scan. Those failures are retained. Restricting
the floor to an actual SDK-envelope increase restored the focused BC support;
the complete suite subsequently exposed the constant-modulus cases described
below. No budget or expected behavioral result was
relaxed. The updated SWU assertion names all four actual source regions and
requires the original modular barriers; full compile, Apply/Undo, reapplication,
point-result comparisons and contract/comment checks remain active.

Initial focused Maven verification passed **12 tests, zero failures/errors/skips**:
`JavaCandidateRankingTest` (5), `BouncyCastleBigIntegerTest` (5),
`BouncyCastleModularResidueTest` (1), and the actual four-public-call-path test in
`BouncyCastleHashToCurveBenchmarkTest` (1). The last test validates all four curve
profiles and 64 fixed messages per profile plus empty and 257-byte messages.
The separate final scan confirms the four source-bound edits above. These are
focused checks; the combined full mathematics suite and native CI remain
separate gates on the integrated change.

## Combined-suite follow-up

The first combined suite reported 1,227 executions, six failures, zero errors and
nine skips. Three unchanged `MathematicalContractTest` cases exposed a real
admission regression for direct squares modulo 101: the generic ceiling made the
SDK model a much larger product than modPow's implicit base reduction. The new
rule is therefore limited to an actual loss of authored modular boundaries,
rather than penalizing every API change with a larger envelope. A new ranking
regression failed first with source score 371 versus candidate 166,409 and then
passed after this refinement. The three constant-modulus behavior tests remain
unchanged, including initialization exceptions and incoming assignments.

The other failures were two declaration-scope cases and the former SWU projection
expectation, all of which required a joint rewrite now deliberately rejected for
losing an early reduction. A scratch probe confirmed the rejected joint candidate
was `(x*x)*(x*x).mod(m)`, with source SDK work 459,904 and candidate work 591,872. The scope fixture now
uses the supported joint `result=result.add(result)` after the modular square;
its emitted temporary remains reduced. The original two-square program is a
separate test requiring safe decomposition and preservation of the intermediate
modulus. Both programs retain the full before/after outcome matrix: null,
overridden modPow, zero, negative and over-4,096-bit receivers against null, zero,
negative and positive moduli. BigInteger, `var` and parameter forms all execute.
The SWU projection now requires the exact four verified regions and optimized
modulus-call counts `[1,1,1,2]`, retaining all four-curve output and null checks.

A fresh ordinary Apply after the boundary refinement again accepted four regions
with zero parse errors. Its complete source remains byte-identical to the hash
above (`19991dde...`); the receipt is retained in `boundary-refined-apply`. No new
performance measurement or changed work/state/time allowance accompanies this
refinement.

The follow-up focused Maven run passes **48 tests, zero failures/errors/skips**:
6 ranking, 20 helper-expansion/scope, all 15 unchanged mathematical-contract,
1 full SWU projection, 5 real BC BigInteger, and 1 four-profile public-call test.
The integration owner reruns the combined suite and native CI separately.
