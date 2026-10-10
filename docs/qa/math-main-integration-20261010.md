# General mathematics main-integration qualification — 10 October 2026

This record supersedes the integration-status statements in the earlier dated
`math-variable-repair-progress.md`; its historical results remain unchanged.
Issue #1657 is still open. Source-specific mathematical pre-solutions are not an
accepted implementation path.

## Integrated feature content

Feature source `1731722ffb5bfe852fbe09a26481a4dcdaae2c5a` combines the Java
adapter repairs, the general Regelsuche algebra path, integral source-helper
expansion and four runtime-variable Help examples. The former constant-only
Help examples are replaced, not offered under an alternate optimization mode.
The Java adapter supplies operations and their semantics, not a target answer.
The earlier constructor/halving-recognizer package remains withdrawn.

Regelsuche #1080 is merged to main. The pinned distribution identifies source
`409083a58ab28d14fb3ce02adb2938dd9b92eb5f` and JAR SHA-256
`a6a7b5668f95a8abc13ff87b455cca4a44b29a3503a67c25d445ea312ba53081`.
The receipts in `sandbox_math_cleanup/lib/` retain the original two-clean-build,
91-SDK-tests-per-build and two-fresh-consumer qualification. They are not a
public-release assertion.

## Normal CI diagnosis

On that exact feature source, native Help run **38028501714** passed. The
independent official Oomph workspace job in Maven run **38028501787** passed.
The downloaded `test-reports-8335` archive (artifact **11661930788**, SHA-256
`d53d84e391de87844b06d769abb7ad33933422edabcd3ddf0140b0bc271145ab`) contains
**4,785 tests, 1 failure, 0 errors and 11 skips**. The only failed assertion is
`RepositoryPolicyTest`: the 6,052-text-line, 82-file integration needs the
substantive `## Repository policy exception` section required by CONTRIBUTING.
The PR previously explained the combined review scope under a different heading.
Linux distribution artifact **11660904636** (SHA-256
`066792f9f95d580b641922f7238623ad85ecd2077abcf6408fb01a2bb47f4b99`)
contains the same repository-policy failure. Neither run qualifies the final
main integration.

The PR body now documents the exception using the existing policy. Much of the
size is the complete pinned upstream before/after source evidence, and the
runtime-only adapter and replacement gallery cannot be shipped independently
without an inconsistent Help contract. No policy threshold, mathematical
checker, image tolerance, coverage gate or test exclusion changed.

The policy reads the event's saved PR body through `GITHUB_EVENT_PATH`, not the
current remote description. Re-running the old event would re-read the old
body. This tracked qualification record creates a new ordinary PR event with
the corrected review context. Fresh complete Maven, coverage, native Help,
distribution and security results, plus normal merge eligibility, are required.
A metadata correction and a previously passed module test are not a main merge.

## Remaining work after integration

- Publish proof-derived explanation controls and source comments through the
  regular Sandbox UI/report path; do not revive specialized constructor rules.
- Preserve authored constant subexpressions granularly rather than rejecting
  an entire mixed runtime expression when its spelling would otherwise change.
- Extend genuinely general prerequisite reasoning and Java state/control-flow,
  helper and array contracts without identifying named demonstration tasks.
- Qualify independent production kernels and measure generated-code behavior
  and performance before proposing upstream changes. Nine bitwise rewrites and
  fewer operators alone do not establish a JIT-level or constant-time benefit.

The complete Bouncy Castle constructor without a specialized recognizer,
general loop-invariant discovery and the SWT performance contribution remain
acceptance goals, not capabilities implied by merging this integration.
