# Mathematics follow-up implementation plan

> Use superpowers:executing-plans. The user requested the remaining proposals and review fixes together.

**Goal:** Extend useful, independently verified source coverage while improving patch selection and correcting full-call measurements.

**Architecture:** Reuse the production extractor, optimizer, verifier and source rewriter. Split unsupported modular chains into bounded connected windows before singleton fallback. Admit primitive array stores as ordered output effects terminating a region. Use the actual patch shape in Readability selection and review ordering. Keep the existing pinned Maven/JUnit/JMH qualification.

**Scope:** These are bounded implementations of the earlier recommendations, not a new symbolic loop prover. Original overflow, exception order, array aliasing and carry values must be preserved. Runtime estimates remain estimates; the corrected full-call benchmark is the calibration evidence, not permission to invent cost weights from noisy samples.

## Constraints and review focus

- Keep the Java 25 baseline, SDK and work/state/time limits unchanged; all attempts share the existing file budget.
- Preserve store ordering even when source and destination arrays alias. Reject effectful indices, compound stores and non-total arithmetic; terminate the region after each store.
- Preserve every actual memory read. Equal source spellings alone do not prove equal values or permit read elimination.
- Keep intermediate values visible across modular windows and reverify each generated window independently.
- Preserve comments, exact Undo and metadata. Formatting preferences must not weaken proof or discard required guards.
- Keep fixes on PR #1767, directly against main; the user's additional implementation request extends its scope. Stay below 1,500 changed lines.

## Tasks

- [x] Reproduce the multi-release loader mismatch with an actual synthetic MR JAR; require equality with normal classpath loading and compiled-override precedence. Use JarFile.runtimeVersion(), take the explicit UTF-8 review suggestion and run affected tests.
- [x] Add a real GenericSqrtRatio regression for connected modular window retries before singleton retries, within the existing work budget.
- [x] Add array-store output projections; compile/execute original and generated versions for aliased/distinct arrays, null/bounds failures, carry overflow, and unsupported accesses. Require unchanged write order and exact Undo.
- [x] Use patch shape for Readability selection and rank comparable candidates by branches/locals/changed lines before estimated savings; add selection regressions and full-call calibration reporting.
- [x] Run the complete selected JUnit suite, both full BC scans and corrected full-call JMH. Record actual count changes and replace obsolete timing claims.
- [x] Review the complete change, fix substantive findings, update PR and address the inline review with test/measurement evidence.

## Ledger

- Base: PR #1767 at 030066d4da66dd8f6189f0f677d9532d005e5e41; existing branch and clean checkout retained.
- Loader regression: expected runtime override, observed base class; corrected loader passes with future entries ignored and compiled classes preferred. Affected tests: 11 passed.
- Choice: preserve reads and ordered stores rather than introduce unproved heap equalities. This enables arithmetic feeding writes safely; whole-loop/multiword algebra remains bounded by the existing proof engine.
- Existing emitter already inlines single-use values; avoid duplicating it. Add selection based on patch shape instead, without changing numerical proof.
- Array tests: two previously failing assertions now pass; actual generated classes preserve aliased writes, exceptional outcomes and exact Undo over 150 deterministic cases.
- Modular regression: real BC GenericSqrtRatio now exercises connected-window retries; old implementation fails the new assertion. No claim that every window improves.
- Readability regression: old behavior introduced guarded control flow; new selection preserves the expression while runtime mode can still offer the verified guarded candidate.
- Independent complete-diff review found no Critical/Important defect. Its minor test-coverage finding is fixed: the array regression now requires an actually transformed read/store region while its carry remains live, and executes both variants.
- Full-scan follow-up exposed budget dilution (111/121 accepted regions fell to 106/115). A new real-BC regression reproduced the lost HAETAE/Hawk/NTRU candidates. Established regions now run first; stores use only the remaining file budget, cannot overlap accepted edits, and scalar retries retain stores as boundaries. The targeted regression passes with all 21 established candidates preserved.
- Final corpus comparison: 3,126 parsed files and the same 10 parse-error files in each profile; strict 111 -> 114 and mathematical 121 -> 124. Source/binary/profile hashes, Java target, options and selected entries match the main baseline. New stores are accepted in ML-DSA Poly, HawkSigner and HQCEngine; no prior candidate count is lost.
- GenericSqrtRatio still has one accepted region, but it now covers a connected four-statement window: the verified rewrite shares the modPow intermediate and removes an inner modular reduction. Its source hash changes; this is not an additional hit or a measured runtime claim. The full-call benchmark continues to isolate the exact generated SWU class.
- Final combined Java 25/Maven qualification: BUILD SUCCESS in 7:00; 210 tests, zero failures/errors and one optional historical Git-reference skip; both complete scans and all 16 runtime-aware JMH configurations completed. All eight timing comparisons are INCONCLUSIVE, with allocation changes from -5.3% to +0.6%. Obsolete measurements are replaced in the original qualification ledger.
- Review fixes include runtime-aware MR-JAR loading with regression coverage and explicit UTF-8 in the reviewed source test. The same PR branch receives this follow-up; native Eclipse/distribution CI remains separate.
