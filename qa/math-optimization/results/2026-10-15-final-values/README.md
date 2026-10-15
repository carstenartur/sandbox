# Final local values and bounded search allocation

The Java adapter previously exposed every assignment version of an escaping local
as an independent result. A six-assignment SWU chain therefore asked the SDK to
preserve six values although only three are observed outside that region. It also
gave every region the same search allowance, irrespective of its operation count.

The adapter now retains the final escaping version and preserves the entire
original execution trace for independent exception and ordering verification.
Comments still pin their binding. Declarations remain in scope, including explicit
types replacing `var` where its initializer moves into a guarded branch.
Remaining file work is divided by original trace size (weights 1–64). Alternate
domains/slices retain half their allocation; combined BigInteger requests retain
a quarter for smaller statement retries. Unsupported attempts still cost their
full allowance. The SDK, mathematical rules, total budget and proof gates are unchanged.

## Frozen Bouncy Castle comparison

Both runs use the complete BC 1.85.2 source archive, Java 17 source semantics,
Java 25.0.4.1+1, JDT 3.47, 1,000,000 work per file and 20,000 states. All 3,136
source files are available as shared context; 3,126 parse successfully and the
same ten multi-release/module files have unresolved types. Exact inputs, hashes,
profile hashes and run times are in `comparison.json`. Baseline: PR #1704 head
`f8a7ed63a8faee2093c7723ebe69e0611c96610f`.

| Profile | Before | After | Net change |
| --- | ---: | ---: | ---: |
| Preserve Java, mathematical opt-in off | 101 | 109 | +8 |
| Preserve Java, mathematical opt-in on | 112 | 118 | +6 |

These are accepted regions after proof, source validation and exact Apply/Undo,
not a whole-library behavior or throughput measurement. The strict after-run
preceded the final BigInteger-only retry reserve; that branch is disabled in this
profile and the executed code is otherwise identical.

The opt-in differences are SWU 4→3, X25519Field 2→3, HawkEngine 15→20,
HawkSigner 7→8, Polynomial 1→2 and Rainbow GF2Field 10→9. The missing Rainbow
candidate is rejected by emitted-source verification at offset 7958 with
`VERIFICATION_BUDGET_EXCEEDED`; the larger search earlier in that file spends more
of the same budget. No proof or budget gate was relaxed to recover it.

## Stronger SWU result

The first region now combines two previously separate modular-square proposals:
six modular reductions become three, and two exponentiations by two become
multiplications. The independent proof is `AFFINE_MODULAR_NORMAL_FORM`. Estimated
operation work falls 104→36; the adapter's estimated score saving is 398 after
charging the retained receiver guards. These are cost-model values, not timings.
The complete class has three accepted regions instead of four; counting patches
alone would conceal this improvement.

`BouncyCastleVersionProjectionTest` analyzes the unchanged full source at the
frozen file budget, counts executable operations, compiles both complete classes,
and compares Hash-to-Curve results for P-256, P-384, P-521 and BLS12-381 on empty,
ASCII, long and Unicode messages. It also checks invalid input and exact Undo.
The existing direct map test covers 100 inputs, including oversized values.
Additional JUnit cases cover overwritten throwing operations, checked overflow,
comments, `var`, nullable/subclassed/oversized BigInteger receivers and fallback.

## Remaining obstacles

- Unknown receiver provenance requires exact-class, null and magnitude guards.
  A small algebraic saving can cost less than its guards: ECJPAKE/Owl-style
  determinant simplifications are already found by the SDK but rejected on net cost.
- Long GenericSqrtRatio chains still reach an inconclusive proof/search boundary;
  projecting eleven versions to five outputs alone does not qualify a rewrite.
- Side effects, external writes, array dependencies and unsupported calls bound
  extraction. Their raw diagnostic counts include non-mathematical code and must
  not be presented as a count of missed useful refactorings.
- Multiple-declarator diagnostics are numerous, but the inspected BigInteger
  cases are largely aliases, factories and null initialization. That feature was
  not added without a demonstrated useful arithmetic chain.

The next valuable extension is more precise source-derived receiver/range facts
and independently checked proof support, with real source regressions. Removing
guards by assumption or raising the global budget would not address those gaps.

## Validation and reproduction

The native Maven/Tycho math module completed with 473 tests, zero failures,
errors or skips; SpotBugs passed. Run the repository's existing
`qa/math-optimization/run-project.sh` with freshly compiled adapter classes, the
pinned binary/source archives and `profiles/bc-mathematical.properties` or
`profiles/bc-strict.properties`. Keep the output directory fresh. The
ordinary generated SWU source is also the registered-cleanup Help fixture; its
native preview, Apply, compile and Undo checks qualify the screenshot separately.
The focused native Help run passed both JUnit methods across all five fixtures;
the refreshed PNGs and provenance record that installed adapter fingerprint.
