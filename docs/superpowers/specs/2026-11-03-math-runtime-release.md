# Mathematics runtime qualification and 1.3.8 completion

## Authorized outcome

The maintainer asked on 2026-11-03 to perform the reported remaining work:
investigate measured regressions, correct their causes, refresh the existing
release preparation, run the canonical release rehearsal, and complete release
1.3.8 after its acceptance gates pass. The request authorizes implementation,
ordinary PR integration, and publication; it does not waive verification.

## Starting evidence

- Main: `ef946b6fad6276694f32389311cb7e71cf7f3955`.
- Existing release PR #1779: `46abab7a67f9843e3ba0af244cd6fad44b366c52`.
- Main BC benchmark 41704548802: P384/32 takes 151.772 rather than
  147.820 microseconds; P521/32 takes 302.560 rather than 289.891 microseconds.
  Six other intervals overlap. The three SWU regions are distinct from the
  previously qualified nine digest changes.
- The latest recorded MethodInfo primitive output is 21.1% slower than original
  JDT at 65,535 synthetic handlers. JVM loop elimination is a hypothesis to test.

## Design and constraints

Keep mathematical equivalence and Java execution contracts unchanged. Establish
causes with complete-call comparisons, controls, and source identity before
altering selection or generation. Prefer source-relative magnitude accounting
for BigInteger and simpler equivalent primitive output when supported by the
evidence. A generic runtime safety ceiling is not a measured operand magnitude.
Do not blacklist upstream classes, curves, or benchmark inputs.

Keep the existing SDK boundary: general algebra belongs in Regelsuche; Java
source extraction, Java emission and Java-specific admission remain in Sandbox.
Do not update or release the SDK merely to change Java emission.

Use Java 25, Maven/JUnit, existing benchmark and native qualification workflows.
Do not weaken correctness assertions, time/work budgets, coverage gates, or
invent positive timing results. Keep original measurements alongside new ones.
Ordinary PRs target main, remain below the repository's change-size limit, and
must pass applicable checks and review before integration.

The existing release request push path publishes; it is not a rehearsal.
The canonical rehearsal requires dry_run=true and skip_tests=false. Missing
dispatch/authentication capability must be reported without replacing a dry-run
with publication or bypassing an approval restriction.

## Acceptance

1. Confirmed diagnoses and regression tests for every implemented correction.
2. Passing targeted tests and the applicable complete Maven/CI gates.
3. Fair fresh performance comparisons with identities and uncertainty retained.
4. Updated release notes and evidence, including any remaining measured limits.
5. Successful canonical stable-version rehearsal and product/assets review before
   release publication. No unperformed manual action is claimed as completed.
