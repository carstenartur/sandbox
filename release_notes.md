# Sandbox 1.3.8 — proposed release notes

Status: prepared for review; this version has not been published by this preparation.
Baseline: [1.3.7](https://github.com/carstenartur/sandbox/releases/tag/v1.3.7).
Candidate source: `b24f806f35dc1ca121f13ae13dfb61cad9ad2749` (2026-10-31).

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

## Reliability and documentation

- Retry temporary Gemini overloads during nightly commit mining.
- Keep scheduled verification from cancelling active checks.
- Explain supported Sandbox workflows and refresh mathematics guidance with
  source-backed examples and explicit fragment boundaries.

## Dependencies and qualification limits

The mathematics plug-in bundles a source-bound Regelsuche development distribution,
not the public Regelsuche 0.5.0 Maven release and not a floating snapshot:

- SDK source: `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef`.
- Standalone JAR SHA-256: `50076c27b7a0c483dd2299ae40cb529e057a58d8324f273f03d15a00f69e2527`.
- Receipt version: `0.5.1-issue1657-3a9250062f3f`; `publiclyPublished: false`.
- JGit storage remains the already released **0.11.8**.

The SDK is included in the Eclipse bundle. Its numerical proof and reproducibility
receipts are separate from Java source admission and native product verification.
Generated Java does not require the SDK at runtime. A newer Regelsuche release is
not a prerequisite for shipping this exact qualified artifact.

The recorded finite-series whole-call JMH run shows separated 99.9% intervals for
five specific large-input pairs on its recorded environment. Small-input results
are mostly inconclusive; this is not a general speedup guarantee or a newly tuned
crossover. All eight recorded complete Bouncy Castle hash-to-curve pairs remain
inconclusive. Accepted-region counts and cost estimates are not timing measurements
or constant-time qualification. Exact rational telescoping remains SDK-only because
Java integral division truncates.

## Release review

These notes cover merged source only, including the counted-loop correctness fix
[#1776](https://github.com/carstenartur/sandbox/pull/1776) and the optional combined
mathematics/Functional Converter operation
[#1778](https://github.com/carstenartur/sandbox/pull/1778).
Main advanced after the original preparation; checks for the earlier source do
not qualify this expanded candidate. Verify the final same-SHA checks before
publication; see [the preparation handoff](docs/releases/1.3.8-preparation.md).
The existing release workflow generates its own commit-log notes; this curated
draft must be explicitly incorporated in the publication review.
