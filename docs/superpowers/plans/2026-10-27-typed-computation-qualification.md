# Typed computation and word-relation qualification

Qualification recorded on 2026-10-27 for the source revisions below. The
integration notes identify the subsequent prerequisite merges; qualification
alone does not announce a public release.

The source adapter retains Java types, bindings and effect order. Regelsuche owns
mathematical candidates, search and independent proof. The existing typed plan
and request contracts provide the computation boundary for future configurable
CAS providers; this change does not add a second mathematics engine to Sandbox.

## Qualified source and binary

- Regelsuche source: `b4081ca59ccf2d8b6985bb628856f68d0c118945` ([PR 1114](https://github.com/carstenartur/Regelsuche/pull/1114)).
- Sandbox consumer: `c4b0f63707a1fca398951a02dd8890819be33eb5` on `codex/typed-computation-tasks`.
- SDK JAR SHA-256: `87fe1a838f21a86c84ee8519e81b7040e47c7775e32ef01d7c8ac04e0a040adf`.
- Existing SDK qualification/staging [run 40448545085](https://github.com/carstenartur/sandbox/actions/runs/40448545085): both jobs passed.
- Staging commit: `92754e85ec870740f72ea6b07e2b7632fc2c4af4`. The JAR and receipt are copied unchanged from its Git blobs.
- Fresh external consumer checks cover optimization, selected-path replay, original checked overflow, and original/candidate domains.

## Validation

All 210 SDK tests pass. The dependency modules also passed their full suites;
independent architecture and code reviews found no remaining product blocker.
Checker v11 rejects v10 evidence. Tests cover typed word projection, transport
sharing, cancellation, bounded proofs, complete versus partial work accounting,
and preservation of earlier trace work after preparation failure.

The product-wide Regelsuche [CI run 40448548628](https://github.com/carstenartur/Regelsuche/actions/runs/40448548628)
also passed on the exact SDK source revision: Maven/Docker, Gradle, JMH and
`ciCheck` completed successfully. This is separate from consumer/native Eclipse CI.

The final Sandbox command was:

```sh
mvn -f qa/math-optimization/pom.xml -Pbc-corpus verify
```

It completed successfully with **560 tests, zero failures/errors and one optional
historical-reference skip**, followed by both complete corpus scans. The retained
region regression now runs under both profiles. Incoming-assignment retries must
produce a candidate at the actual assignment before compilation, 343 boundary
comparisons and exact Undo are checked.

For a fresh reproduction, add `clean` before `verify`: the scanner deliberately
refuses to overwrite an existing `target/bc-*` output directory.

The integration rerun with the QA Jackson 2.21.7 update also passed: 560 tests,
zero failures/errors, one optional skip, and both complete scans. All 117 strict
and 126 mathematical accepted spans, archive/profile settings and parse errors
match the qualified feature exactly. Product/test sources and SDK bytes are unchanged.

## Matched Bouncy Castle 1.85.2 scans

The baseline is the previously qualified Sandbox `eb774d4` checkout, not an older
historical report (SDK source `9d046a0a113c92a8f77a3e751d1c1007ab012db4`,
JAR SHA-256 `40707f43a79b3c9aea6edbed4f5c2fc1be506665fd7cfa864f5a507f6ae7129e`).
Binary/source/profile hashes, Java target, option values,
selected entries and the complete parse-error set match. Both profiles parse
3,126 files and retain the same ten parse-error files.

| Profile | Baseline accepted regions | Current | Lost original spans |
| --- | ---: | ---: | ---: |
| Strict Java | 114 | 117 | 0 |
| Mathematical opt-in | 124 | 126 | 0 |

New source spans are HawkEngine `83332/112` (strict), HawkEngine `112211/137`
(both), and GF2Field `7958/149` (both). Every baseline accepted offset/length pair
is retained, including both NTRU Polynomial regions. File work remains 1,000,000,
with 20,000 states, five-second requests and Java 17 source semantics.

The local JVM changed after environment recovery from OpenJDK `25+36-3489` to
Temurin `25+36-LTS`; this is a functional/search comparison, not a new timing
measurement. No runtime speedup or constant-time qualification is claimed.

## Complete computation tasks and integration boundary

Pinned Nat256.add yields 25 ordinary edit regions but one inspection task with
26 outputs, 16 fresh reads and eight stores. The actual JDT plan and independently
constructed SDK plan pass comparison/replay checks and both reach search with
`INCOMPLETE_SEARCH_RELATION` (289,474 work units). This is not a proved full-addition
optimization. Tasks remain inspection data; automatic edits across stores require
a separately verified realization of their effect schedule.

The older explanation API still reports presentation work separately from fresh
verification. A complete explanation-call ledger remains an explicit follow-up.
Native Eclipse/UI CI is separate from this headless qualification. Regelsuche
[PR 1114](https://github.com/carstenartur/Regelsuche/pull/1114) is merged as
`ca67c0133898e10c21fec9ce5493bdc2b2a4a546`; Sandbox
[prerequisite PR 1767](https://github.com/carstenartur/sandbox/pull/1767) is merged as
`2c785aabf1a6ac1fa25554f2292cf851a8f46a48`. The consumer integration is based
directly on that updated `main` in [PR 1769](https://github.com/carstenartur/sandbox/pull/1769),
with unchanged product/test sources and exact SDK bytes from the qualified feature
tree. The QA build additionally incorporates the Jackson 2.21.7 update proposed in
[PR 1768](https://github.com/carstenartur/sandbox/pull/1768); it does not change the
packaged SDK. PR 1769's native Eclipse, Oomph, screenshots and distribution gates
remain mandatory; the prerequisite's CI does not substitute for them. No stacked
PR is used. Configurable CAS/SymPy selection remains a future
extension, with requirements documented in the
[source/CAS architecture](../../../sandbox_math_cleanup/ARCHITECTURE.md#complete-computation-tasks-and-cas-ownership).
