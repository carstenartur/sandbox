# Mathematics optimization qualification — 2026-10-30

## Delivered behavior

- Source-aware ranking participates in the existing verified SDK frontier. It
  prices emitted operations, captures, guards and locals before selection, and
  separates original source occurrences, weighted scores and runtime estimates.
- Optional BigInteger estimates use source-specific magnitude bounds and
  intermediate growth. The generic 4096-bit safety ceiling remains a guard,
  rather than an assumed operand size. Unknown sizes retain structural costs.
- Whole straight-line primitive tasks can replace total right-hand sides across
  stores. Declarations, calls, reads, stores and local commits retain their order;
  no temporary is introduced. Generated Java is recompiled, re-extracted and
  independently verified against the original event schedule and value plan.
- Pure static source helpers can be summarized across files, including nonprivate
  helpers. Calls remain executed, preserving class initialization and exceptions.
  Request-local caches and source hashes reject stale dependency edits.
- Independent low-word polynomial and disjoint-bit proofs support generic masked
  arithmetic and carry-free word reconstruction. Wrong masks, overlapping carries
  and unsupported arithmetic remain unproved.
- Explanation work includes independent verification, derivation replay and
  presentation. Completed preparation remains charged when a phase fails.

## Design decisions and bounds

One metered ranking callback extends the existing search; there are no parallel
independent optimizers or new CAS process. Ordinary scalar regions keep the SDK
structural model. Source ranking is used for memory and BigInteger regions.
Generic 4096-bit bounds intentionally cannot trigger magnitude-specific ranking;
this can miss an improvement for operands actually known to have exactly that size.

Whole-task attempts use only the remainder after existing regions. Their complete
allocation is reserved in four shares: search, source realization/re-extraction,
verification and explanation. Source discovery has the explicit remaining cap;
reported completed-work overruns additionally debit the file allowance.

Array-region search reserves one third of its existing allowance for source costs
and explanation. SDK search reserves the known final preparation cost. Requests
with literal word masks use one third of the remainder for exploration and two
thirds for final checking; other requests retain the existing half/half split.
File budgets, corpus profiles and source archives are unchanged. Literal
normalization and accepted child congruences are cached only within one independent
proof; separate verification and replay start fresh. Low-word proposals remain
independent of outer Boolean rewrites, whose identities are checked first.

All transformations still require typed independent proof and source compilation.
Loops, control-flow joins, virtual dispatch, mutable helper state and unavailable
source remain conservative boundaries. This is a bounded source optimizer, not a
general whole-program optimizer or an implementation of complete Nat256 addition.

## Verification

Bases: Sandbox `6212f23864e3e2f1be5515d37664df4ee992c1f2` and Regelsuche
`7cb836ec3b7ec1f326949ab8b78f2ce3979d572f`. Toolchain: Temurin 25.0.5+8 and Maven
3.9.16; native plugin packaging uses the repository's Tycho 5.0.4 target.
The SDK source revision and exact JAR SHA-256 are recorded in
[`qualification.json`](../../../sandbox_math_cleanup/lib/qualification.json).
The receipt comes unchanged from the existing clean-tree SDK packager and its
fresh external consumer, which checks optimization, replay and checked overflow.

Integration completed against merged Regelsuche main
`2dbf8e4a11e2aabc78e5d5c51c10cfe23d8a86a3`, including the final review changes
from PR #1126. The fresh dependency reactor passes 2,510 tests, including 227 SDK
tests, with no failures, errors or skips. The
[native SDK qualification](https://github.com/carstenartur/sandbox/actions/runs/41171211843)
passes two clean builds and two isolated external consumers; all ten distribution
files match byte for byte. Its unchanged
[reproducibility receipt](../../../sandbox_math_cleanup/lib/reproducibility-2026-10-31.json)
records the source tree, test count and successful comparisons.

Sandbox embeds the native-qualified JAR, SHA-256
`5160ddecb4c4d4d521676d37c4dc639ae9b81f17fa37b6eaa3eead70f6a46efa`.
Every class and runtime resource is byte-identical to the locally rebuilt SDK
used for the fresh 568-test suite and 120/129 corpus scans; only the archive-input
provenance JSON differs. All five screenshot fixture SDK pins identify these
native-qualified bytes. Original captured screenshot provenance is preserved.

Baseline: 210 SDK tests; 560 headless consumer tests, with one optional historical
reference test skipped. Both baseline corpus scans parsed 3,126 BC 1.85.2 files
with ten parse-error files; strict accepted 117 regions and mathematical accepted
126. These counts are coverage evidence, not speed measurements.

| Gate | Result |
| --- | --- |
| Complete SDK dependency reactor | 2,510 tests, including 227 SDK tests; no failures/errors/skips |
| Native reproducible SDK distribution | Two clean builds, 227 SDK tests/build, two fresh consumers, ten identical files |
| Complete standalone consumer suite | 568 tests; no failures/errors; one existing optional historical skip |
| Native mathematics plugin package | Eclipse compilation and SpotBugs pass; zero SpotBugs findings |
| Repository policy | All 12 tests pass; both changes remain below the 1,500-text-line limit |
| Matched BC strict scan | 120 accepted regions, previously 117; 3,126 parsed files and ten parse-error files |
| Matched BC mathematical scan | 129 accepted regions, previously 126; 3,126 parsed files and ten parse-error files |

Commands: `mvn -Psdk-release -pl regelsuche-optimization-sdk -am install` in
Regelsuche; `mvn -f qa/math-optimization/pom.xml -Pbc-corpus clean verify`,
`mvn -pl sandbox_math_cleanup -am -DskipTests package`, and
`mvn -pl sandbox_common_test -am '-Dtest=RepositoryPolicy*Test,PinnedGitRepositoryTest'
-Dsurefire.failIfNoSpecifiedTests=false -Drepository.policy.base=refs/remotes/origin/main verify`
in Sandbox. The SDK packager's fresh external consumer also passes.

Both scans retain the exact baseline binary/source/profile hashes and the same
per-file parse statuses. Gains occur in ECDomainParameters, Faest and FalconVrfy.
Coverage is not a strict superset: Hawk's previous spans `(112211,137)` and
`(128027,1051)` exceed their current fully accounted budgets. A smaller retry at
`(128239,839)` and a new region at `(64177,246)` replace them, leaving Hawk's region
count unchanged. These remaining opportunities are recorded rather than hidden by
larger budgets or reduced acceptance thresholds.

Regression coverage includes compiled original/generated comparisons, overflow,
null/short arrays, aliasing, exception-visible local state, overwritten inputs,
comments, exact Undo, cross-class initialization failure, source staleness, unknown
magnitudes, cost ordering, cancellation and complete shared work accounting.
The four public hash-to-curve call paths are compared using separately compiled
original/generated classes. Existing BC retained-region minimums remain intact.

## Review and remaining integration

One independent whole-branch reviewer found no Critical arithmetic or source-effect
defect. Two Important findings were reproduced and fixed: lost completed preparation
work on explanation failure, and a fresh source-discovery budget during task
re-extraction. A Minor finding is deferred: extreme direct-API magnitude estimates
can overflow the consumer's weighted score and fail closed, losing an optimization.
This does not admit unproved generated code.

At PR publication, native Eclipse UI/workspace tests and remote CI are the final
integration gates; their results and fresh JMH measurements are recorded in the PR.
This source-pinned report makes no measured speedup claim. Historical native receipts and
screenshots retain their original provenance and are not attributed to this SDK.
The Sandbox PR is based directly on main; the prerequisite Regelsuche PR #1126
is already merged. No stacked PR is required.
Publication is explicitly authorized. The GitHub connector supplies the publication
commits because the local environment has no Git push credentials. The distribution is rebuilt and qualified against the final merged upstream source.
An independent integration review found no Critical or Important defect and passed
252 mixed-width/shift cases: 126 valid identities verified and 126 incorrect-mask
variants rejected. The upstream change is [Regelsuche PR #1126](https://github.com/carstenartur/Regelsuche/pull/1126).
