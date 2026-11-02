# Sandbox 1.3.8 — proposed release notes

Status: draft for review; publication remains held pending final qualification and acceptance.
Baseline: [1.3.7](https://github.com/carstenartur/sandbox/releases/tag/v1.3.7).
Candidate source: `7458aa94907e7bbff5259fdeaf864fbb57a223c7` (2026-11-02).

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
  Preserve Java policy, with original-code fallbacks for short ranges or failed guards.
- Avoid BigInteger setup before the original short-range path for admitted
  primitive int/unit-stride ranges, while retaining large-range safety guards.

See merged [#1767](https://github.com/carstenartur/sandbox/pull/1767),
[#1769](https://github.com/carstenartur/sandbox/pull/1769),
[#1771](https://github.com/carstenartur/sandbox/pull/1771),
[#1772](https://github.com/carstenartur/sandbox/pull/1772),
[#1774](https://github.com/carstenartur/sandbox/pull/1774) and
[#1777](https://github.com/carstenartur/sandbox/pull/1777).

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
- Explain supported Sandbox workflows and refresh mathematics guidance with
  source-backed examples and explicit fragment boundaries.
- Update the qualified vendored value-arithmetic SDK and native mathematics Help
  provenance through merged [#1782](https://github.com/carstenartur/sandbox/pull/1782).
  Later adapter and SDK70 integration is included through #1784–#1792.
- Include the Fp12 algebra-proof timeout correction in
  [#1791](https://github.com/carstenartur/sandbox/pull/1791), and current native
  mathematics Help provenance from #1792. The latest macOS distribution still
  has a separate Hawk preparation-budget failure; full platform acceptance
  remains incomplete.

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

The historical [finite-series qualification](qa/math-optimization/results/2026-10-31-finite-series/README.md)
used SDK source `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef`; it is not a fresh
measurement of the current SDK. Its whole-call JMH run shows separated 99.9% intervals for
five specific large-input pairs on its recorded environment. Small-input results
are mostly inconclusive; this is not a general speedup guarantee or a newly tuned
crossover. All eight recorded complete Bouncy Castle hash-to-curve pairs remain
inconclusive. Accepted-region counts and cost estimates are not timing measurements
or constant-time qualification. Exact rational telescoping remains SDK-only because
Java integral division truncates.

## Release review

These notes cover merged source through
[#1792](https://github.com/carstenartur/sandbox/pull/1792).
Main advanced by nine commits after the previous preparation; the old PR checks
and SDK receipt cannot qualify the expanded source.

At the 2026-11-02 review, Core, Maven/Oomph, metrics, benchmark and Ubuntu/Windows
distribution checks passed on `7458aa94907e7bbff5259fdeaf864fbb57a223c7`.
The [macOS distribution job](https://github.com/carstenartur/sandbox/actions/runs/41505536028/job/125483768927)
failed in `BouncyCastleFinalPreparationBudgetTest.strictAcceptsBothHawkFragmentsWithinTheFullFileBudget`:
both original Hawk fragments lacked accepted replacements after `CANCELLED`.
The Math module recorded 1,204 tests, one failure and no errors or skips.
This is an unresolved gate; its root cause is not established by these notes.

Native Eclipse consumer acceptance ran successfully on Ubuntu; Windows skipped
that native step. Full platform/release tests, the canonical dry run and manual
product acceptance remain outstanding; see
[the preparation handoff](docs/releases/1.3.8-preparation.md).
The existing release workflow generates its own commit-log notes; this curated
draft must be explicitly incorporated in the publication review.
