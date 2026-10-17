# Field-array source completion — PR #1713

## Contract
Continue the approved mathematics cleanup work. Keep generic mathematics in
Regelsuche and reuse the existing Java ordered-source-capture, proof, emission,
cost, cancellation, Apply and Undo paths. No source-specific formula recognition,
new workflow, disabled test or larger default search allowance.

## Steps
1. Integrate current main with the existing five JUnit regressions, preserving
   the merged #1711/#1712 production changes. Run the ordinary Maven authority
   and record the actual missing-field-input failures.
2. Extend JavaSourceReadShape for one-dimensional primitive array fields through
   unqualified, this-qualified and type-qualified references. Every occurrence
   remains a distinct ordered read. Mutable/volatile indices, volatile array
   fields, effectful receivers, writes and unsupported profiles retain boundaries.
3. Add regression coverage for repeated/dead reads, observable writes, aliases,
   exceptions and class-initialization effects. Retain original index spelling.
4. Qualify actual Bouncy Castle Keccak source with pinned source/binary provenance,
   extraction counts and bounded optimizer outcomes. Distinguish source admission
   from accepted changes and estimated cost from measured runtime performance.
5. Inspect native tests, quality, distribution and reviews for the exact branch
   head before merging. Do not claim a wider corpus improvement from a fixture.

## Ledger
- Initial branch: 4c9e7807226a0ddecec8c0a9faca4d715a75bab1.
- Integration main: 65fb1e0b79622234784e30db268de9c84e914f01.
- Main already contains the #1712 final numeric field / indexed buffer support;
  array receivers are still restricted to locals.
- The only unintegrated #1713 delta is PreservedArraySourceTest.java; its bytes
  are retained. Both parents are retained, without force-updating branch history.
- Execution environment: both container and Python currently fail to start with
  ClientError. Use the existing hosted Maven/JUnit authority; do not substitute
  unexecuted tests or claim local verification.
- Pending: executed baseline, implementation, qualification, final review.
