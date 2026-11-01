# Sandbox 1.3.8 — proposed release notes

Status: draft for review; publication remains held pending final qualification and acceptance.
Baseline: [1.3.7](https://github.com/carstenartur/sandbox/releases/tag/v1.3.7).
Candidate source: `d06ffc85a56ec61968ac2eee28031924ddd3368a` (2026-11-01).

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

## Source-derived tail-state planning foundation

- Add a neutral bounded ULR state descriptor and source-derived inspection for a
  narrow static `int`/`long` direct-tail-recursion fragment, with at most eight
  states and a counter that decreases by one to the `counter <= 0` exit.
- Prepare a source-only loop plan after explicit `ALLOW_FRAME_ELISION` opt-in.
  Independent typed replay checks the emitted state transfers; next-state values
  use the old state in Java's left-to-right order before assignment.
- Retain source-snapshot Apply/Undo, cancellation and shared work limits. Removing
  frames changes observable stack/resource behavior, including possible
  `StackOverflowError`; the strict stack policy produces no plan.

Included through merged [#1783](https://github.com/carstenartur/sandbox/pull/1783).
This internal foundation registers no cleanup checkbox, default profile, Quick
Assist or save action, and does not yet provide native Converter/UI integration,
value-object summaries, affine/matrix extraction or mathematical iteration
replacement. See [the exact source contract](sandbox_math_cleanup/TAIL_RECURSION.md).

## Reliability and documentation

- Retry temporary Gemini overloads during nightly commit mining.
- Keep scheduled verification from cancelling active checks.
- Explain supported Sandbox workflows and refresh mathematics guidance with
  source-backed examples and explicit fragment boundaries.
- Update the qualified vendored value-arithmetic SDK and native mathematics Help
  provenance through merged [#1782](https://github.com/carstenartur/sandbox/pull/1782).
  This SDK prerequisite does not itself add later Java adapter features.

## Dependencies and qualification limits

The mathematics plug-in bundles a source-bound Regelsuche development distribution,
not the public Regelsuche 0.5.0 Maven release and not a floating snapshot:

- SDK source: `93f0f35abaea58ea492441776be7c9d2a9be1695`.
- Standalone JAR SHA-256: `5ad54860d828a738d4df8dba87659b8a14009bfb208f79705a4b8529b9c58884`.
- Receipt version: `0.5.1-issue1657-93f0f35abaea`; `publiclyPublished: false`.
- JGit storage remains the already released **0.11.8**.

The current local source-bound receipt records two clean builds, 302 SDK tests
per build, two fresh external consumers and ten byte-identical distribution files.
It is not a remote CI result or an Eclipse product qualification. Historical
finite-series receipts remain bound to their original SDK revisions.

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

These notes cover merged source only, including the counted-loop correctness fix
[#1776](https://github.com/carstenartur/sandbox/pull/1776) and the optional combined
mathematics/Functional Converter operation
[#1778](https://github.com/carstenartur/sandbox/pull/1778), the qualified SDK/Help
update [#1782](https://github.com/carstenartur/sandbox/pull/1782), and the internal
tail-state foundation [#1783](https://github.com/carstenartur/sandbox/pull/1783).
Main advanced after the original preparation; checks for the earlier source do
not qualify this expanded candidate. Verify the final same-SHA checks before
publication. Full release tests, the canonical dry run and manual acceptance
remain outstanding; see [the preparation handoff](docs/releases/1.3.8-preparation.md).
The existing release workflow generates its own commit-log notes; this curated
draft must be explicitly incorporated in the publication review.
