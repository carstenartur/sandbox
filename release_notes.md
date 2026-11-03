# Sandbox 1.3.8 — proposed release notes

Status: draft for review; publication remains held pending final qualification and acceptance.
Baseline: [1.3.7](https://github.com/carstenartur/sandbox/releases/tag/v1.3.7).
Candidate source: `ef946b6fad6276694f32389311cb7e71cf7f3955` (2026-11-03, after #1797).
The source includes #1798, the Linux/Windows native CI scope, mining source-evidence
admission from #1796 and the reusable upstream qualification infrastructure from
#1797. Only the release-preparation PR #1779 remains open and draft in this release
workstream. Release 1.3.8 has not been published.

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

Included through merged [#1798](https://github.com/carstenartur/sandbox/pull/1798).
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

The [#1798 MethodInfo experiment](https://github.com/carstenartur/sandbox/pull/1798)
compares original JDT, the earlier generated BigInteger form and the new primitive
form. For the four measured cases with 4,096, 4,097, 8,192 and 65,535
exception-table entries, the new form needs 36–53% less time than the earlier
generated form. Against original JDT, however, the
synthetic maximum of **65,535 handlers remains 21.1% slower**: 402.219 versus
332.144 ns/op, with separated 99.9% intervals. Every other original comparison,
including the real ClassFileReader control, has overlapping intervals. This is
neither a demonstrated general speedup nor a measurement of compiler throughput.

The final #1798 [BC SWU benchmark](https://github.com/carstenartur/sandbox/actions/runs/41605344693)
measures three transformed regions in a complete hash-to-curve call. **All eight
timing pairs are inconclusive.** Preparation/check work in the GC-profiler setup
prevents an isolated allocation-savings claim. This experiment does not measure
the nine separately qualified digest rewrites. Smaller digest-helper bytecode
does not itself establish faster execution.

The historical [finite-series qualification](qa/math-optimization/results/2026-10-31-finite-series/README.md)
used SDK source `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef`; it is not a fresh
measurement of the current SDK. Its whole-call JMH run shows separated 99.9% intervals for
five specific large-input pairs on its recorded environment. Small-input results
are mostly inconclusive; this is not a general speedup guarantee or a newly tuned
crossover. Its results remain attributed to that earlier artifact and environment.
Accepted-region counts and cost estimates are not timing measurements or
constant-time qualification. Exact rational telescoping remains SDK-only because
Java integral division truncates.

## Release review

Main `ef946b6fad6276694f32389311cb7e71cf7f3955` has tree
`bc6ddc4aa0a8ede8951eda47715e4d5e057347df`, identical to tested QA head
`673c4a5928dfd0a22a3541c38c6fb14fdde5388d`. The completed
[QA Maven execution](https://github.com/carstenartur/sandbox/actions/runs/41697761523)
passed 6,662 test occurrences with 12 skips, including all 1,239 mathematics
cases; the separate Oomph workspace execution passed 38 tests.
[Metrics](https://github.com/carstenartur/sandbox/actions/runs/41697761160)
recorded 6,869 passes and 12 skips, with LINE coverage **44,441/64,240 = 69.1796%**
above the unchanged 63% gate. The 25-module aggregate excludes the mathematics
plug-in, so this is not its coverage percentage. These overlapping reactor
counts are not additive. The fresh
[SWT comparison](https://github.com/carstenartur/sandbox/actions/runs/41697761447/job/126132695087)
passed 4,550 occurrences with 462 identical skips in each phase.

The 33 QA paths merged in #1797 change no runtime source from main `64235a3…`.
Linux and Windows distribution acceptance completed on #1796's identical runtime
tree; its atomic Help retry also passed. Push path filters therefore schedule no
new Maven/Oomph or distribution run for the QA-only merge. The actual new main
metrics, benchmark/SWT and security runs retain their own recorded states in
[the preparation handoff](docs/releases/1.3.8-preparation.md). The generic PR
benchmark is still running and supplies no new performance result.

The handoff preserves the historical source boundaries and provisioning failures;
later successful executions do not establish a permanent Oomph repair. Native
Eclipse consumer qualification remains Linux-only within the Linux/Windows matrix.
Release-preparation PR [#1779](https://github.com/carstenartur/sandbox/pull/1779)
remains draft while the canonical release dry run and stable-version,
product and release-evidence acceptance are outstanding.
The existing release workflow generates its own commit-log notes; this curated
draft must be explicitly incorporated in the publication review.
