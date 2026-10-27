# Typed computation and word-relation qualification

The source adapter retains Java types, bindings and effect order. Regelsuche owns
mathematical candidates, search and independent proof. The existing typed plan
and request contracts provide the computation boundary for future configurable
CAS providers; this change does not add a second mathematics engine to Sandbox.

## Qualified source and binary

- Regelsuche source: `b4081ca59ccf2d8b6985bb628856f68d0c118945` ([PR 1114](https://github.com/carstenartur/Regelsuche/pull/1114)).
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

The final Sandbox command was:

```sh
mvn -f qa/math-optimization/pom.xml -Pbc-corpus verify
```

It completed successfully with **560 tests, zero failures/errors and one optional
historical-reference skip**, followed by both complete corpus scans. The retained
region regression now runs under both profiles. Incoming-assignment retries must
produce a candidate at the actual assignment before compilation, 343 boundary
comparisons and exact Undo are checked.

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
Native Eclipse/UI CI is separate from this headless qualification. The Sandbox
feature branch awaits integration after prerequisite PR 1767; no stacked PR is
created.
