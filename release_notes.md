# Sandbox 1.3.8 — release notes

Baseline: [1.3.7](https://github.com/carstenartur/sandbox/releases/tag/v1.3.7).
This release expands verified mathematics transformations, improves generated
Java and cleanup coordination, and strengthens mining example verification and
review lifecycle handling. The supported fragments, separate opt-in contracts
and measured qualification limits are described below.

## Mathematics cleanup

- Expand verified Java computations across expression/block lambdas, reducer bodies,
  conditional/switch arms, exact-dispatch source calls and supported primitive SAM
  or method-reference contexts.
- Inspect numeric iterations through the shared ULR core, compose adjacent
  sequential integral stream maps, and hoist an admitted total invariant primitive
  computation with separate scheduling evidence.
- Preserve ordered array/field/accessor reads, source bindings, exception behavior
  and remaining file budgets; retain exact Apply/Undo and generated-source checks.
- Export typed computation tasks and consume independently verified high/low-word
  relations from the pinned Regelsuche SDK.
- Add verified finite-series loop and sequential stream reductions: polynomial,
  affine, geometric/product and bounded coupled, nested, periodic or piecewise
  fragments. Runtime templates apply under the estimated-runtime goal and strict
  Preserve Java policy. Guards and original-code fallbacks remain where required;
  eligible exclusive unit-stride constant sums now use primitive arithmetic directly.
- Avoid BigInteger setup before the original short-range path for admitted
  primitive int/unit-stride ranges, while retaining large-range safety guards.
- Account for increased BigInteger operand sizes when ranking candidates that
  remove authored modular-reduction boundaries. Preserve immediate reductions
  in the admitted SWU square rewrites instead of preferring larger unreduced
  intermediates. Direct immediately reduced squares remain eligible; this
  conservative ranking adjustment does not replace the independent proof gates.

See merged [#1767](https://github.com/carstenartur/sandbox/pull/1767),
[#1769](https://github.com/carstenartur/sandbox/pull/1769),
[#1771](https://github.com/carstenartur/sandbox/pull/1771),
[#1772](https://github.com/carstenartur/sandbox/pull/1772),
[#1774](https://github.com/carstenartur/sandbox/pull/1774) and
[#1777](https://github.com/carstenartur/sandbox/pull/1777).

## Generated Java and explanations

- Emit compact primitive calculations for independently proved constant-increment
  sums where the Java context permits them. Exclusive unit-stride `int` headers
  need no BigInteger work or retained fallback; other supported headers keep
  termination guards and the original fallback.
- Keep admitted dead-counter `int` accumulator sums in Java's 32-bit arithmetic:
  compare signed endpoints before subtracting, then calculate the count residue
  and constant product as `int`. This preserves overflow even across the full
  signed-int endpoint domain. Live counters, `long` accumulators and other
  supported headers retain their existing guarded templates. Reapplication
  recognizes the emitted conditional form.
- Reject generated Java errors and new warnings using the project's compiler
  options. Existing warnings are accepted only on matching, unchanged source
  ranges; equal warning totals alone are insufficient.
- Remove redundant casts and unused generated work where evaluation order,
  exceptions and Java expression types remain correct. Use `BigInteger.valueOf`
  for constants that fit a signed `long`.
- Explain runtime transformations under the existing `NONE`, `NONTRIVIAL` and
  `ALL` comment modes. Comments name the targets, arithmetic contract and actual
  guards, and distinguish estimated work from measured timing.
- Preserve recognized generated calculations and their fallback loops on a
  second cleanup. Retain counted-tail acceleration with independently replayed
  parameter binding and initialization.

Included through [#1798](https://github.com/carstenartur/sandbox/pull/1798) and
the operand-cost/int-width corrections in
[#1800](https://github.com/carstenartur/sandbox/pull/1800).
These output-quality improvements do not establish a general runtime speedup;
the measured limits below remain part of the release assessment.

## Functional Converter correctness

- Preserve Java semantics in counted-loop conversion: reject inclusive endpoint
  overflow and unstable/effectful bounds, preserve initializer/body AST and comments,
  and decline unsafe counter widths, counter writes and mutable captures.
- Respect checked exceptions (including resource close) and lexical stream-name
  resolution; retain native preview/apply/undo and compiled/executed regressions.

Included through merged [#1776](https://github.com/carstenartur/sandbox/pull/1776).

## Combined mathematics and Functional Converter cleanup

- Add an optional whole-file operation that runs mathematics before converting
  remaining eligible loops using the selected Functional Converter profile.
  It is off by default and requires Preserve Java behavior with Mathematical mode off.
- Present the combined result as one file preview, one selectable change group
  and one Undo; preparing a preview does not edit the editor buffer.
- Share work and time budgets across the phases. Cancellation or exhaustion
  discards the private result; changes to source, project settings or either
  effective cleanup profile invalidate a prepared proposal.
- Keep Quick Assist scoped to its mathematics selection and preserve the ordinary
  Functional Converter's independent operation. The option adds no save action.

Included through merged [#1778](https://github.com/carstenartur/sandbox/pull/1778),
including the correction that keeps previews aligned with live profile options.

## Explicit native tail-recursion cleanup

- Convert the admitted static `int`/`long` countdown fragment only after the
  separate, default-off tail-recursion consent. The emitted method records
  `ALLOW_FRAME_ELISION`: removing frames can change stack observation and
  `StackOverflowError`.
- Derive bounded state transitions from Java source and independently replay the
  generated loop. Next-state expressions keep the old state and Java evaluation
  order; source/AST/comment freshness is checked.
- Use ordinary native Preview/Apply/Undo with one private source and shared work
  and time budgets. Protect transferred tail bodies from unrelated mathematics
  and optional Functional Converter edits, including repeated Apply.
- Keep Select All, selection Quick Assist and save actions from granting consent.
  Existing finite-series/affine acceleration additionally requires Preserve Java,
  Mathematical mode off and Lower estimated runtime. Other admitted cases retain
  the verified ordinary loop.

This extends the foundation in [#1783](https://github.com/carstenartur/sandbox/pull/1783)
with [#1784](https://github.com/carstenartur/sandbox/pull/1784) and the native
integration in [#1792](https://github.com/carstenartur/sandbox/pull/1792).
General/mutual recursion, floating-point or value-object recursive state and
arbitrary control flow remain outside the supported fragment. SDK70's
OrderedMatrixChain rules have no new general Java consumer in this adapter.
See [the source contract](sandbox_math_cleanup/TAIL_RECURSION.md).

## Source-proved value arithmetic and refinements

- Add source-proved immutable-value arithmetic, fixed powers, value constants
  and factory residues in the admitted mathematical fragments.
- Require the separate explicit audited-runtime consent for detached value reuse.
  Verify inverse reuse and reject effectful speculative predicates.
- Preserve SWT source intent, exact source spans and shared preparation budgets.
  Correct tail-state identity limits and local-class method lookup.

Included through [#1784](https://github.com/carstenartur/sandbox/pull/1784),
[#1785](https://github.com/carstenartur/sandbox/pull/1785),
[#1786](https://github.com/carstenartur/sandbox/pull/1786),
[#1787](https://github.com/carstenartur/sandbox/pull/1787),
[#1788](https://github.com/carstenartur/sandbox/pull/1788),
[#1789](https://github.com/carstenartur/sandbox/pull/1789) and
[#1790](https://github.com/carstenartur/sandbox/pull/1790).
These bounded proofs and lower estimated costs are not measured speedup or
cryptographic constant-time claims.

## Reliability and documentation

- Retry temporary Gemini overloads during nightly commit mining.
- Keep scheduled verification from cancelling active checks.
- Preserve stable-tree release identity across Maven clean and include the
  curated release notes in the canonical dry-run evidence. Publication uses the
  same reviewed notes asset; empty or wrong-version curated notes fail closed.
- Keep the CLI's embedded `VERSION` value aligned with its archive version in
  stable and development builds. Include both ZIP and TAR.GZ distributions in
  the pre-publication evidence for inspection.
- Retain reviewed DSL rejections when mining rediscovers the same evidence,
  through merged [#1795](https://github.com/carstenartur/sandbox/pull/1795).
- Check source evidence before admitting mined cleanup candidates, within the
  existing Gemini request budget, through merged
  [#1796](https://github.com/carstenartur/sandbox/pull/1796).
- Add reusable native SWT/JDT before-and-after qualification with source-bound
  Apply evidence, exact test inventories and retained raw reports, through merged
  [#1797](https://github.com/carstenartur/sandbox/pull/1797). Historical receipts
  retain their original tested sources and measurement limits.
- Explain supported Sandbox workflows and refresh mathematics guidance with
  source-backed examples and explicit fragment boundaries.
- Refresh the Bouncy Castle Help example from the actual registered-cleanup
  preview and native capture. It shows four immediately reduced square regions,
  retains the surrounding modular reductions, and keeps paired source/adapter/
  screenshot provenance. The visible first result is
  `tv1 = u.multiply(u).mod(p);`.
- Update the qualified vendored value-arithmetic SDK and native mathematics Help
  provenance through merged [#1782](https://github.com/carstenartur/sandbox/pull/1782).
  Later adapter and SDK70 integration is included through #1784–#1792.
- Include the Fp12 algebra-proof timeout correction in
  [#1791](https://github.com/carstenartur/sandbox/pull/1791), and current native
  mathematics Help provenance from #1792.
- Skip unnecessary liveness traversal when a mathematics region has no local
  outputs. Merged [#1794](https://github.com/carstenartur/sandbox/pull/1794) also
  limits native distribution CI to Linux and Windows. macOS archives remain
  assembled without current native macOS qualification. The scope change does
  not establish that the historical macOS analysis timeouts were repaired.
- Qualify complete BLS12-381 G2 and pairing Apply outputs with source-bound JMH,
  and bind preparation measurements and reporting to the same JVM. See the
  [complete-output qualification](https://github.com/carstenartur/sandbox/commit/88c0ba5d4127b208cdc7485dbf330f62cf69c62e)
  and [preparation-JVM correction](https://github.com/carstenartur/sandbox/commit/e3867ac14b45fcd880fe11ee81f43ea1575248d2).
  These bounded results do not qualify every BLS path or cryptographic constant-time behavior.

## Mining verification and review lifecycle

- Compile examples using the parsed public top-level type's actual filename;
  retain compiler, binding, exact-type guard and exactly-one-replacement checks.
  A filename correction alone does not make an invalid example ready.
- Report unresolved imports as unsuccessful verification that may reflect invalid
  source or unavailable dependencies. Give definite public-type filename, syntax
  and type errors priority; a dependency diagnostic establishes no semantic validity.
- Require JDK-only, compiling positive examples with exactly one intended match,
  no file metadata or dependency stubs, and explicit exact-type versus subtype
  guards in mining prompts. The existing model-request budget is unchanged.
- Accept recorded review decisions only from the trusted workflow account, with
  an exact leading candidate marker and current revision. Apply the same checks
  when avoiding duplicate review issues. Closed `not_planned` reviews remain
  rejected; other closed reviews remain historical without approval or promotion.
  Preserve terminal decisions and validate the complete snapshot before changes.

A sealed replay of **611 recorded candidates** confirmed the following outcomes:

| Outcome | Candidates |
| --- | ---: |
| Ready for human review on supplied examples | 19 |
| Other deterministic verification failures | 411 |
| Unresolved imports: invalid source or unavailable dependencies | 170 |
| Rejected | 9 |
| Historical closed review | 1 |
| Duplicate | 1 |

The 19 ready candidates pass their supplied examples; none is approved or
promoted, and none is one of the five specifically investigated failures. All
five remain blocked. The 581 unsuccessful verifications remain failed; the
170 unresolved-import outcomes establish neither source validity nor semantic
equivalence. These are recorded replay results, not a count of production rules
or a claim that a public candidate-data report has been updated.

## Dependencies and qualification limits

The mathematics plug-in bundles the exact source-bound Regelsuche SDK70
development distribution:

- SDK source: `70c13fb580062b1fd653f35b81b2fb7ed1903c28`.
- Standalone JAR SHA-256: `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`.
- Receipt version: `0.5.2-issue1657-70c13fb58006`; `publiclyPublished: false`.
- JGit storage remains the already released **0.11.8**.

The exact artifact and unchanged generated qualification were transferred from
staging commit `1573e539f14313409ed5e16e1d80ea95b97493b5`.
The retained external-consumer output is historical qualification of that
artifact, not a fresh test of the reconstructed Java/native adapter.
The earlier 302-tests-per-build double-build receipt belongs to SDK source
`93f0f35abaea58ea492441776be7c9d2a9be1695`, not SDK70. No missing
SDK70 double-build receipt or test count is inferred.
See [the current SDK provenance](sandbox_math_cleanup/lib/README.md).

The SDK is included in the Eclipse bundle. Its numerical proof and reproducibility
receipts are separate from Java source admission and native product verification.
Generated Java does not require the SDK at runtime. A newer Regelsuche release is
not a prerequisite for shipping this exact qualified artifact.

## Measured performance and qualification limits

The historical [#1798 MethodInfo experiment](https://github.com/carstenartur/sandbox/pull/1798)
compared original JDT, generated BigInteger and an earlier primitive form. In its
four measured cases with 4,096, 4,097, 8,192 and 65,535 exception-table entries,
that primitive form needed 36–53% less time than the generated BigInteger form.
Its synthetic 65,535-handler case was **21.1% slower than original JDT**:
402.219 versus 332.144 ns/op, with separated 99.9% intervals. That observation
belongs to its recorded output and environment. Later controlled full-call
diagnostics did not reproduce the regression: all quiet comparisons against
original overlapped, including native/recompiled controls. Neither result
establishes the timing of the final int-ring output, a general speedup or
compiler-throughput improvement. Smaller bytecode alone is not timing evidence.

The final #1798 [BC SWU benchmark](https://github.com/carstenartur/sandbox/actions/runs/41605344693)
measures three transformed regions in a complete hash-to-curve call. **All eight
timing pairs are inconclusive.** Preparation/check work in the GC-profiler setup
prevents an isolated allocation-savings claim. This experiment does not measure
the nine separately qualified digest rewrites. Smaller digest-helper bytecode
does not itself establish faster execution.

A separate [QA benchmark completed on 3 November](https://github.com/carstenartur/sandbox/actions/runs/41697761447)
records **seven inconclusive BC timing pairs and one slower generated case**:
`P384_XMD_SHA_384` with 32-byte input needs **2.0645% more time**, with separated
99.9% intervals. This observation applies to that run and does not establish a
general speedup. The [preparation handoff](docs/releases/1.3.8-preparation.md)
retains the exact timings, test scope and historical performance alerts.

The current [#1800 BC execution](https://github.com/carstenartur/sandbox/actions/runs/41777104714/job/126399895785)
measures the ordinary four-region SWU output. Of eight complete-call timing
pairs, **three have lower generated times and five remain inconclusive**; none
has a separated higher-time interval. Two inconclusive cases nevertheless have
higher generated means. These results support only the three measured cases in
that environment; they do not establish a general gain, absence of regression
or cryptographic constant-time behavior. Earlier operand-growth diagnostics
used a different manual restored-reduction variant and retain their own limits.

The accompanying general benchmarks reproduce an unresolved performance
difference from the recorded main baseline: **40 of 48 cases exceed 1.15 times
the baseline time**, and all 48 recorded 99.9% intervals lie above baseline.
The measured source and build definitions are unchanged; the affected AST API
paths do not invoke the mathematics cleanup. Missing loaded-code/JIT evidence
and a matched same-machine control prevent a causal conclusion. Successful
benchmark execution does not clear these advisory observations. The similarly
named Sandbox MethodInfo benchmark does not time upstream JDT MethodInfo.

The historical [finite-series qualification](qa/math-optimization/results/2026-10-31-finite-series/README.md)
used SDK source `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef`; it is not a fresh
measurement of the current SDK. Its whole-call JMH run shows separated 99.9% intervals for
five specific large-input pairs on its recorded environment. Small-input results
are mostly inconclusive; this is not a general speedup guarantee or a newly tuned
crossover. Its results remain attributed to that earlier artifact and environment.
Accepted-region counts and cost estimates are not timing measurements or
constant-time qualification. Exact rational telescoping remains SDK-only because
Java integral division truncates.

## Qualification scope

The [qualification record](docs/releases/1.3.8-preparation.md) preserves exact
source, output, artifact and execution identities, including original failures,
explicit skips and later bounded successes. Reactor, metrics and upstream case
counts overlap and must not be added. The recorded 25-module coverage aggregate
excludes the mathematics plug-in; its percentage is not mathematics coverage.

Current JDT Core qualification covers all 1,626 sources, with five edits in four
files including the corrected int-width MethodInfo output. Both native phases
pass 290,650 executions without skips or failures, with verified reactor/compiler
class identities. This establishes the recorded transformed-output test scope,
without a MethodInfo timing or per-method coverage claim.

Current all-source JDT UI qualification covers 2,661 Java sources with **zero
accepted edits**: both phases have 9,423 passes and the same 154 skips, and all
5,233 compiled classes are unchanged. It qualifies the no-edit processing path,
not transformed UI methods or a combined transformed Core/UI installation.
BC corpus scans retain ten parse-error files. The historical native digest
receipt applies only to nine edits in five files, not the full changed corpus.

Native distribution qualification targets Linux and Windows. The additional
native Eclipse-consumer gate is Linux-only; assembled macOS archives do not
establish native macOS qualification. Pinned upstream tests, numerical proofs,
source-identity mappings and SDK receipts each retain their own scope. Earlier
reports apply to changed generated output only when the relevant identities
match; they are not fresh executions of a newer source or installation.
