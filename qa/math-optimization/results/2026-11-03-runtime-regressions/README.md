# Source-bound runtime regression investigation

These records belong to the corrections developed from main
`ef946b6fad6276694f32389311cb7e71cf7f3955` on 3 November 2026. They retain
unfavorable and inconclusive measurements as well as successful correctness tests.
They do not constitute stable-release or installed-product acceptance.

## Findings

The [BigInteger report](../../../../docs/qa/math-bigint-growth-20261103.md)
identifies delayed modular reductions in the first SWU region. Real field inputs
produce much larger multiplication operands and division dividends. An isolated
arithmetic experiment separates the prior generated form from the original and
restored-reduction controls. Complete public-call intervals overlap, including
visible original/original variation. No whole-call improvement is inferred.

The [MethodInfo report](../../../../docs/qa/methodinfo-int-ring-20261103.md)
qualifies an int-domain lowering for dead-counter, exclusive unit-stride constant
sums. It checks the full signed-int endpoint domain, 45 complete-reader variant
comparisons and unchanged output on reapplication. Both retained JMH cohorts
have overlapping intervals for all original comparisons. The historical 21.1%
native regression was not reproduced; its cause and resolution remain unproven.

The corrections use existing Java-source and SDK contracts. They add no
class/curve/input blacklist, new algebraic premise or relaxed search budget.

## Baseline CI

`baseline-bc-summary.json` transcribes all eight full-call comparisons from
[main run 41704548802](https://github.com/carstenartur/sandbox/actions/runs/41704548802/job/126156277224),
artifact 13268854121. The original ZIP digest is recorded in that summary.
Two prior-generated cases have separated intervals: P384/32 is 2.6733% slower
and P521/32 is 4.3701% slower; the other six are inconclusive. These are the
three opt-in SWU regions, independently of the earlier nine digest edits.

## Retention and reproduction

`methodinfo-int-ring-20261103.tar.gz` retains complete original/current/new
sources and classes, native compiler and byte-identical A/A controls, the
qualified compiler JAR, fixtures, production generation helper, commands,
raw JMH output and RED/GREEN Maven logs. Its SHA-256 is
`5cffe88a1164d8e6f389ed38d1663f1c7717102d1829a353b7dfeeebdd49037c`.
`methodinfo-receipt.json` records individual hashes and scope. The first cohort
had initial shared compilation load; the complete predeclared quiet repeat and
the original cohort are both retained.

The tracked report and receipt correct the archive's approximate 15-second
overlap estimate to the supported startup timestamps (approximately four to six
seconds); no raw timing result or archive byte was changed.

`bc-diagnostic-20261103.tar.gz` retains the full selected original/generated/
restored sources, JMH implementation and results, operand probes, all failed
intermediate rules, final ordinary Apply output, focused tests and raw baseline
CI JSON. SHA-256:
`3cb630f604bc37ce634af8686cf4fc27cd2ad86bbe798179fd72e2f08a840f6d`.
Only duplicate extracted `source-context` trees are omitted; input receipts
identify the exact upstream source archives and dependency hashes.

`combined-suite-red.tar.gz` retains all 137 JUnit XML reports and the full Maven
log from source `0f252d7ef3823e6aad2fcfa8df28264cf3f10145`: 1,227 cases,
six failures, zero errors, nine skips. SHA-256:
`efdc391975a9024274af888d9c48b8dc0da6617cd8f3274274ceebce80ff8753`.
The report explains the genuine constant-modulus admission regression and the
three obsolete expectations requiring removal of an early reduction. The new
rule also requires fewer authored modular boundaries; unchanged constant-modulus
tests and both old/new declaration-scope outcome matrices are preserved.

Final source `6b29610b9` passes Maven `clean verify`: **1,231 cases, 1,222 passed,
zero failures/errors and nine explicit native/upstream opt-in skips**.
`combined-suite-green.tar.gz` retains all 137 XML reports and the complete log;
its SHA-256 is `0ff8f1b6e8e2c0e824b60e7767e3678d9149d890241de2f11b06a6a0302a1e61`.
`verification.json` records the command, source/input identities, every skipped
case, the final independent review and the earlier failing run.

Exact source/output
identities, rather than a lower benchmark point estimate, determine which
qualification applies. Native CI and canonical release rehearsal remain
separate gates.
